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

        long now = System.currentTimeMillis();
        long latestDrop = JobRewardRollRepository.getInstance().getLatestSuccessfulRollTimestamp(playerUuid, jobId);
        if (latestDrop > 0) {
            long elapsedSeconds = (now - latestDrop) / 1000L;
            if (elapsedSeconds < rule.getCooldownSeconds()) {
                long remaining = rule.getCooldownSeconds() - elapsedSeconds;
                return new LimitCheckResult(false, "Cooldown ativo: aguarde " + remaining + "s para outro drop de chave em " + jobId);
            }
        }

        long startOfDay = getStartOfDayMillis("America/Sao_Paulo");
        int jobDropsToday = JobRewardRollRepository.getInstance().countSuccessfulRollsForJobSince(playerUuid, jobId, startOfDay);
        if (jobDropsToday >= rule.getMaxKeysPerJobPerDay()) {
            return new LimitCheckResult(false, "Limite diário de chaves para a profissão (" + rule.getMaxKeysPerJobPerDay() + ") atingido");
        }

        int totalDropsToday = JobRewardRollRepository.getInstance().countTotalSuccessfulRollsSince(playerUuid, startOfDay);
        if (totalDropsToday >= rule.getMaxKeysTotalPerDay()) {
            return new LimitCheckResult(false, "Limite diário total de chaves (" + rule.getMaxKeysTotalPerDay() + ") atingido");
        }

        return new LimitCheckResult(true, "OK");
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
