package com.pedrodalben.bigbangessentials.jobs.slot;

import java.time.Duration;

/**
 * Service managing cooldowns when switching professions within a job slot.
 */
public class JobSwitchCooldownService {
    private static final JobSwitchCooldownService INSTANCE = new JobSwitchCooldownService();

    public static JobSwitchCooldownService getInstance() {
        return INSTANCE;
    }

    private JobSwitchCooldownService() {}

    public long calculateCooldownUntil(int cooldownMinutes) {
        if (cooldownMinutes <= 0) return 0L;
        return System.currentTimeMillis() + Duration.ofMinutes(cooldownMinutes).toMillis();
    }

    public boolean isOnCooldown(JobSlot slot) {
        return slot != null && slot.cooldownUntil() > System.currentTimeMillis();
    }

    public long getRemainingSeconds(JobSlot slot) {
        if (slot == null) return 0L;
        long diff = slot.cooldownUntil() - System.currentTimeMillis();
        return diff > 0 ? diff / 1000L : 0L;
    }

    public String formatRemainingTime(JobSlot slot) {
        long seconds = getRemainingSeconds(slot);
        if (seconds <= 0) return "0s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}
