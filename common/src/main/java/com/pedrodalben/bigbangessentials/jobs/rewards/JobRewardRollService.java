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

import java.util.ArrayList;
import java.util.List;
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

        double fragmentChance = 0.25 * (actionWeight > 0 ? actionWeight : 1.0);
        if (ThreadLocalRandom.current().nextDouble() < fragmentChance) {
            JourneyFragmentService.getInstance().addFragments(
                playerUuid, 1L, "JOB_ACTION", actionType, actionType, null, null, "Job action reward: " + jobId
            );
        }

        if (!jobDef.crateRewards.isEmpty()) {
            processConfiguredRewards(playerUuid, jobDef, jobLevel, actionType, actionWeight);
            return;
        }

        processLegacyReward(playerUuid, jobId, jobLevel, actionType, actionWeight);
    }

    private void processConfiguredRewards(UUID playerUuid, JobsConfig.JobDefinition jobDef, int jobLevel, String actionType, double actionWeight) {
        String jobId = jobDef.id;

        List<CrateRewardDefinition> matchingRewards = new ArrayList<>();
        for (CrateRewardDefinition reward : jobDef.crateRewards) {
            if (reward.matchesAction(actionType) && jobLevel >= reward.minimumJobLevel()) {
                matchingRewards.add(reward);
            }
        }
        if (matchingRewards.isEmpty()) return;

        matchingRewards.sort(CrateRewardDefinition.PRIORITY_DESC);

        boolean oneRewardMode = jobDef.crateRewards.stream().anyMatch(CrateRewardDefinition::oneRewardPerAction);

        for (CrateRewardDefinition reward : matchingRewards) {
            processConfiguredReward(playerUuid, jobDef, jobLevel, actionType, actionWeight, reward);
            if (oneRewardMode) {
                break;
            }
        }
    }

    private void processConfiguredReward(UUID playerUuid, JobsConfig.JobDefinition jobDef, int jobLevel, String actionType, double actionWeight, CrateRewardDefinition reward) {
        String jobId = jobDef.id;

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
            CrateKeyGrantResult grantResult;
            if (reward.physicalKey()) {
                grantResult = gateway.grantPhysicalKey(
                    playerUuid, reward.keyId(), reward.amount(), CrateKeyGrantSource.JOB_LUCK, actionType, null
                );
            } else {
                grantResult = gateway.grantVirtualKey(
                    playerUuid, reward.keyId(), reward.amount(), CrateKeyGrantSource.JOB_LUCK, actionType, null
                );
            }
            if (grantResult.success()) {
                LOGGER.info("[CrateReward] Player {} won {} x{} in job {} (level {}, action {}). Reward: {}/{}/{}",
                    playerUuid, reward.keyId(), reward.amount(), jobId, jobLevel, actionType,
                    reward.keyId(), reward.chance(), reward.dailyLimit());
                JobRewardNotificationService.getInstance().notifyKeyFound(playerUuid, reward.keyId(), reward.keyDisplayName());
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
                JobRewardNotificationService.getInstance().notifyKeyFound(playerUuid, "craft_key", "Chave do Ofício");
            }
        }
    }

    private void logSkip(UUID playerUuid, String jobId, String keyId, String reasonCode, String detail) {
        LOGGER.debug("[CrateReward] Skipped {} for player {} in job {}: [{}] {}", keyId, playerUuid, jobId, reasonCode, detail);
    }
}
