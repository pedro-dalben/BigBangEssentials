package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.*;
import com.pedrodalben.bigbangessentials.jobs.action.JobActionListener;
import com.pedrodalben.bigbangessentials.jobs.action.JobActionProcessedEvent;
import com.pedrodalben.bigbangessentials.jobs.database.JobActionReceiptRepository;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates the execution pipeline:
 * Idempotency (UUID + fingerprint) -> Validation -> Eligibility -> Rule Evaluation -> Calculation -> Application -> Receipt.
 */
public class JobActionProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobActionProcessor.class);
    private static final JobActionProcessor INSTANCE = new JobActionProcessor();

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong duplicateRejectedCount = new AtomicLong(0);
    private final List<JobActionListener> listeners = new CopyOnWriteArrayList<>();

    public static JobActionProcessor getInstance() {
        return INSTANCE;
    }

    private JobActionProcessor() {}

    public void registerListener(JobActionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregisterListener(JobActionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(ServerPlayer player, JobActionProcessedEvent event) {
        for (JobActionListener listener : listeners) {
            try {
                listener.onActionProcessed(player, event);
            } catch (Exception e) {
                LOGGER.error("Error in JobActionListener while processing event for action {}", event.action().actionId(), e);
            }
        }
    }

    public void process(ServerPlayer player, JobAction action) {
        if (player == null || action == null) return;

        // Login data is loaded asynchronously. Queue the action behind that
        // barrier instead of evaluating an empty PlayerJobsData and silently
        // dropping the first actions made after joining the server.
        java.util.concurrent.CompletableFuture<PlayerJobsData> ready =
                JobsManager.getInstance().getPlayerDataReady(player.getUUID());
        if (!ready.isDone()) {
            ready.whenComplete((ignored, error) -> {
                if (error == null) process(player, action);
                else LOGGER.error("Jobs data was not ready for player {}; action discarded", player.getUUID(), error);
            });
            return;
        }

        processedCount.incrementAndGet();

        // 1. Idempotency Check (UUID-based)
        JobActionReceiptRepository receiptRepo = JobActionReceiptRepository.getInstance();

        if (receiptRepo.isAlreadyProcessedOrProcessing(action.actionId())) {
            duplicateRejectedCount.incrementAndGet();
            if (JobsManager.isGlobalDebugMode()) {
                LOGGER.debug("Action {} rejected as duplicate or currently processing.", action.actionId());
            }
            return;
        }

        // 1b. Fingerprint-based deduplication
        String fingerprint = JobFingerprintService.getInstance().computeActionFingerprint(action);
        if (JobFingerprintService.getInstance().isEphemeralDuplicate(fingerprint)) {
            duplicateRejectedCount.incrementAndGet();
            if (JobsManager.isGlobalDebugMode()) {
                LOGGER.debug("Action {} rejected by fingerprint dedup.", action.actionId());
            }
            return;
        }

        if (!receiptRepo.reserveAction(action.actionId(), action.playerId())) {
            duplicateRejectedCount.incrementAndGet();
            return;
        }

        // 2. Validation
        JobActionValidator.ValidationResult valResult = JobActionValidator.getInstance().validate(player, action);
        if (!valResult.isValid()) {
            cancelDiscoveryIfPending(action);
            receiptRepo.recordReceipt(action.actionId(), player.getUUID(), "", action.type().name(),
                    action.targetId(), JobRewardOutcome.failure(valResult.reason()),
                    action.context().getMetadataJson());
            notifyListeners(player, new JobActionProcessedEvent(action, false, Optional.of(valResult.reason()), Instant.now()));
            return;
        }

        notifyListeners(player, new JobActionProcessedEvent(action, true, Optional.empty(), Instant.now()));

        // 3. Eligibility Resolution
        List<JobEligibilityResolver.EligibleJob> eligibleJobs =
                JobEligibilityResolver.getInstance().resolveEligibleJobs(player, action);
        if (eligibleJobs.isEmpty()) {
            cancelDiscoveryIfPending(action);
            receiptRepo.recordReceipt(action.actionId(), player.getUUID(), "", action.type().name(),
                    action.targetId(), JobRewardOutcome.failure("NO_ELIGIBLE_JOBS"),
                    action.context().getMetadataJson());
            return;
        }

        // 4. Rule Evaluation & Reward Application loop
        double totalXp = 0.0;
        double totalCoins = 0.0;
        String lastJobId = "";
        boolean anySuccess = false;

        for (JobEligibilityResolver.EligibleJob job : eligibleJobs) {
            JobRuleEvaluator.MatchResult match = JobRuleEvaluator.getInstance().evaluate(job.jobDef(), action);
            if (match.isMatch() && match.rule() != null) {
                JobRuleEvaluator.EvaluatedRule rule = match.rule();
                JobRewardOutcome outcome = JobRewardCalculator.getInstance().calculate(
                        player, job.data(), job.jobDef(), job.progress(), action, rule.reward(), rule.matchedActionKey());

                if (outcome.success()) {
                    boolean applied = JobRewardApplier.getInstance().applyResult(player, job.data(), job.jobDef(), action, outcome,
                            JobsManager.getInstance().getRepository());
                    if (applied) {
                        totalXp += outcome.experience();
                        totalCoins += outcome.coins();
                        lastJobId = job.jobDef().id;
                        anySuccess = true;
                    }
                }
            }
        }

        // 5. Record final receipt
        if (anySuccess) {
            successCount.incrementAndGet();
            receiptRepo.recordReceipt(action.actionId(), player.getUUID(), lastJobId, action.type().name(),
                    action.targetId(), JobRewardOutcome.success(totalXp, totalCoins),
                    action.context().getMetadataJson());
        } else {
            cancelDiscoveryIfPending(action);
            receiptRepo.recordReceipt(action.actionId(), player.getUUID(), "", action.type().name(),
                    action.targetId(), JobRewardOutcome.failure("NO_MATCHING_RULES_OR_ZERO_PAYOUT"),
                    action.context().getMetadataJson());
        }
    }

    public long getProcessedCount() { return processedCount.get(); }
    public long getSuccessCount() { return successCount.get(); }
    public long getDuplicateRejectedCount() { return duplicateRejectedCount.get(); }

    private void cancelDiscoveryIfPending(JobAction action) {
        if (action.type() == JobActionType.EXPLORE && action.context() != null && action.context().isFirstDiscovery()) {
            String type = determineDiscoveryType(action);
            if (!type.isEmpty() && !action.targetId().isEmpty()) {
                com.pedrodalben.bigbangessentials.jobs.antiexploit.ExplorationDiscoveryService.getInstance()
                        .cancelDiscovery(action.playerId(), type, action.targetId());
            }
        }
    }

    private String determineDiscoveryType(JobAction action) {
        if (action.context() == null) return "";
        String src = action.context().getEventSource();
        if (src == null) return "";
        if (src.equals("EXPLORATION_BIOME")) return "BIOME";
        if (src.equals("EXPLORATION_STRUCTURE")) return "STRUCTURE";
        if (src.equals("EXPLORATION_CELL")) return "CELL";
        if (src.equals("EXPLORATION_DIMENSION")) return "DIMENSION";
        if (action.context().getBiome() != null && !action.context().getBiome().isEmpty()) return "BIOME";
        return "";
    }
}
