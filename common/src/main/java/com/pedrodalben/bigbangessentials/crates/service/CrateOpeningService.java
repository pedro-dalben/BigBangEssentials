package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.PlayerCrateState;
import com.pedrodalben.bigbangessentials.crates.integration.CrateEconomyIntegration;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerMilestoneRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerCrateStateRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerMilestoneRepository;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class CrateOpeningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateOpeningService.class);
    private static final CrateOpeningService INSTANCE = new CrateOpeningService();

    private final CrateKeyService keyService;
    private final RewardService rewardService;
    private final CrateAuditService auditService;
    private final CrateMetricsService metricsService;
    private final PlayerCrateStateRepository playerStateRepo;
    private final PlayerMilestoneRepository milestoneRepo;
    private final CrateEconomyIntegration economyIntegration;
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks;

    private CrateOpeningService() {
        this.keyService = CrateKeyService.getInstance();
        this.rewardService = RewardService.getInstance();
        this.auditService = CrateAuditService.getInstance();
        this.metricsService = CrateMetricsService.getInstance();
        this.playerStateRepo = new JdbcPlayerCrateStateRepository();
        this.milestoneRepo = new JdbcPlayerMilestoneRepository();
        this.economyIntegration = CrateEconomyIntegration.getInstance();
        this.playerLocks = new ConcurrentHashMap<>();
    }

    public static CrateOpeningService getInstance() {
        return INSTANCE;
    }

    public CrateOpeningResult openCrate(ServerPlayer player, CrateDefinition crate, GrantSource source, String idempotencyKey) {
        UUID playerId = player.getUUID();
        ReentrantLock lock = playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());

        if (!lock.tryLock()) {
            LOGGER.warn("Player {} tried to open crate while already opening one", playerId);
            return new CrateOpeningResult(false, "You are already opening a crate!", null);
        }

        try {
            return openCrateInternal(player, crate, source, idempotencyKey);
        } finally {
            lock.unlock();
            playerLocks.remove(playerId);
        }
    }

    private CrateOpeningResult openCrateInternal(ServerPlayer player, CrateDefinition crate, GrantSource source, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CrateOpenAudit> existing = auditService.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                LOGGER.info("Idempotent opening detected for key '{}', skipping", idempotencyKey);
                return new CrateOpeningResult(false, "Already processed", existing.get());
            }
        }

        ValidationResult validation = validateRequirements(player, crate);
        if (!validation.valid()) {
            LOGGER.warn("Player {} failed validation for crate '{}': {}", player.getUUID(), crate.getKey(), validation.message());
            return new CrateOpeningResult(false, validation.message(), null);
        }

        String expectedKey = !crate.getRequirements().getAcceptedKeyIds().isEmpty() ? crate.getRequirements().getAcceptedKeyIds().get(0) : null;
        CrateOpenAudit audit = auditService.createPendingAudit(player.getUUID(), crate, idempotencyKey, source);
        audit.transitionTo(CrateOpenAudit.OpenStatus.RESERVED);
        if (expectedKey != null) {
            audit.setConsumedKey(expectedKey, "EXPECTED", null, 1);
        }
        auditService.saveAudit(audit);

        boolean keyConsumed = false;
        boolean costPaid = false;
        boolean cooldownApplied = false;
        PlayerCrateState savedState = null;

        try {
            if (!crate.getRequirements().getAcceptedKeyIds().isEmpty()) {
                keyConsumed = keyService.consumeKeyForOpening(player, crate, idempotencyKey);
                if (!keyConsumed) {
                    audit.transitionTo(CrateOpenAudit.OpenStatus.FAILED);
                    audit.setFailureReason("Failed to consume key");
                    auditService.saveAudit(audit);
                    return new CrateOpeningResult(false, "Failed to consume key", audit);
                }
                audit.transitionTo(CrateOpenAudit.OpenStatus.KEY_CONSUMED);
                audit.setConsumedKey(expectedKey, "VIRTUAL/PHYSICAL", null, 1);
                auditService.saveAudit(audit);
            }

            if (crate.getRequirements().hasCostRequirement()) {
                costPaid = economyIntegration.withdraw(player.getUUID(), crate.getCost(), "Crate opening: " + crate.getKey());
                if (!costPaid) {
                    audit.setFailureReason("Insufficient funds");
                    rollback(player.getUUID(), crate, keyConsumed, false, false, null, audit);
                    return new CrateOpeningResult(false, "Insufficient funds", audit);
                }
                audit.setCost(crate.getCost(), "PAID");
                auditService.saveAudit(audit);
            }

            if (crate.getRequirements().hasCooldown()) {
                long cooldownUntil = crate.getRequirements().isOneTimeUse() ? Long.MAX_VALUE : System.currentTimeMillis() + crate.getRequirements().getCooldownMillis();
                playerStateRepo.startCooldown(player.getUUID(), crate.getKey(), cooldownUntil);
                cooldownApplied = true;
                audit.setCooldownStatus("APPLIED");
            }

            savedState = playerStateRepo.recordOpening(player.getUUID(), crate.getKey());

            CrateReward selectedReward = rewardService.rollEligibleReward(crate, player);
            if (selectedReward == null) {
                audit.setFailureReason("No eligible rewards available");
                rollback(player.getUUID(), crate, keyConsumed, costPaid, cooldownApplied, savedState, audit);
                return new CrateOpeningResult(false, "No eligible rewards available", audit);
            }

            audit.transitionTo(CrateOpenAudit.OpenStatus.REWARD_SELECTED);
            audit.setSelectedReward(selectedReward.getId(), selectedReward.getName(), null);
            auditService.saveAudit(audit);

            audit.transitionTo(CrateOpenAudit.OpenStatus.DELIVERY_PENDING);
            auditService.saveAudit(audit);

            rewardService.deliverReward(player, selectedReward);

            checkMilestones(player, crate, savedState, audit);

            auditService.completeAudit(audit, CrateOpenAudit.OpenStatus.COMPLETED);

            metricsService.recordOpening(crate.getKey(), true);
            metricsService.recordRewardDelivered(selectedReward.getId());
            if (crate.getRequirements().hasCostRequirement()) {
                metricsService.recordCostSpent(crate.getKey(), crate.getCost());
            }

            LOGGER.info("Player {} opened crate '{}' and received reward '{}'", player.getUUID(), crate.getKey(), selectedReward.getName());
            return new CrateOpeningResult(true, "Success", audit);

        } catch (Exception e) {
            LOGGER.error("Failed to open crate for player {}: {}", player.getUUID(), e.getMessage(), e);
            audit.setFailureReason("Internal error: " + e.getMessage());
            rollback(player.getUUID(), crate, keyConsumed, costPaid, cooldownApplied, savedState, audit);
            metricsService.recordOpening(crate.getKey(), false);
            return new CrateOpeningResult(false, "Internal error: " + e.getMessage(), audit);
        }
    }

    private void rollback(UUID playerId, CrateDefinition crate,
                           boolean keyConsumed, boolean costPaid,
                           boolean cooldownApplied, PlayerCrateState savedState,
                           CrateOpenAudit audit) {
        boolean compFailed = false;
        StringBuilder failReason = new StringBuilder();

        try {
            if (keyConsumed) {
                keyService.giveVirtualKey(playerId, crate.getRequirements().getAcceptedKeyIds().get(0), 1, GrantSource.ROLLBACK, null);
                LOGGER.info("Rollback: restored 1 key for player {}", playerId);
            }
        } catch (Exception e) {
            compFailed = true;
            failReason.append("KeyRestoreFailed: ").append(e.getMessage()).append("; ");
            LOGGER.error("CRITICAL: Rollback failed to restore key for player {}: {}", playerId, e.getMessage(), e);
        }

        try {
            if (costPaid) {
                economyIntegration.deposit(playerId, crate.getCost(), "Rollback: crate opening failed");
                LOGGER.info("Rollback: restored cost of {} for player {}", crate.getCost(), playerId);
            }
        } catch (Exception e) {
            compFailed = true;
            failReason.append("CostRestoreFailed: ").append(e.getMessage()).append("; ");
            LOGGER.error("CRITICAL: Rollback failed to restore cost for player {}: {}", playerId, e.getMessage(), e);
        }

        try {
            if (cooldownApplied) {
                playerStateRepo.clearCooldown(playerId, crate.getKey());
                LOGGER.info("Rollback: cleared cooldown for player {} on crate '{}'", playerId, crate.getKey());
            }
        } catch (Exception e) {
            compFailed = true;
            failReason.append("CooldownClearFailed: ").append(e.getMessage()).append("; ");
            LOGGER.error("CRITICAL: Rollback failed to clear cooldown for player {}: {}", playerId, e.getMessage(), e);
        }

        try {
            if (audit != null) {
                if (compFailed) {
                    audit.transitionTo(CrateOpenAudit.OpenStatus.COMPENSATION_FAILED);
                    audit.setCompensationReason(failReason.toString());
                } else {
                    audit.transitionTo(CrateOpenAudit.OpenStatus.ROLLED_BACK);
                    audit.setCompensationReason("Clean rollback executed");
                }
                auditService.saveAudit(audit);
            }
        } catch (Exception e) {
            LOGGER.error("CRITICAL: Rollback failed to save audit for player {}: {}", playerId, e.getMessage(), e);
        }
    }

    private ValidationResult validateRequirements(ServerPlayer player, CrateDefinition crate) {
        if (!crate.isEnabled()) {
            return new ValidationResult(false, "Crate is disabled");
        }

        if (!crate.hasValidRewards()) {
            return new ValidationResult(false, "Crate has no valid rewards");
        }

        var requirements = crate.getRequirements();

        if (requirements.hasPermissionRequirement()) {
            if (!player.hasPermissions(4)) {
                var permNode = requirements.getRequiredPermission();
                if (permNode == null || permNode.isBlank()
                    || !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                        player.getUUID(), permNode)) {
                    return new ValidationResult(false, "You don't have permission to open this crate");
                }
            }
        }

        PlayerCrateState playerState = playerStateRepo.findByPlayerAndCrate(player.getUUID(), crate.getKey())
            .orElse(null);
        if (playerState != null) {
            if (playerState.isOnCooldown()) {
                return new ValidationResult(false, "Crate is on cooldown");
            }
        }

        if (requirements.hasKeyRequirement()) {
            boolean hasKey = keyService.hasRequiredKey(player, crate);
            if (!hasKey) {
                return new ValidationResult(false, "You don't have the required key");
            }
        }

        if (requirements.hasCostRequirement()) {
            if (!economyIntegration.hasBalance(player.getUUID(), requirements.getRequiredCost())) {
                return new ValidationResult(false, "Insufficient funds");
            }
        }

        return new ValidationResult(true, "OK");
    }

    private void checkMilestones(ServerPlayer player, CrateDefinition crate, PlayerCrateState playerState, CrateOpenAudit audit) {
        var milestones = crate.getMilestones();
        int total = playerState != null ? playerState.getTotalOpened() : 1;
        for (var milestone : milestones) {
            if (!milestone.isActive()) continue;
            int req = milestone.getRequiredOpenings();
            if (req <= 0) continue;

            int mult = 1;
            boolean qualifies = false;
            if (milestone.isRepeatable()) {
                if (total >= req && total % req == 0) {
                    qualifies = true;
                    mult = total / req;
                }
            } else {
                if (total >= req) {
                    qualifies = true;
                    mult = 1;
                }
            }

            if (qualifies) {
                long now = System.currentTimeMillis();
                boolean recorded = milestoneRepo.recordDelivery(player.getUUID(), crate.getKey(), milestone.getId(), mult, now, now, audit != null ? audit.getId().toString() : null, milestone.isRepeatable());
                if (recorded) {
                    var reward = crate.getReward(milestone.getRewardId());
                    if (reward != null && reward.isActive()) {
                        rewardService.deliverReward(player, reward);
                        LOGGER.info("Player {} reached milestone '{}' (mult {}) for crate '{}'", player.getUUID(), milestone.getName(), mult, crate.getKey());
                    }
                }
            }
        }
    }

    public List<CrateOpeningResult> massOpen(ServerPlayer player, CrateDefinition crate, int times, GrantSource source) {
        List<CrateOpeningResult> results = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            String idempotencyKey = player.getUUID() + ":" + crate.getKey() + ":" + System.currentTimeMillis() + ":" + i;
            CrateOpeningResult result = openCrate(player, crate, source, idempotencyKey);
            results.add(result);
            if (!result.success()) break;
        }
        return results;
    }

    public void reload() {
    }

    public record CrateOpeningResult(boolean success, String message, CrateOpenAudit audit) {
    }

    private record ValidationResult(boolean valid, String message) {
    }
}
