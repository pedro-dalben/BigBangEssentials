package com.pedrodalben.bigbangessentials.jobs.rewards;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

public class JobRewardLimitService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobRewardLimitService.class);
    private static final JobRewardLimitService INSTANCE = new JobRewardLimitService();

    public static JobRewardLimitService getInstance() {
        return INSTANCE;
    }

    private JobRewardLimitService() {}

    public LimitCheckResult checkLimit(UUID playerUuid, String jobId, JobKeyDropRule rule) {
        if (playerUuid == null || jobId == null || rule == null) {
            return new LimitCheckResult(false, "Invalid arguments");
        }

        return checkLimitInternal(playerUuid, jobId, rule.getCooldownSeconds(), rule.getMaxKeysPerJobPerDay(), rule.getMaxKeysTotalPerDay());
    }

    public LimitCheckResult checkLimit(UUID playerUuid, String jobId, CrateRewardDefinition reward) {
        if (playerUuid == null || jobId == null || reward == null) {
            return new LimitCheckResult(false, "Invalid arguments");
        }

        return checkLimitInternal(playerUuid, jobId, reward.cooldownSeconds(), reward.dailyLimit(), reward.dailyLimit());
    }

    private LimitCheckResult checkLimitInternal(UUID playerUuid, String jobId, long cooldownSeconds, int maxPerJob, int maxTotal) {
        long now = System.currentTimeMillis();
        long latestDrop = JobRewardRollRepository.getInstance().getLatestSuccessfulRollTimestamp(playerUuid, jobId);
        if (latestDrop > 0) {
            long elapsedSeconds = (now - latestDrop) / 1000L;
            if (elapsedSeconds < cooldownSeconds) {
                long remaining = cooldownSeconds - elapsedSeconds;
                return new LimitCheckResult(false, "Cooldown ativo: aguarde " + remaining + "s para outro drop de chave em " + jobId);
            }
        }

        String timezone = getConfiguredTimezone();
        long startOfDay = getStartOfDayMillis(timezone);
        int jobDropsToday = JobRewardRollRepository.getInstance().countSuccessfulRollsForJobSince(playerUuid, jobId, startOfDay);
        if (maxPerJob > 0 && jobDropsToday >= maxPerJob) {
            return new LimitCheckResult(false, "Limite diário de chaves para a profissão (" + maxPerJob + ") atingido");
        }

        int totalDropsToday = JobRewardRollRepository.getInstance().countTotalSuccessfulRollsSince(playerUuid, startOfDay);
        if (maxTotal > 0 && totalDropsToday >= maxTotal) {
            return new LimitCheckResult(false, "Limite diário total de chaves (" + maxTotal + ") atingido");
        }

        return new LimitCheckResult(true, "OK");
    }

    private String getConfiguredTimezone() {
        try {
            var config = com.pedrodalben.bigbangessentials.jobs.JobsManager.getInstance().getConfig();
            if (config != null && config.getDailyLimitTimezone() != null && !config.getDailyLimitTimezone().isBlank()) {
                return config.getDailyLimitTimezone();
            }
        } catch (Exception ignored) {}
        return "America/Sao_Paulo";
    }

    private long getStartOfDayMillis(String timezone) {
        try {
            ZoneId zone = ZoneId.of(timezone);
            ZonedDateTime now = ZonedDateTime.now(zone);
            return now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis() - 86400000L;
        }
    }

    public record LimitCheckResult(boolean allowed, String reason) {}
}
