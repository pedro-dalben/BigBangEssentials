package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.PlayerCrateState;
import com.pedrodalben.bigbangessentials.crates.integration.CrateEconomyIntegration;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerCrateStateRepository;
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
    private final PlayerCrateStateRepository playerStateRepo;
    private final CrateEconomyIntegration economyIntegration;
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks;

    private CrateOpeningService() {
        this.keyService = CrateKeyService.getInstance();
        this.rewardService = RewardService.getInstance();
        this.auditService = CrateAuditService.getInstance();
        this.playerStateRepo = new JdbcPlayerCrateStateRepository();
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
        }
    }

    private CrateOpeningResult openCrateInternal(ServerPlayer player, CrateDefinition crate, GrantSource source, String idempotencyKey) {
        boolean keyConsumed = false;
        boolean costPaid = false;
        boolean cooldownApplied = false;
        PlayerCrateState savedState = null;
        CrateOpenAudit audit = null;

        try {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<CrateOpenAudit> existing = auditService.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    LOGGER.info("Idempotent opening detected for key '{}', skipping", idempotencyKey);
                    return new CrateOpeningResult(false, "Already processed", existing.get());
                }
            }

            ValidationResult validation = validateRequirements(player, crate);
            if (!validation.valid()) {
                LOGGER.warn("Player {} failed validation for crate '{}': {}",
                    player.getUUID(), crate.getKey(), validation.message());
                return new CrateOpeningResult(false, validation.message(), null);
            }

            CrateReward selectedReward = rewardService.rollEligibleReward(crate, player);
            if (selectedReward == null) {
                LOGGER.warn("No eligible rewards for crate '{}'", crate.getKey());
                return new CrateOpeningResult(false, "No eligible rewards available", null);
            }

            if (!crate.getRequirements().getAcceptedKeyIds().isEmpty()) {
                keyConsumed = keyService.consumeKeyForOpening(player, crate);
                if (!keyConsumed) {
                    return new CrateOpeningResult(false, "Failed to consume key", null);
                }
            }

            if (crate.getRequirements().hasCostRequirement()) {
                costPaid = economyIntegration.withdraw(player.getUUID(), crate.getCost(), "Crate opening: " + crate.getKey());
                if (!costPaid) {
                    return new CrateOpeningResult(false, "Insufficient funds", null);
                }
            }

            PlayerCrateState playerState = playerStateRepo.findByPlayerAndCrate(player.getUUID(), crate.getKey())
                .orElse(new PlayerCrateState(player.getUUID(), crate.getKey()));

            if (crate.getRequirements().hasCooldown()) {
                if (crate.getRequirements().isOneTimeUse()) {
                    playerState.startCooldown(Long.MAX_VALUE);
                } else {
                    playerState.startCooldown(crate.getRequirements().getCooldownMillis());
                }
                cooldownApplied = true;
            }

            playerState.recordOpening();
            savedState = playerStateRepo.save(playerState);

            audit = auditService.createPendingAudit(player.getUUID(), crate, idempotencyKey, source);
            auditService.saveAudit(audit);

            rewardService.deliverReward(player, selectedReward);

            checkMilestones(player, crate, playerState);

            auditService.completeAudit(audit, CrateOpenAudit.OpenStatus.COMPLETED);

            LOGGER.info("Player {} opened crate '{}' and received reward '{}'",
                player.getUUID(), crate.getKey(), selectedReward.getName());

            return new CrateOpeningResult(true, "Success", audit);

        } catch (Exception e) {
            LOGGER.error("Failed to open crate for player {}: {}", player.getUUID(), e.getMessage(), e);

            rollback(player.getUUID(), crate, keyConsumed, costPaid, cooldownApplied, savedState, audit);
            return new CrateOpeningResult(false, "Internal error: " + e.getMessage(), audit);
        }
    }

    private void rollback(UUID playerId, CrateDefinition crate,
                           boolean keyConsumed, boolean costPaid,
                           boolean cooldownApplied, PlayerCrateState savedState,
                           CrateOpenAudit audit) {
        try {
            if (keyConsumed) {
                keyService.giveVirtualKey(playerId, crate.getRequirements().getAcceptedKeyIds().get(0), 1, GrantSource.ROLLBACK, null);
                LOGGER.info("Rollback: restored 1 key for player {}", playerId);
            }
        } catch (Exception e) {
            LOGGER.error("Rollback failed to restore key for player {}: {}", playerId, e.getMessage());
        }

        try {
            if (costPaid) {
                economyIntegration.deposit(playerId, crate.getCost(), "Rollback: crate opening failed");
                LOGGER.info("Rollback: restored cost of {} for player {}", crate.getCost(), playerId);
            }
        } catch (Exception e) {
            LOGGER.error("Rollback failed to restore cost for player {}: {}", playerId, e.getMessage());
        }

        try {
            if (cooldownApplied && savedState != null) {
                savedState.clearCooldown();
                playerStateRepo.save(savedState);
                LOGGER.info("Rollback: cleared cooldown for player {} on crate '{}'", playerId, crate.getKey());
            }
        } catch (Exception e) {
            LOGGER.error("Rollback failed to clear cooldown for player {}: {}", playerId, e.getMessage());
        }

        try {
            if (audit != null) {
                auditService.completeAudit(audit, CrateOpenAudit.OpenStatus.ROLLED_BACK);
            }
        } catch (Exception e) {
            LOGGER.error("Rollback failed to mark audit for player {}: {}", playerId, e.getMessage());
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
                boolean hasPerm = false;
                var permNode = requirements.getRequiredPermission();
                if (permNode != null && !permNode.isBlank()) {
                    hasPerm = true;
                }
                if (!hasPerm) {
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
            boolean hasKey = keyService.hasRequiredKey(player, crate.getKey());
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

    private void checkMilestones(ServerPlayer player, CrateDefinition crate, PlayerCrateState playerState) {
        var milestones = crate.getMilestones();
        for (var milestone : milestones) {
            if (milestone.isActive() && milestone.isReached(playerState.getTotalOpened())) {
                var reward = crate.getReward(milestone.getRewardId());
                if (reward != null && reward.isActive()) {
                    rewardService.deliverReward(player, reward);
                    LOGGER.info("Player {} reached milestone '{}' for crate '{}'",
                        player.getUUID(), milestone.getName(), crate.getKey());
                }
            }
        }
    }

    public List<CrateOpeningResult> massOpen(ServerPlayer player, CrateDefinition crate, int times, GrantSource source) {
        List<CrateOpeningResult> results = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            String idempotencyKey = player.getUUID() + ":" + crate.getKey() + ":" + i;
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
