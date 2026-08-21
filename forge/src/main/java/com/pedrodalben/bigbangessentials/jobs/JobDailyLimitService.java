package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import net.minecraft.server.level.ServerPlayer;

public class JobDailyLimitService {
    private static final JobDailyLimitService INSTANCE = new JobDailyLimitService();

    private JobDailyLimitService() {}

    public static JobDailyLimitService getInstance() {
        return INSTANCE;
    }

    public double getDailyLimit(JobDefinition jobDef, JobsConfig config, ServerPlayer player) {
        double limit = jobDef.maxDailyEarnings;
        if (limit < 0.0) {
            limit = config.getDailyLimitGlobal();
        }
        double limitMultiplier = JobPermissionService.getInstance().getDailyLimitPermissionMultiplier(player);
        return limit * limitMultiplier;
    }

    public double calculatePayoutAfterLimits(double currentEarnings, double finalPayout, double dailyLimit, boolean dailyLimitEnabled) {
        if (!dailyLimitEnabled || dailyLimit <= 0.0) {
            return finalPayout;
        }
        if (currentEarnings >= dailyLimit) {
            return 0.0;
        }
        if (currentEarnings + finalPayout > dailyLimit) {
            return dailyLimit - currentEarnings;
        }
        return finalPayout;
    }
}
