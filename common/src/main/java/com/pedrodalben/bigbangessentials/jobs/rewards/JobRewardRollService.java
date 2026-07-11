package com.pedrodalben.bigbangessentials.jobs.rewards;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantResult;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.crates.DefaultCrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.rewards.JobRewardLimitService.LimitCheckResult;
import com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService;
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

    public void processActionRewards(UUID playerUuid, JobsConfig.JobDefinition jobDef, int jobLevel, String actionType, double actionWeight) {
        if (playerUuid == null || jobDef == null || actionType == null) return;

        String jobId = jobDef.id;

        // 1. Journey Fragments (25% chance per valid action, scaled by weight)
        double fragmentChance = 0.25 * (actionWeight > 0 ? actionWeight : 1.0);
        if (ThreadLocalRandom.current().nextDouble() < fragmentChance) {
            JourneyFragmentService.getInstance().addFragments(
                playerUuid, 1L, "JOB_ACTION", actionType, actionType, null, null, "Job action reward: " + jobId
            );
        }

        // 2. Evaluate configured crate rewards (new path)
        if (!jobDef.crateRewards.isEmpty()) {
            for (CrateRewardDefinition reward : jobDef.crateRewards) {
                processConfiguredReward(playerUuid, jobDef, jobLevel, actionType, actionWeight, reward);
            }
            return;
        }

        // 3. Legacy fallback: use hardcoded JobKeyDropRule
        processLegacyReward(playerUuid, jobId, jobLevel, actionType, actionWeight);
    }

    private void processConfiguredReward(UUID playerUuid, JobsConfig.JobDefinition jobDef, int jobLevel, String actionType, double actionWeight, CrateRewardDefinition reward) {
        String jobId = jobDef.id;

        if (!reward.matchesAction(actionType)) return;

        if (jobLevel < reward.minimumJobLevel()) {
            logSkip(playerUuid, jobId, reward.keyId(), "job_level_too_low", "level " + jobLevel + " < required " + reward.minimumJobLevel());
            return;
        }

        if (reward.requiredRankId() != null && !JobRankMilestoneService.getInstance().isAtOrAboveRank(playerUuid, reward.requiredRankId())) {
            logSkip(playerUuid, jobId, reward.keyId(), "rank_too_low", "required rank " + reward.requiredRankId() + " not reached");
            return;
        }

        LimitCheckResult limitCheck = JobRewardLimitService.getInstance().checkLimit(playerUuid, jobId, reward);
        long now = System.currentTimeMillis();
        String rollId = UUID.randomUUID().toString();

        double baseChance = reward.chance();
        double finalChance = baseChance * (actionWeight > 0 ? actionWeight : 1.0);
        double randomVal = ThreadLocalRandom.current().nextDouble();

        if (!limitCheck.allowed()) {
            JobKeyRollResult failRoll = new JobKeyRollResult(
                rollId, actionType, playerUuid, jobId, jobLevel, baseChance, actionWeight, finalChance, randomVal, false, limitCheck.reason(), now
            );
            JobRewardRollRepository.getInstance().saveRoll(failRoll);
            logSkip(playerUuid, jobId, reward.keyId(), "limit_reached", limitCheck.reason());
            return;
        }

        boolean success = randomVal < finalChance;
        String reason = success
            ? "Dropped " + reward.keyId() + " (x" + reward.amount() + ")"
            : "RNG failed (" + String.format("%.4f", randomVal) + " >= " + String.format("%.4f", finalChance) + ")";

        JobKeyRollResult roll = new JobKeyRollResult(
            rollId, actionType, playerUuid, jobId, jobLevel, baseChance, actionWeight, finalChance, randomVal, success, reason, now
        );
        JobRewardRollRepository.getInstance().saveRoll(roll);

        if (success) {
            CrateRewardGateway gateway = DefaultCrateRewardGateway.getInstance();
            CrateKeyGrantResult grantResult = gateway.grantVirtualKey(
                playerUuid, reward.keyId(), reward.amount(), CrateKeyGrantSource.JOB_LUCK, actionType, null
            );
            if (grantResult.success()) {
                LOGGER.info("[CrateReward] Player {} won {} x{} in job {} (level {}, action {}). Reward: {}/{}/{}",
                    playerUuid, reward.keyId(), reward.amount(), jobId, jobLevel, actionType,
                    reward.keyId(), reward.chance(), reward.dailyLimit());
                JobRewardNotificationService.getInstance().notifyKeyFound(playerUuid, reward.keyId());
            } else {
                LOGGER.warn("[CrateReward] Player {} won {} roll but gateway failed: {}", playerUuid, reward.keyId(), grantResult.errorMessage());
            }
        }
    }

    private void processLegacyReward(UUID playerUuid, String jobId, int jobLevel, String actionType, double actionWeight) {
        JobKeyDropRule rule = JobKeyDropRule.defaultConfig();
        LimitCheckResult limitCheck = JobRewardLimitService.getInstance().checkLimit(playerUuid, jobId, rule);
        long now = System.currentTimeMillis();
        String rollId = UUID.randomUUID().toString();

        double baseChance = rule.calculateChance(jobLevel);
        double finalChance = baseChance * (actionWeight > 0 ? actionWeight : 1.0);
        double randomVal = ThreadLocalRandom.current().nextDouble();

        if (!limitCheck.allowed()) {
            JobKeyRollResult failRoll = new JobKeyRollResult(
                rollId, actionType, playerUuid, jobId, jobLevel, baseChance, actionWeight, finalChance, randomVal, false, limitCheck.reason(), now
            );
            JobRewardRollRepository.getInstance().saveRoll(failRoll);
            return;
        }

        boolean success = randomVal < finalChance;
        String reason = success ? "Dropped craft_key" : "RNG failed (" + String.format("%.4f", randomVal) + " >= " + String.format("%.4f", finalChance) + ")";

        JobKeyRollResult roll = new JobKeyRollResult(
            rollId, actionType, playerUuid, jobId, jobLevel, baseChance, actionWeight, finalChance, randomVal, success, reason, now
        );
        JobRewardRollRepository.getInstance().saveRoll(roll);

        if (success) {
            CrateRewardGateway gateway = DefaultCrateRewardGateway.getInstance();
            CrateKeyGrantResult grantResult = gateway.grantVirtualKey(
                playerUuid, "craft_key", 1, CrateKeyGrantSource.JOB_LUCK, actionType, null
            );
            if (grantResult.success()) {
                LOGGER.info("Player {} won craft_key drop in job {} (level {}) on action {}!", playerUuid, jobId, jobLevel, actionType);
                JobRewardNotificationService.getInstance().notifyKeyFound(playerUuid, "craft_key");
            }
        }
    }

    private void logSkip(UUID playerUuid, String jobId, String keyId, String reasonCode, String detail) {
        LOGGER.debug("[CrateReward] Skipped {} for player {} in job {}: [{}] {}", keyId, playerUuid, jobId, reasonCode, detail);
    }
}
