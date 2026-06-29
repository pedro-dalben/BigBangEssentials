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

public class CrateOpeningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateOpeningService.class);
    private static final CrateOpeningService INSTANCE = new CrateOpeningService();

    private final CrateKeyService keyService;
    private final RewardService rewardService;
    private final CrateAuditService auditService;
    private final PlayerCrateStateRepository playerStateRepo;
    private final CrateEconomyIntegration economyIntegration;

    private CrateOpeningService() {
        this.keyService = CrateKeyService.getInstance();
        this.rewardService = RewardService.getInstance();
        this.auditService = CrateAuditService.getInstance();
        this.playerStateRepo = new JdbcPlayerCrateStateRepository();
        this.economyIntegration = CrateEconomyIntegration.getInstance();
    }

    public static CrateOpeningService getInstance() {
        return INSTANCE;
    }

    /**
     * Full crate opening flow with atomicity and error protection.
     */
    public CrateOpeningResult openCrate(ServerPlayer player, CrateDefinition crate, GrantSource source, String idempotencyKey) {
        try {
            // 1. Idempotency check
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<CrateOpenAudit> existing = auditService.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    LOGGER.info("Idempotent opening detected for key '{}', skipping", idempotencyKey);
                    return new CrateOpeningResult(false, "Already processed", existing.get());
                }
            }

            // 2. Validate requirements
            ValidationResult validation = validateRequirements(player, crate);
            if (!validation.valid()) {
                LOGGER.warn("Player {} failed validation for crate '{}': {}",
                    player.getUUID(), crate.getKey(), validation.message());
                return new CrateOpeningResult(false, validation.message(), null);
            }

            // 3. Create audit log entry (PENDING)
            CrateOpenAudit audit = auditService.createPendingAudit(player.getUUID(), crate, idempotencyKey, source);
            auditService.saveAudit(audit);

            // 4. Calculate rewards
            CrateReward selectedReward = calculateReward(crate);
            if (selectedReward == null) {
                audit.setErrorDetail("No eligible rewards available");
                auditService.completeAudit(audit, CrateOpenAudit.OpenStatus.FAILED);
                return new CrateOpeningResult(false, "No eligible rewards available", audit);
            }

            // 5. Consume key/cost
            boolean keyConsumed = false;
            if (!crate.getRequirements().getAcceptedKeyIds().isEmpty()) {
                keyConsumed = keyService.consumeKeyForOpening(player.getUUID(), crate);
                if (!keyConsumed) {
                    audit.setErrorDetail("Failed to consume key");
                    auditService.completeAudit(audit, CrateOpenAudit.OpenStatus.FAILED);
                    return new CrateOpeningResult(false, "Failed to consume key", audit);
                }
            }

            if (crate.getRequirements().hasCostRequirement()) {
                boolean costPaid = economyIntegration.withdraw(player.getUUID(), crate.getCost(), "Crate opening: " + crate.getKey());
                if (!costPaid) {
                    audit.setErrorDetail("Insufficient funds");
                    auditService.completeAudit(audit, CrateOpenAudit.OpenStatus.FAILED);
                    return new CrateOpeningResult(false, "Insufficient funds", audit);
                }
            }

            // 6. Apply cooldown
            PlayerCrateState playerState = playerStateRepo.findByPlayerAndCrate(player.getUUID(), crate.getKey())
                .orElse(new PlayerCrateState(player.getUUID(), crate.getKey()));

            if (crate.getRequirements().hasCooldown()) {
                if (crate.getRequirements().isOneTimeUse()) {
                    playerState.startCooldown(Long.MAX_VALUE);
                } else {
                    playerState.startCooldown(crate.getRequirements().getCooldownMillis());
                }
            }

            playerState.recordOpening();
            playerStateRepo.save(playerState);

            // 7. Deliver rewards
            List<String> rewardIds = new ArrayList<>();
            List<String> rewardNames = new ArrayList<>();
            rewardIds.add(selectedReward.getId());
            rewardNames.add(selectedReward.getName());
            rewardService.deliverReward(player, selectedReward);

            // 8. Check milestones
            checkMilestones(player, crate, playerState);

            // 9. Complete audit
            auditService.completeAudit(audit, CrateOpenAudit.OpenStatus.COMPLETED);

            LOGGER.info("Player {} opened crate '{}' and received reward '{}'",
                player.getUUID(), crate.getKey(), selectedReward.getName());

            return new CrateOpeningResult(true, "Success", audit);

        } catch (Exception e) {
            LOGGER.error("Failed to open crate for player {}: {}", player.getUUID(), e.getMessage(), e);
            return new CrateOpeningResult(false, "Internal error: " + e.getMessage(), null);
        }
    }

    /**
     * Validate all requirements before opening.
     */
    private ValidationResult validateRequirements(ServerPlayer player, CrateDefinition crate) {
        if (!crate.isEnabled()) {
            return new ValidationResult(false, "Crate is disabled");
        }

        if (!crate.hasValidRewards()) {
            return new ValidationResult(false, "Crate has no valid rewards");
        }

        var requirements = crate.getRequirements();

        // Check permission
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

        // Check cooldown
        PlayerCrateState playerState = playerStateRepo.findByPlayerAndCrate(player.getUUID(), crate.getKey())
            .orElse(null);
        if (playerState != null) {
            if (playerState.isOnCooldown()) {
                return new ValidationResult(false, "Crate is on cooldown");
            }
        }

        // Check key requirements
        if (requirements.hasKeyRequirement()) {
            boolean hasKey = keyService.hasRequiredKey(player.getUUID(), crate.getKey());
            if (!hasKey) {
                return new ValidationResult(false, "You don't have the required key");
            }
        }

        // Check economy cost
        if (requirements.hasCostRequirement()) {
            if (!economyIntegration.hasBalance(player.getUUID(), requirements.getRequiredCost())) {
                return new ValidationResult(false, "Insufficient funds");
            }
        }

        return new ValidationResult(true, "OK");
    }

    /**
     * Calculate a reward from the crate using two-stage weighted selection.
     */
    private CrateReward calculateReward(CrateDefinition crate) {
        CrateRarity selectedRarity = rewardService.selectRarityByWeight(crate);
        if (selectedRarity == null) return null;

        return rewardService.selectRewardByWeight(crate, selectedRarity.getId());
    }

    /**
     * Check and deliver milestone rewards.
     */
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

    /**
     * Mass open a crate multiple times.
     */
    public List<CrateOpeningResult> massOpen(ServerPlayer player, CrateDefinition crate, int times, GrantSource source) {
        List<CrateOpeningResult> results = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            String idempotencyKey = UUID.randomUUID() + ":" + i;
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
