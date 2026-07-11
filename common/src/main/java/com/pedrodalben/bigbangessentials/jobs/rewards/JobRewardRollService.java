package com.pedrodalben.bigbangessentials.jobs.rewards;

import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantResult;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.crates.DefaultCrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.rewards.JobRewardLimitService.LimitCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class JobRewardRollService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobRewardRollService.class);
    private static final JobRewardRollService INSTANCE = new JobRewardRollService();

    public static JobRewardRollService getInstance() {
        return INSTANCE;
    }

    private JobRewardRollService() {}

    public void processActionRewards(String actionId, UUID playerUuid, String jobId, int jobLevel, double actionWeight) {
        if (actionId == null || playerUuid == null || jobId == null) return;

        // 1. Check and award Journey Fragments (e.g., 25% chance per valid action scaled by weight)
        double fragmentChance = 0.25 * (actionWeight > 0 ? actionWeight : 1.0);
        if (ThreadLocalRandom.current().nextDouble() < fragmentChance) {
            JourneyFragmentService.getInstance().addFragments(
                playerUuid, 1L, "JOB_ACTION", actionId, actionId, null, null, "Job action reward: " + jobId
            );
        }

        // 2. Evaluate key drop rules
        JobKeyDropRule rule = JobKeyDropRule.defaultConfig();
        LimitCheckResult limitCheck = JobRewardLimitService.getInstance().checkLimit(playerUuid, jobId, rule);
        long now = System.currentTimeMillis();
        String rollId = UUID.randomUUID().toString();

        double baseChance = rule.calculateChance(jobLevel);
        double finalChance = baseChance * (actionWeight > 0 ? actionWeight : 1.0);
        double randomVal = ThreadLocalRandom.current().nextDouble();

        if (!limitCheck.allowed()) {
            JobKeyRollResult failRoll = new JobKeyRollResult(
                rollId, actionId, playerUuid, jobId, jobLevel, baseChance, actionWeight, finalChance, randomVal, false, limitCheck.reason(), now
            );
            JobRewardRollRepository.getInstance().saveRoll(failRoll);
            return;
        }

        boolean success = randomVal < finalChance;
        String reason = success ? "Dropped craft_key" : "RNG failed (" + String.format("%.4f", randomVal) + " >= " + String.format("%.4f", finalChance) + ")";
        
        JobKeyRollResult roll = new JobKeyRollResult(
            rollId, actionId, playerUuid, jobId, jobLevel, baseChance, actionWeight, finalChance, randomVal, success, reason, now
        );
        JobRewardRollRepository.getInstance().saveRoll(roll);

        if (success) {
            CrateRewardGateway gateway = DefaultCrateRewardGateway.getInstance();
            CrateKeyGrantResult grantResult = gateway.grantVirtualKey(
                playerUuid, "craft_key", 1, CrateKeyGrantSource.JOB_LUCK, actionId, null
            );
            if (grantResult.success()) {
                LOGGER.info("Player {} won craft_key drop in job {} (level {}) on action {}!", playerUuid, jobId, jobLevel, actionId);
                JobRewardNotificationService.getInstance().notifyKeyFound(playerUuid, "craft_key");
            }
        }
    }
}
