package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.api.EconomyAPI;
import com.pedrodalben.bigbangessentials.jobs.*;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository;
import com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.*;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Applies calculated rewards to the player: deposits money, updates daily earnings, adds XP, and sends notifications.
 */
public class JobRewardApplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobRewardApplier.class);
    private static final JobRewardApplier INSTANCE = new JobRewardApplier();

    public static JobRewardApplier getInstance() {
        return INSTANCE;
    }

    private JobRewardApplier() {}

    public void apply(ServerPlayer player, PlayerJobsData data, JobDefinition jobDef, JobAction action, JobRewardOutcome outcome, JobsRepository repository) {
        if (player == null || data == null || jobDef == null || outcome == null || !outcome.success()) {
            return;
        }

        UUID playerId = player.getUUID();
        String jobId = jobDef.id;
        double allowedPayout = outcome.coins();
        double finalXp = outcome.experience();

        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config == null) return;

        // 1. Apply Money
        if (allowedPayout > 0.0) {
            boolean deposited = EconomyAPI.deposit(playerId, BigDecimal.valueOf(allowedPayout));
            if (deposited) {
                double currentEarnings = data.getDailyEarnings(jobId);
                double newEarnings = currentEarnings + allowedPayout;
                data.setDailyEarnings(jobId, newEarnings);
                if (repository != null) {
                    repository.savePlayerJobEarnings(playerId, jobId, data.getCurrentCycleStart(), newEarnings);
                }

                com.pedrodalben.bigbangessentials.util.Platform.postEvent(new JobRewardPaidEvent(playerId, jobId, allowedPayout));

                if (config.isDailyLimitEnabled()) {
                    double dailyLimit = JobDailyLimitService.getInstance().getDailyLimit(jobDef, config, player);
                    if (dailyLimit > 0.0) {
                        JobMessageService.getInstance().checkDailyLimitWarnings(player, data, jobId, newEarnings, dailyLimit);
                    }
                }
            } else {
                LOGGER.error("Failed to deposit jobs reward of {} for player {}", allowedPayout, player.getName().getString());
            }
        }

        // 2. Apply XP
        if (finalXp > 0.0) {
            JobExperienceService.getInstance().addExperience(player, data, jobId, finalXp);
        }

        // 3. Execute Side Effects (if any)
        if (!outcome.sideEffects().isEmpty()) {
            for (JobRewardSideEffect effect : outcome.sideEffects()) {
                try {
                    effect.apply();
                } catch (Exception e) {
                    LOGGER.error("Error applying job reward side effect {} for player {}", effect.getType(), playerId, e);
                }
            }
        }

        // 3.5. Process Phase 4 Reward Cycle (Fragments, Key Rolls & Contracts)
        try {
            int jobLevel = data.getProgress(jobId) != null ? data.getProgress(jobId).getLevel() : 1;
            String actionType = action.type() != null ? action.type().name() : action.actionId().toString();
            com.pedrodalben.bigbangessentials.jobs.rewards.JobRewardRollService.getInstance()
                    .processActionRewards(playerId, jobDef, jobLevel, actionType, outcome.experience() > 0 ? 1.0 : 0.5);
            com.pedrodalben.bigbangessentials.jobs.contracts.JobContractService.getInstance()
                    .processActionProgress(player, action, jobId);
        } catch (Exception e) {
            LOGGER.error("Error processing reward cycle & contracts for player {}", playerId, e);
        }

        // 4. Notifications
        if (data.isNotificationsEnabled() && (allowedPayout > 0.0 || finalXp > 0.0)) {
            JobMessageService.getInstance().sendActionBarNotification(player, jobDef, allowedPayout, finalXp);
        }

        // 5. Handle exploration discovery confirmation
        if (action.type() == JobActionType.EXPLORE && action.context() != null && action.context().isFirstDiscovery()) {
            String discoveryType = determineDiscoveryType(action);
            String discoveryKey = action.targetId();
            if (!discoveryType.isEmpty() && !discoveryKey.isEmpty()) {
                com.pedrodalben.bigbangessentials.jobs.antiexploit.ExplorationDiscoveryService.getInstance()
                        .confirmDiscovery(playerId, discoveryType, discoveryKey);
            }
        }

        // 6. Debug Mode Logging
        if (data.isDebugMode() || JobsManager.isGlobalDebugMode()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    String.format("§7[Debug] Ação: %s | Alvo: %s | Job: %s | XP: %.2f | Coins: %.2f",
                            action.type().name(), action.targetId(), jobDef.displayName, finalXp, allowedPayout)
            ));
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
