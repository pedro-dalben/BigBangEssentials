package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.chat.AfkManager;
import com.pedrodalben.bigbangessentials.jobs.*;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.*;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates XP and Coins without altering existing balancing or formulas.
 * Handles multipliers, permissions, events, and daily limit checks.
 */
public class JobRewardCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobRewardCalculator.class);
    private static final JobRewardCalculator INSTANCE = new JobRewardCalculator();

    public static JobRewardCalculator getInstance() {
        return INSTANCE;
    }

    private JobRewardCalculator() {}

    public JobRewardOutcome calculate(ServerPlayer player, PlayerJobsData data, JobDefinition jobDef, JobProgress progress, JobAction action, ActionReward baseReward, String matchedKey) {
        if (player == null || data == null || jobDef == null || progress == null || baseReward == null) {
            return JobRewardOutcome.failure("Parâmetros nulos no cálculo da recompensa.");
        }

        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config == null) {
            return JobRewardOutcome.failure("Configuração não carregada.");
        }

        boolean isAfk = AfkManager.getInstance().isAfk(player.getUUID());
        boolean preventEarnings = isAfk && config.isPreventEarningsWhileAfk();
        boolean preventXp = isAfk && config.isPreventXpWhileAfk();

        int amount = action.context() != null ? action.context().getCustomAttributeAsInt("amount", 1) : 1;
        if (amount < 1) amount = 1;

        // 1. Calculate Coins (Money)
        double baseMoney = baseReward.money * amount;
        double levelMultiplier = JobRewardService.getInstance().calculateLevelMultiplier(progress.getLevel(), jobDef);
        double skillMultiplier = JobsManager.getInstance().calculateSkillMultiplier(data, jobDef, "money-multiplier");
        double permissionMultiplier = JobsManager.getInstance().getGanhosPermissionMultiplier(player);
        double tempMultiplier = 1.0;

        JobRewardCalculateEvent calcEvent = new JobRewardCalculateEvent(
                player.getUUID(), jobDef.id, baseMoney, levelMultiplier, skillMultiplier, permissionMultiplier, tempMultiplier
        );
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(calcEvent);

        if (calcEvent.isCanceled()) {
            if (data.isDebugMode() || JobsManager.isGlobalDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        String.format("§7[Debug] Evento de recompensa cancelado para a ação %s (%s). Motivo: EVENT_CANCELLED", action.type().name(), action.targetId())
                ));
            }
            return JobRewardOutcome.failure("JobRewardCalculateEvent cancelado.");
        }

        double finalPayout = calcEvent.getFinalAmount();
        double dailyLimit = JobDailyLimitService.getInstance().getDailyLimit(jobDef, config, player);
        double currentEarnings = data.getDailyEarnings(jobDef.id);
        double allowedPayout = finalPayout;
        boolean limitReached = false;

        if (config.isDailyLimitEnabled() && dailyLimit > 0.0) {
            allowedPayout = JobDailyLimitService.getInstance().calculatePayoutAfterLimits(currentEarnings, finalPayout, dailyLimit, config.isDailyLimitEnabled());
            if (currentEarnings >= dailyLimit || currentEarnings + finalPayout > dailyLimit) {
                limitReached = true;
            }
        }

        if (preventEarnings) {
            allowedPayout = 0.0;
        }

        // 2. Calculate XP
        double baseXp = baseReward.xp * amount;
        double skillXpMultiplier = JobsManager.getInstance().calculateSkillMultiplier(data, jobDef, "xp-multiplier");
        double permissionXpMultiplier = JobsManager.getInstance().getXpPermissionMultiplier(player);
        double finalXp = baseXp * skillXpMultiplier * permissionXpMultiplier;

        if (preventXp) {
            finalXp = 0.0;
        }

        if (limitReached && !config.isContinueXpAfterLimit()) {
            finalXp = 0.0;
        }

        if (finalXp > 0.0) {
            JobExperienceGainEvent xpEvent = new JobExperienceGainEvent(player.getUUID(), jobDef.id, finalXp);
            com.pedrodalben.bigbangessentials.util.Platform.postEvent(xpEvent);
            if (xpEvent.isCanceled()) {
                if (data.isDebugMode() || JobsManager.isGlobalDebugMode()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            String.format("§7[Debug] XP cancelado. Motivo: EVENT_CANCELLED")
                    ));
                }
                finalXp = 0.0;
            } else {
                finalXp = xpEvent.getAmount();
            }
        }

        if (allowedPayout <= 0.0 && finalXp <= 0.0) {
            if (limitReached) {
                return JobRewardOutcome.failure("DAILY_LIMIT_REACHED");
            } else if (preventEarnings && preventXp) {
                return JobRewardOutcome.failure("PLAYER_AFK");
            }
        }

        return JobRewardOutcome.success(finalXp, allowedPayout);
    }
}
