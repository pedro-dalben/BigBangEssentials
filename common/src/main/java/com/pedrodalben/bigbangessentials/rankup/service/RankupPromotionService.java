package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemCreditRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemDebitRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemOperationResult;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class RankupPromotionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupPromotionService.class);

    private final RankupManager manager;
    private final Map<UUID, PromotionExecution> promotionQueue = new ConcurrentHashMap<>();
    private final Set<String> activeCompensations = ConcurrentHashMap.newKeySet();

    public RankupPromotionService(RankupManager manager) {
        this.manager = Objects.requireNonNull(manager, "RankupManager cannot be null");
    }

    public record PromotionInspection(
            boolean active,
            UUID playerUuid,
            String transactionId,
            String stage,
            long elapsedMs,
            boolean futureDone,
            String errorMessage
    ) {}

    private enum PromotionStage {
        PROMOTE_ENTERED,
        PREFLIGHT_SYNC_STARTED,
        PREFLIGHT_SYNC_COMPLETED,
        PREFLIGHT_SYNC_FAILED,
        QUEUE_RESERVED,
        PREFLIGHT_STARTED,
        PREFLIGHT_COMPLETED,
        IDEMPOTENCY_LOOKUP_STARTED,
        IDEMPOTENCY_LOOKUP_COMPLETED,
        TRANSACTION_PREPARED,
        MONEY_DEBIT_STARTED,
        MONEY_DEBIT_COMPLETED,
        GEMS_DEBIT_STARTED,
        GEMS_DEBIT_COMPLETED,
        LUCKPERMS_STARTED,
        LUCKPERMS_COMPLETED,
        HISTORY_STARTED,
        HISTORY_COMPLETED,
        PROGRESS_CLEAR_STARTED,
        PROGRESS_CLEAR_COMPLETED,
        ACTIONS_STARTED,
        ACTIONS_COMPLETED,
        TRANSACTION_COMPLETED,
        QUEUE_RELEASED
    }

    public static final class PromotionExecution {
        private final UUID playerUuid;
        private final String transactionId;
        private final String targetRankId;
        private final long startedAtMs = System.currentTimeMillis();
        private final CompletableFuture<RankupPromotionResult> promise = new CompletableFuture<>();
        private volatile PromotionStage stage = PromotionStage.QUEUE_RESERVED;
        private volatile String lastError;

        private PromotionExecution(UUID playerUuid, String transactionId, String targetRankId) {
            this.playerUuid = playerUuid;
            this.transactionId = transactionId;
            this.targetRankId = targetRankId;
        }
    }

    public void recoverTransactions() {
        manager.getRepository().findPendingTransactions()
                .exceptionally(err -> {
                    LOGGER.error("[RankUp] Failed to load pending transactions from database during recovery", err);
                    return List.of();
                })
                .thenAccept(transactions -> {
                    for (RankupTransaction tx : transactions) {
                        try {
                            UUID uuid = tx.playerUuid();
                            LOGGER.info("Recovering pending RankUp transaction {} for player {}", tx.transactionId(), uuid);

                            if (tx.status() == RankupTransactionStatus.LUCKPERMS_UPDATED) {
                                manager.getTaskProgressService().resetLadderProgress(uuid, tx.ladderId())
                                        .thenCompose(v -> manager.getRepository().saveTransaction(tx.withStatus(RankupTransactionStatus.COMPLETED)))
                                        .join();
                                LOGGER.info("Recovered transaction {} by completing it (LuckPerms was already updated).", tx.transactionId());
                            } else {
                                compensate(uuid, tx).join();
                                LOGGER.info("Recovered transaction {} by compensating.", tx.transactionId());
                            }
                        } catch (Exception e) {
                            LOGGER.error("[RankUp] Error recovering transaction {}", tx.transactionId(), e);
                        }
                    }
                });
    }

    public CompletableFuture<RankupPromotionResult> promote(ServerPlayer player, RankupRank targetRank) {
        return promote(player, targetRank, true);
    }

    public boolean isPromotionInProgress(UUID uuid) {
        return promotionQueue.containsKey(uuid);
    }

    public boolean isPromotionInProgress(UUID uuid, PromotionEvaluationContext context) {
        PromotionExecution execution = promotionQueue.get(uuid);
        if (execution == null) return false;
        return context == null || !context.ignoresQueueLock(execution.transactionId);
    }

    public PromotionInspection inspectPromotion(UUID uuid) {
        PromotionExecution execution = promotionQueue.get(uuid);
        if (execution == null) {
            return new PromotionInspection(false, uuid, null, null, 0L, true, null);
        }
        return new PromotionInspection(true, uuid, execution.transactionId, execution.stage != null ? execution.stage.name() : null,
                System.currentTimeMillis() - execution.startedAtMs, execution.promise.isDone(), execution.lastError);
    }

    public boolean unlockPromotion(UUID uuid) {
        PromotionExecution execution = promotionQueue.get(uuid);
        if (execution == null) return true;
        if (!execution.promise.isDone()) {
            return false;
        }
        releaseExecution(uuid, execution);
        return true;
    }

    public boolean cancelPromotion(UUID uuid) {
        PromotionExecution execution = promotionQueue.get(uuid);
        if (execution == null) return true;
        execution.lastError = "Cancelled by administrator";
        if (!execution.promise.isDone()) {
            execution.promise.complete(RankupPromotionResult.failure("Promotion cancelled by administrator", RankupTransactionStatus.RECOVERY_REQUIRED, execution.transactionId, RankupPromotionResultCode.INTERNAL_ERROR));
        }
        releaseExecution(uuid, execution);
        return true;
    }

    public CompletableFuture<RankupPromotionResult> promote(ServerPlayer player, RankupRank targetRank, boolean executeActions) {
        if (player == null || player.getUUID() == null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Invalid player", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.INTERNAL_ERROR));
        }
        if (com.pedrodalben.bigbangessentials.BigBangEssentials.isServerStopping()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Server is shutting down", RankupTransactionStatus.RECOVERY_REQUIRED, null, RankupPromotionResultCode.INTERNAL_ERROR));
        }

        UUID uuid = player.getUUID();
        LOGGER.info("[RANKUP-PROMOTION] player_uuid={} target_rank={} stage=PROMOTE_ENTERED thread={}",
                uuid, targetRank != null ? targetRank.id() : "null", Thread.currentThread().getName());

        LOGGER.info("[RANKUP-PROMOTION] player_uuid={} stage=PREFLIGHT_SYNC_STARTED", uuid);
        RankupEligibilitySnapshot preflight;
        try {
            preflight = preflightSnapshot(uuid);
        } catch (Throwable preflightError) {
            LOGGER.error("[RANKUP-PROMOTION] player_uuid={} stage=PREFLIGHT_SYNC_FAILED", uuid, preflightError);
            return CompletableFuture.completedFuture(
                    RankupPromotionResult.failure("Erro interno ao iniciar a promoção: " + preflightError.getMessage(),
                            RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.INTERNAL_ERROR));
        }
        LOGGER.info("[RANKUP-PROMOTION] player_uuid={} stage=PREFLIGHT_SYNC_COMPLETED state={}", uuid, preflight.state().name());
        if (preflight.nextRank() == null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Already at maximum rank", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.ALREADY_MAX_RANK));
        }
        if (targetRank == null || !targetRank.enabled()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Target rank is invalid or disabled", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.CONFIGURATION_INVALID));
        }
        if (!preflight.nextRank().id().equalsIgnoreCase(targetRank.id())) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Target rank is not the next rank in the ladder (" + preflight.nextRank().displayName() + ")", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.NOT_NEXT_RANK));
        }
        if (!preflight.isReadyForPromotion()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure(preflight.state().defaultStatusText(), RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.TASKS_INCOMPLETE));
        }

        String transactionId = UUID.randomUUID().toString();
        PromotionExecution execution = new PromotionExecution(uuid, transactionId, targetRank.id());
        PromotionExecution existing = promotionQueue.putIfAbsent(uuid, execution);
        if (existing != null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("A promotion is already in progress.", RankupTransactionStatus.FAILED, existing.transactionId, RankupPromotionResultCode.TRANSACTION_IN_PROGRESS));
        }

        logStage(execution, PromotionStage.QUEUE_RESERVED, "ok", null);

        try {
            CompletableFuture<RankupPromotionResult> pipeline =
                    doPromote(player, targetRank, preflight, executeActions, execution)
                            .orTimeout(promotionTimeoutSeconds(), TimeUnit.SECONDS);

            pipeline.whenComplete((result, error) -> {
                try {
                    if (error != null) {
                        execution.lastError = error.getMessage();
                        result = handlePromotionFailure(execution, error);
                    }
                    if (result == null) {
                        LOGGER.error("[RANKUP-PROMOTION] player_uuid={} transaction_id={} stage=NULL_RESULT_DETECTED - pipeline completed without result or error", uuid, execution.transactionId);
                        result = RankupPromotionResult.failure("Internal error: promotion pipeline did not produce a result", RankupTransactionStatus.RECOVERY_REQUIRED, execution.transactionId, RankupPromotionResultCode.INTERNAL_ERROR);
                    }
                    if (!execution.promise.isDone()) {
                        execution.promise.complete(result);
                    }
                } catch (Throwable completionError) {
                    LOGGER.error("[RANKUP-PROMOTION] player_uuid={} transaction_id={} stage=COMPLETION_ERROR", uuid, execution.transactionId, completionError);
                    if (!execution.promise.isDone()) {
                        execution.promise.completeExceptionally(completionError);
                    }
                } finally {
                    releaseExecution(uuid, execution);
                }
            });
        } catch (Throwable synchronousError) {
            try {
                RankupPromotionResult result = handlePromotionFailure(execution, synchronousError);
                if (!execution.promise.isDone()) {
                    execution.promise.complete(result);
                }
            } finally {
                releaseExecution(uuid, execution);
            }
        }

        return execution.promise;
    }

    protected CompletableFuture<RankupPromotionResult> doPromote(ServerPlayer player, RankupRank targetRank,
                                                                 RankupEligibilitySnapshot preflight,
                                                                 boolean executeActions,
                                                                 PromotionExecution execution) {
        UUID uuid = player.getUUID();
        RankupConfig config = manager.getConfig();
        if (config == null || !config.isEnabled()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("RankUp module is disabled or unconfigured", RankupTransactionStatus.FAILED, execution.transactionId, RankupPromotionResultCode.CONFIGURATION_INVALID));
        }

        PromotionEvaluationContext internalContext = PromotionEvaluationContext.internal(execution.transactionId);
        logStage(execution, PromotionStage.PREFLIGHT_STARTED, "start", null);
        RankupEligibilitySnapshot snapshot = manager.getEligibilitySnapshot(uuid, internalContext);
        logStage(execution, PromotionStage.PREFLIGHT_COMPLETED, snapshot.state().name(), null);

        if (snapshot.nextRank() == null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Already at maximum rank", RankupTransactionStatus.FAILED, execution.transactionId, RankupPromotionResultCode.ALREADY_MAX_RANK));
        }
        if (!snapshot.nextRank().id().equalsIgnoreCase(targetRank.id())) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Target rank is not the next rank in the ladder (" + snapshot.nextRank().displayName() + ")", RankupTransactionStatus.FAILED, execution.transactionId, RankupPromotionResultCode.NOT_NEXT_RANK));
        }
        if (!snapshot.isReadyForPromotion()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure(snapshot.state().defaultStatusText(), RankupTransactionStatus.FAILED, execution.transactionId, RankupPromotionResultCode.TASKS_INCOMPLETE));
        }

        RankupRank currentRank = snapshot.currentRank();
        BigDecimal moneyRequired = snapshot.moneyRequired();
        int gemsRequired = snapshot.gemsRequired();
        String idempotencyKey = uuid + ":" + config.getLadder().id() + ":" + targetRank.id();

        AtomicReference<RankupTransaction> transactionRef = new AtomicReference<>();

        CompletableFuture<RankupTransaction> transactionPipeline =
                lookupTransaction(execution, idempotencyKey)
                        .thenCompose(opt -> {
                            if (opt.isPresent()) {
                                RankupTransaction transaction = opt.get();
                                if (transaction.status() == RankupTransactionStatus.COMPLETED) {
                                    // Transaction already completed — skip the entire pipeline
                                    LOGGER.info("[RANKUP-PROMOTION] player_uuid={} transaction_id={} stage=IDEMPOTENCY_ALREADY_COMPLETED", uuid, transaction.transactionId());
                                    RankupPromotionResult alreadyDone = RankupPromotionResult.success("Promoted to " + targetRank.displayName() + " (transaction already completed)", transaction.transactionId());
                                    throw new PromotionPipelineException(alreadyDone);
                                }
                                if (transaction.status() == RankupTransactionStatus.RECOVERY_REQUIRED) {
                                    LOGGER.warn("[RANKUP-PROMOTION] player_uuid={} transaction_id={} stage=IDEMPOTENCY_RECOVERY_REQUIRED", uuid, transaction.transactionId());
                                    RankupPromotionResult recoveryNeeded = RankupPromotionResult.failure(
                                            "A previous promotion requires recovery before retrying. Transaction: " + transaction.transactionId(),
                                            RankupTransactionStatus.RECOVERY_REQUIRED, transaction.transactionId(), RankupPromotionResultCode.INTERNAL_ERROR);
                                    throw new PromotionPipelineException(recoveryNeeded);
                                }
                                if (transaction.status() == RankupTransactionStatus.FAILED || transaction.status() == RankupTransactionStatus.COMPENSATED || transaction.status() == RankupTransactionStatus.PREPARED) {
                                    RankupTransaction resetTx = new RankupTransaction(
                                            transaction.transactionId(), uuid, config.getLadder().id(),
                                            currentRank != null ? currentRank.id() : "", targetRank.id(),
                                            moneyRequired, gemsRequired, RankupTransactionStatus.PREPARED,
                                            idempotencyKey, null, System.currentTimeMillis(), null
                                    );
                                    return saveTransaction(execution, resetTx, PromotionStage.TRANSACTION_PREPARED)
                                            .thenApply(v -> resetTx);
                                }
                                // For any other active status (MONEY_DEBITED, GEMS_DEBITED, LUCKPERMS_UPDATED),
                                // resume the pipeline from where it left off
                                LOGGER.info("[RANKUP-PROMOTION] player_uuid={} transaction_id={} stage=IDEMPOTENCY_RESUMING status={}", uuid, transaction.transactionId(), transaction.status());
                                return CompletableFuture.completedFuture(transaction);
                            } else {
                                RankupTransaction newTx = new RankupTransaction(
                                        UUID.randomUUID().toString(), uuid, config.getLadder().id(),
                                        currentRank != null ? currentRank.id() : "", targetRank.id(),
                                        moneyRequired, gemsRequired, RankupTransactionStatus.PREPARED,
                                        idempotencyKey, null, System.currentTimeMillis(), null
                                );
                                return saveTransaction(execution, newTx, PromotionStage.TRANSACTION_PREPARED)
                                        .thenApply(v -> newTx);
                            }
                        })
                        .thenCompose(tx -> {
                            transactionRef.set(tx);
                            return chargeMoney(execution, tx)
                                    .thenApply(updated -> {
                                        transactionRef.set(updated);
                                        return updated;
                                    })
                                    .thenCompose(updated -> chargeGems(execution, updated, targetRank))
                                    .thenApply(updated -> {
                                        transactionRef.set(updated);
                                        return updated;
                                    })
                                    .thenCompose(updated -> updateLuckPerms(execution, updated, currentRank, targetRank, config))
                                    .thenApply(updated -> {
                                        transactionRef.set(updated);
                                        return updated;
                                    })
                                    .thenCompose(updated -> clearProgress(execution, updated, uuid, config))
                                    .thenApply(updated -> {
                                        transactionRef.set(updated);
                                        return updated;
                                    })
                                    .thenCompose(updated -> writeHistory(execution, updated, uuid, executeActions))
                                    .thenApply(updated -> {
                                        transactionRef.set(updated);
                                        return updated;
                                    })
                                    .thenCompose(updated -> executeActionsOnServer(execution, updated, player, uuid, currentRank, targetRank, executeActions))
                                    .thenApply(updated -> {
                                        transactionRef.set(updated);
                                        return updated;
                                    });
                        });

        return transactionPipeline
                .thenCompose(tx -> completeTransaction(execution, tx, targetRank))
                .handle((result, err) -> {
            if (err != null) {
                return handlePipelineError(execution, uuid, transactionRef.get(), targetRank, err);
            }
            return CompletableFuture.completedFuture(result);
        }).thenCompose(f -> f);
    }

    protected RankupEligibilitySnapshot preflightSnapshot(UUID uuid) {
        return manager.getEligibilitySnapshot(uuid);
    }

    private CompletableFuture<Optional<RankupTransaction>> lookupTransaction(PromotionExecution execution, String idempotencyKey) {
        logStage(execution, PromotionStage.IDEMPOTENCY_LOOKUP_STARTED, "start", null);
        return manager.getRepository().findTransactionByIdempotencyKey(idempotencyKey)
                .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                .whenComplete((opt, err) -> logStage(execution, PromotionStage.IDEMPOTENCY_LOOKUP_COMPLETED, err == null ? "ok" : "error", err));
    }

    private CompletableFuture<RankupTransaction> chargeMoney(PromotionExecution execution, RankupTransaction tx) {
        if (tx.moneyDebited() || tx.moneyAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return CompletableFuture.completedFuture(tx);
        }
        logStage(execution, PromotionStage.MONEY_DEBIT_STARTED, "start", null);
        return CompletableFuture.supplyAsync(() -> EconomyManager.getInstance().debit(tx.playerUuid(), tx.moneyAmount(), "rankup:charge:" + tx.idempotencyKey(), "Rankup charge", java.util.Map.of("source", "rankup", "reference", tx.transactionId().toString())).status() == EconomyOperationStatus.COMPLETED)
                .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                .thenCompose(ok -> {
                    if (!ok) {
                        RankupTransaction failedTx = tx.withStatus(RankupTransactionStatus.FAILED).withErrorMessage("Failed to withdraw money balance");
                        RankupPromotionResult failRes = RankupPromotionResult.failure("Failed to withdraw money balance", RankupTransactionStatus.FAILED, tx.transactionId(), RankupPromotionResultCode.INSUFFICIENT_MONEY);
                        return saveTransaction(execution, failedTx, PromotionStage.MONEY_DEBIT_COMPLETED)
                                .thenCompose(v -> CompletableFuture.failedFuture(new PromotionPipelineException(failRes)));
                    }
                    RankupTransaction updated = tx.withMoneyDebited(true).withStatus(RankupTransactionStatus.MONEY_DEBITED);
                    return saveTransaction(execution, updated, PromotionStage.MONEY_DEBIT_COMPLETED).thenApply(v -> updated);
                });
    }

    private CompletableFuture<RankupTransaction> chargeGems(PromotionExecution execution, RankupTransaction tx, RankupRank targetRank) {
        if (tx.gemsDebited() || tx.gemsAmount() <= 0) {
            return CompletableFuture.completedFuture(tx);
        }
        logStage(execution, PromotionStage.GEMS_DEBIT_STARTED, "start", null);
        GemDebitRequest gemRequest = new GemDebitRequest(
                tx.playerUuid(), tx.gemsAmount(), "rankup", "rank_promotion",
                null, tx.idempotencyKey(), tx.transactionId(), Map.of("to_rank", targetRank.id())
        );
        return CompletableFuture.supplyAsync(() -> GemsManager.getInstance().debit(gemRequest))
                .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                .thenCompose(gemResult -> {
                    if (!gemResult.success()) {
                        boolean moneyRestored = !tx.moneyDebited();
                        if (tx.moneyDebited() && tx.moneyAmount().compareTo(BigDecimal.ZERO) > 0) {
                            try {
                                EconomyOperationReceipt refund = EconomyManager.getInstance().credit(tx.playerUuid(), tx.moneyAmount(), "rankup:refund:" + tx.idempotencyKey(), "Rankup refund", java.util.Map.of("source", "rankup", "reference", tx.transactionId().toString()));
                                moneyRestored = refund != null && refund.status() == EconomyOperationStatus.COMPLETED;
                            } catch (Exception ignored) {}
                        }
                        RankupTransaction failedTx = tx.withMoneyDebited(!moneyRestored)
                                .withCompensated(moneyRestored)
                                .withStatus(moneyRestored ? RankupTransactionStatus.COMPENSATED : RankupTransactionStatus.RECOVERY_REQUIRED)
                                .withErrorMessage("Failed to debit gems");
                        RankupPromotionResult failRes = RankupPromotionResult.failure("Failed to debit gems: " + (gemResult.messageKey() != null ? gemResult.messageKey() : "unknown"), RankupTransactionStatus.FAILED, failedTx.transactionId(), RankupPromotionResultCode.INSUFFICIENT_GEMS);
                        return saveTransaction(execution, failedTx, PromotionStage.GEMS_DEBIT_COMPLETED)
                                .thenCompose(v -> CompletableFuture.failedFuture(new PromotionPipelineException(failRes)));
                    }
                    RankupTransaction updated = tx.withGemsDebited(true).withStatus(RankupTransactionStatus.GEMS_DEBITED);
                    return saveTransaction(execution, updated, PromotionStage.GEMS_DEBIT_COMPLETED).thenApply(v -> updated);
                });
    }

    private CompletableFuture<RankupTransaction> updateLuckPerms(PromotionExecution execution, RankupTransaction tx, RankupRank currentRank, RankupRank targetRank, RankupConfig config) {
        if (tx.luckpermsUpdated()) {
            return CompletableFuture.completedFuture(tx);
        }
        logStage(execution, PromotionStage.LUCKPERMS_STARTED, "start", null);
        return manager.getLuckPermsService().applyRankChange(tx.playerUuid(), currentRank, targetRank, config)
                .orTimeout(luckpermsTimeoutSeconds(), TimeUnit.SECONDS)
                .thenCompose(mutationResult -> {
                    if (!mutationResult.success()) {
                        return compensate(tx.playerUuid(), tx)
                                .thenCompose(compensatedTx -> {
                                    RankupTransactionStatus finalStatus = compensatedTx.compensated() ? RankupTransactionStatus.COMPENSATED : RankupTransactionStatus.RECOVERY_REQUIRED;
                                    RankupTransaction failedTx = compensatedTx.withStatus(finalStatus).withErrorMessage("LuckPerms update failed: " + mutationResult.errorMessage());
                                    RankupPromotionResult failRes = RankupPromotionResult.failure("LuckPerms update failed: " + mutationResult.errorMessage(), finalStatus, failedTx.transactionId(), RankupPromotionResultCode.LUCKPERMS_UNAVAILABLE);
                                    return saveTransaction(execution, failedTx, PromotionStage.LUCKPERMS_COMPLETED)
                                            .thenCompose(v -> CompletableFuture.failedFuture(new PromotionPipelineException(failRes)));
                                });
                    }
                    RankupTransaction updated = tx.withLuckpermsUpdated(true).withStatus(RankupTransactionStatus.LUCKPERMS_UPDATED);
                    return saveTransaction(execution, updated, PromotionStage.LUCKPERMS_COMPLETED).thenApply(v -> updated);
                });
    }

    private CompletableFuture<RankupTransaction> clearProgress(PromotionExecution execution, RankupTransaction tx, UUID uuid, RankupConfig config) {
        if (tx.progressCleared()) {
            return CompletableFuture.completedFuture(tx);
        }
        logStage(execution, PromotionStage.PROGRESS_CLEAR_STARTED, "start", null);
        return manager.getTaskProgressService().resetLadderProgress(uuid, tx.ladderId())
                .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                .thenCompose(v -> {
                    RankupTransaction updated = tx.withProgressCleared(true);
                    return saveTransaction(execution, updated, PromotionStage.PROGRESS_CLEAR_COMPLETED).thenApply(ignored -> updated);
                });
    }

    private CompletableFuture<RankupTransaction> writeHistory(PromotionExecution execution, RankupTransaction tx, UUID uuid, boolean executeActions) {
        if (tx.historyWritten()) {
            return CompletableFuture.completedFuture(tx);
        }
        logStage(execution, PromotionStage.HISTORY_STARTED, "start", null);
        com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause cause = executeActions ? com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.NORMAL_RANKUP : com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.ADMIN_PROMOTE;
        RankupRankHistoryEntry history = new RankupRankHistoryEntry(
                null, uuid, tx.ladderId(),
                tx.fromRankId(), tx.toRankId(),
                uuid.toString(), cause.name(),
                System.currentTimeMillis()
        );
        return manager.getRepository().addRankHistory(history)
                .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                .thenCompose(v -> {
                    RankupTransaction updated = tx.withHistoryWritten(true);
                    return saveTransaction(execution, updated, PromotionStage.HISTORY_COMPLETED).thenApply(ignored -> updated);
                });
    }

    private CompletableFuture<RankupTransaction> executeActionsOnServer(PromotionExecution execution, RankupTransaction tx, ServerPlayer player,
                                                                         UUID uuid, RankupRank currentRank, RankupRank targetRank, boolean executeActions) {
        if (tx.actionsExecuted()) {
            return CompletableFuture.completedFuture(tx);
        }
        logStage(execution, PromotionStage.ACTIONS_STARTED, "start", null);
        MinecraftServer server = player != null ? player.getServer() : Platform.getCurrentServer();
        CompletableFuture<Void> serverStep = runOnServerThread(server, () -> {
            com.pedrodalben.bigbangessentials.api.rankup.RankTransitionCompletedEvent event = new com.pedrodalben.bigbangessentials.api.rankup.RankTransitionCompletedEvent(
                    UUID.nameUUIDFromBytes(tx.transactionId().getBytes()),
                    uuid,
                    tx.fromRankId(),
                    currentRank != null ? currentRank.order() : 0,
                    tx.toRankId(),
                    targetRank.order(),
                    executeActions ? com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.NORMAL_RANKUP : com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.ADMIN_PROMOTE,
                    java.time.Instant.now()
            );
            manager.getTransitionService().fireTransitionEvent(event);
            if (executeActions && player != null) {
                executePostRankActions(player, uuid, currentRank, targetRank);
            }
            if (manager.getPlaceholderService() != null) {
                manager.getPlaceholderService().refresh(uuid);
            }
        }).orTimeout(serverThreadTimeoutSeconds(), TimeUnit.SECONDS);

        return serverStep.thenCompose(v -> {
            RankupTransaction updated = tx.withActionsExecuted(true);
            return saveTransaction(execution, updated, PromotionStage.ACTIONS_COMPLETED).thenApply(ignored -> updated);
        });
    }

    private CompletableFuture<RankupPromotionResult> completeTransaction(PromotionExecution execution, RankupTransaction tx, RankupRank targetRank) {
        RankupTransaction completed = tx.withStatus(RankupTransactionStatus.COMPLETED);
        logStage(execution, PromotionStage.TRANSACTION_COMPLETED, "start", null);
        return saveTransaction(execution, completed, PromotionStage.TRANSACTION_COMPLETED)
                .thenApply(v -> RankupPromotionResult.success("Promoted to " + targetRank.displayName(), completed.transactionId()));
    }

    private CompletableFuture<Void> saveTransaction(PromotionExecution execution, RankupTransaction tx, PromotionStage nextStage) {
        return manager.getRepository().saveTransaction(tx)
                .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                .whenComplete((v, err) -> logStage(execution, nextStage, err == null ? "ok" : "error", err));
    }

    private CompletableFuture<RankupPromotionResult> handlePipelineError(PromotionExecution execution, UUID uuid, RankupTransaction currentTx, RankupRank targetRank, Throwable err) {
        Throwable root = unwrap(err);
        if (root instanceof PromotionPipelineException ppe) {
            return CompletableFuture.completedFuture(ppe.getResult());
        }

        LOGGER.error("[RankUp] Unhandled error in promotion pipeline for player {} (tx={}, stage={}, target={})",
                uuid,
                currentTx != null ? currentTx.transactionId() : execution.transactionId,
                execution.stage,
                targetRank != null ? targetRank.id() : "unknown",
                err);

        if (currentTx == null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure(buildFailureMessage(execution, root), RankupTransactionStatus.FAILED, execution.transactionId, RankupPromotionResultCode.INTERNAL_ERROR));
        }

        return compensate(uuid, currentTx)
                .thenCompose(compensatedTx -> {
                    RankupTransactionStatus finalStatus = compensatedTx.compensated() ? RankupTransactionStatus.COMPENSATED : RankupTransactionStatus.RECOVERY_REQUIRED;
                    RankupTransaction failedTx = compensatedTx.withStatus(finalStatus).withErrorMessage(root.getMessage());
                    return manager.getRepository().saveTransaction(failedTx)
                            .exceptionally(saveErr -> {
                                LOGGER.error("[RankUp] Failed to save failed transaction state to database (tx={})", failedTx.transactionId(), saveErr);
                                return null;
                            })
                            .thenApply(v -> RankupPromotionResult.failure(buildFailureMessage(execution, root), finalStatus, failedTx.transactionId()));
                });
    }

    private RankupPromotionResult handlePromotionFailure(PromotionExecution execution, Throwable error) {
        Throwable root = unwrap(error);
        RankupTransactionStatus status = root instanceof TimeoutException ? RankupTransactionStatus.RECOVERY_REQUIRED : RankupTransactionStatus.FAILED;
        String message = buildFailureMessage(execution, root);
        return RankupPromotionResult.failure(message, status, execution.transactionId, root instanceof TimeoutException ? RankupPromotionResultCode.TIMEOUT : RankupPromotionResultCode.INTERNAL_ERROR);
    }

    private String buildFailureMessage(PromotionExecution execution, Throwable root) {
        String stage = execution.stage != null ? execution.stage.name() : "UNKNOWN";
        String id = execution.transactionId != null ? execution.transactionId : "unknown";
        return "Não foi possível concluir sua promoção. A operação parou na etapa " + stage + " e foi enviada para recuperação. Código da transação: " + id + (root != null && root.getMessage() != null ? " | " + root.getMessage() : "");
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current != current.getCause()) {
            current = current.getCause();
        }
        return current;
    }

    private void releaseExecution(UUID uuid, PromotionExecution expected) {
        if (promotionQueue.remove(uuid, expected)) {
            logStage(expected, PromotionStage.QUEUE_RELEASED, "ok", null);
            refreshPromotionState(expected.playerUuid);
        }
    }

    private void refreshPromotionState(UUID playerUuid) {
        try {
            MinecraftServer server = Platform.getCurrentServer();
            if (server == null || playerUuid == null) return;
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(playerUuid);
                    if (player != null && MenuSystem.getInstance() != null) {
                        MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                    }
                    if (manager.getPlaceholderService() != null) {
                        manager.getPlaceholderService().refresh(playerUuid);
                    }
                } catch (Exception e) {
                    LOGGER.debug("[RankUp] Failed to refresh promotion UI for {}", playerUuid, e);
                }
            });
        } catch (Exception e) {
            LOGGER.debug("[RankUp] Unable to schedule promotion UI refresh for {}", playerUuid, e);
        }
    }

    private CompletableFuture<Void> runOnServerThread(MinecraftServer server, Runnable task) {
        if (server == null) {
            try {
                task.run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private void logStage(PromotionExecution execution, PromotionStage stage, String result, Throwable error) {
        execution.stage = stage;
        long elapsed = System.currentTimeMillis() - execution.startedAtMs;
        String errorText = error != null ? error.getClass().getSimpleName() + ":" + error.getMessage() : "";
        LOGGER.info("[RANKUP-PROMOTION] player_uuid={} transaction_id={} target_rank={} stage={} thread={} elapsed_ms={} result={} error={}",
                execution.playerUuid,
                execution.transactionId,
                execution.targetRankId,
                stage.name(),
                Thread.currentThread().getName(),
                elapsed,
                result,
                errorText);
    }

    protected long promotionTimeoutSeconds() {
        RankupConfig config = manager.getConfig();
        return config != null ? config.getPromotionTimeouts().promotionTimeoutSeconds() : 20L;
    }

    protected long databaseTimeoutSeconds() {
        RankupConfig config = manager.getConfig();
        return config != null ? config.getPromotionTimeouts().databaseStepTimeoutSeconds() : 5L;
    }

    protected long luckpermsTimeoutSeconds() {
        RankupConfig config = manager.getConfig();
        return config != null ? config.getPromotionTimeouts().luckpermsStepTimeoutSeconds() : 8L;
    }

    protected long serverThreadTimeoutSeconds() {
        RankupConfig config = manager.getConfig();
        return config != null ? config.getPromotionTimeouts().serverThreadStepTimeoutSeconds() : 5L;
    }

    private static class PromotionPipelineException extends RuntimeException {
        private final RankupPromotionResult result;

        public PromotionPipelineException(RankupPromotionResult result) {
            super(result.message());
            this.result = result;
        }

        public RankupPromotionResult getResult() {
            return result;
        }
    }

    public CompletableFuture<RankupTransaction> compensate(UUID uuid, RankupTransaction transaction) {
        if (!activeCompensations.add(transaction.transactionId())) {
            return CompletableFuture.completedFuture(transaction);
        }

        LOGGER.info("[RANKUP-COMPENSATION] Starting compensation for transaction {} (status={})", transaction.transactionId(), transaction.status());

        RankupConfig config = manager.getConfig();
        RankupRank fromRank = config != null ? config.getRank(transaction.fromRankId()) : null;
        RankupRank toRank = config != null ? config.getRank(transaction.toRankId()) : null;

        CompletableFuture<RankupTransaction> step1;
        if (transaction.luckpermsUpdated() && !transaction.compensated()) {
            if (config != null && toRank != null) {
                step1 = manager.getLuckPermsService().revertRankChange(uuid, fromRank, toRank, config)
                        .orTimeout(luckpermsTimeoutSeconds(), TimeUnit.SECONDS)
                        .thenCompose(res -> {
                            if (res.success()) {
                                RankupTransaction updated = transaction.withLuckpermsUpdated(false);
                                return manager.getRepository().saveTransaction(updated).thenApply(v -> updated);
                            }
                            LOGGER.warn("[RANKUP-COMPENSATION] LuckPerms revert failed for transaction {}", transaction.transactionId());
                            return CompletableFuture.completedFuture(transaction);
                        });
            } else {
                step1 = CompletableFuture.completedFuture(transaction);
            }
        } else {
            step1 = CompletableFuture.completedFuture(transaction);
        }

        CompletableFuture<RankupTransaction> step2 = step1.thenCompose(tx -> {
            if (tx.moneyDebited() && !tx.compensated() && tx.moneyAmount().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    boolean ok = EconomyManager.getInstance().credit(uuid, tx.moneyAmount(), "rankup:refund:" + tx.idempotencyKey(), "Rankup refund", Map.of("source", "rankup", "reference", tx.transactionId().toString())).status() == EconomyOperationStatus.COMPLETED;
                    if (ok) {
                        RankupTransaction updated = tx.withMoneyDebited(false);
                        return manager.getRepository().saveTransaction(updated)
                                .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                                .thenApply(v -> updated);
                    }
                } catch (Exception e) {
                    LOGGER.error("[RANKUP-COMPENSATION] Failed to deposit refund for transaction {}", tx.transactionId(), e);
                }
            }
            return CompletableFuture.completedFuture(tx);
        });

        CompletableFuture<RankupTransaction> step3 = step2.thenCompose(tx -> {
            if (tx.gemsDebited() && !tx.compensated() && tx.gemsAmount() > 0) {
                try {
                    GemCreditRequest creditRequest = new GemCreditRequest(
                            uuid, tx.gemsAmount(), "rankup", "compensation",
                            null, tx.idempotencyKey() + ":comp", tx.transactionId(), Map.of()
                    );
                    GemOperationResult result = GemsManager.getInstance().credit(creditRequest);
                    if (result.success()) {
                        RankupTransaction updated = tx.withGemsDebited(false);
                        return manager.getRepository().saveTransaction(updated)
                                .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                                .thenApply(v -> updated);
                    }
                } catch (Exception e) {
                    LOGGER.error("[RANKUP-COMPENSATION] Failed to refund gems for transaction {}", tx.transactionId(), e);
                }
            }
            return CompletableFuture.completedFuture(tx);
        });

        return step3.thenCompose(tx -> {
            boolean fullyCompensated = !tx.luckpermsUpdated() && !tx.moneyDebited() && !tx.gemsDebited();
            RankupTransaction compensatedTx = tx.withCompensated(fullyCompensated);
            if (fullyCompensated) {
                compensatedTx = compensatedTx.withStatus(RankupTransactionStatus.COMPENSATED);
            } else {
                compensatedTx = compensatedTx.withStatus(RankupTransactionStatus.RECOVERY_REQUIRED);
            }
            RankupTransaction finalTx = compensatedTx;
            LOGGER.info("[RANKUP-COMPENSATION] Compensation result for transaction {}: fullyCompensated={}", finalTx.transactionId(), fullyCompensated);
            return manager.getRepository().saveTransaction(finalTx)
                    .orTimeout(databaseTimeoutSeconds(), TimeUnit.SECONDS)
                    .thenApply(v -> finalTx);
        }).orTimeout(promotionTimeoutSeconds(), TimeUnit.SECONDS)
          .whenComplete((res, err) -> {
              activeCompensations.remove(transaction.transactionId());
              if (err != null) {
                  LOGGER.error("[RANKUP-COMPENSATION] Compensation failed for transaction {}", transaction.transactionId(), err);
              }
          });
    }

    public void executePostRankActions(UUID playerId, RankupRank fromRank, RankupRank toRank) {
        ServerPlayer player = null;
        try {
            MinecraftServer server = Platform.getCurrentServer();
            if (server != null && playerId != null) {
                player = server.getPlayerList().getPlayer(playerId);
            }
        } catch (Exception ignored) {}
        executePostRankActions(player, playerId, fromRank, toRank);
    }

    public void executePostRankActions(ServerPlayer player, UUID playerId, RankupRank fromRank, RankupRank toRank) {
        RankupActions actions = toRank.actions();
        if (actions == null) return;

        MinecraftServer server = null;
        if (player != null) {
            server = player.getServer();
        }
        if (server == null) {
            server = Platform.getCurrentServer();
        }
        if (playerId == null && player != null) {
            playerId = player.getUUID();
        }
        if (playerId == null) return;

        String playerName = playerId.toString();
        if (player != null && player.getName() != null) {
            playerName = player.getName().getString();
        } else if (server != null) {
            java.util.Optional<com.mojang.authlib.GameProfile> profile = server.getProfileCache().get(playerId);
            if (profile.isPresent()) {
                playerName = profile.get().getName();
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", playerName);
        placeholders.put("%uuid%", playerId.toString());
        placeholders.put("%old_rank_id%", fromRank != null ? fromRank.id() : "");
        placeholders.put("%old_rank_display_name%", fromRank != null ? fromRank.displayName() : "");
        placeholders.put("%rank_id%", toRank.id());
        placeholders.put("%rank_display_name%", toRank.displayName());
        placeholders.put("%luckperms_group%", toRank.luckPerms().group());

        if (actions.broadcast() != null && !actions.broadcast().isBlank()) {
            String message = replacePlaceholders(actions.broadcast(), placeholders);
            broadcastToServer(server, message);
        }

        for (String cmd : actions.commands()) {
            String normalized = cmd.trim();
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            normalized = replacePlaceholders(normalized, placeholders);
            runConsoleCommand(server, normalized);
        }
    }

    private void broadcastToServer(MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) return;
        server.execute(() -> server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(message), false));
    }

    private void runConsoleCommand(MinecraftServer server, String command) {
        if (server == null || command == null || command.isBlank()) return;
        server.execute(() -> {
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
            } catch (Exception e) {
                LOGGER.error("Failed to run RankUp post-action command '{}'", command, e);
            }
        });
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}
