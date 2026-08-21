package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.JobDailyLimitReachedEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

public class JobMessageService {
    private static final JobMessageService INSTANCE = new JobMessageService();

    private JobMessageService() {}

    public static JobMessageService getInstance() {
        return INSTANCE;
    }

    public void checkDailyLimitWarnings(ServerPlayer player, PlayerJobsData data, String jobId, double currentEarnings, double dailyLimit) {
        if (dailyLimit <= 0.0) return;

        double pct = (currentEarnings / dailyLimit) * 100.0;
        int triggered = 0;
        if (pct >= 100.0) {
            triggered = 100;
        } else if (pct >= 90.0) {
            triggered = 90;
        } else if (pct >= 80.0) {
            triggered = 80;
        }

        if (triggered > 0) {
            boolean alreadyTriggered = data.getTriggeredThresholds().contains(triggered);
            if (!alreadyTriggered) {
                data.getTriggeredThresholds().add(triggered);

                long now = System.currentTimeMillis();
                if (now - data.getLastLimitMessageTime() > 10000) { // 10s message cooldown
                    data.setLastLimitMessageTime(now);

                    JobDefinition jobDef = JobsManager.getInstance().getConfig().getJob(jobId);
                    String jobName = jobDef != null ? jobDef.displayName : jobId;

                    if (triggered == 100) {
                        player.sendSystemMessage(Component.literal(
                                String.format("§cVocê atingiu o limite diário de $%.2f da profissão %s. Você continuará recebendo XP, mas não receberá mais dinheiro até o próximo reset.", dailyLimit, jobName)
                        ));
                        com.pedrodalben.bigbangessentials.util.Platform.postEvent(new JobDailyLimitReachedEvent(player.getUUID(), jobId, dailyLimit, currentEarnings));
                    } else {
                        player.sendSystemMessage(Component.literal(
                                String.format("§eVocê atingiu %d%% do limite diário ($%.2f) da profissão %s.", triggered, dailyLimit, jobName)
                        ));
                    }
                }
            }
        }
    }

    public void sendActionBarNotification(ServerPlayer player, JobDefinition jobDef, double payout, double xp) {
        StringBuilder sb = new StringBuilder();
        if (payout > 0.0) {
            sb.append(String.format("§a+ $%.2f", payout));
        }
        if (xp > 0.0) {
            if (sb.length() > 0) sb.append(" §7| ");
            sb.append(String.format("§e%s +%.1f XP", jobDef.displayName, xp));
        }
        if (sb.length() > 0) {
            player.displayClientMessage(Component.literal(sb.toString()), true);
        }
    }
}
