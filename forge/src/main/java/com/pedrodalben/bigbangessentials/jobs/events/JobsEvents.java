package com.pedrodalben.bigbangessentials.jobs.events;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.UUID;

public class JobsEvents {

    public static abstract class JobEvent extends Event {
        private final UUID playerUuid;
        private final String jobId;

        public JobEvent(UUID playerUuid, String jobId) {
            this.playerUuid = playerUuid;
            this.jobId = jobId;
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public String getJobId() { return jobId; }
    }

    public static class JobJoinEvent extends JobEvent /* cancellable */ {
        public JobJoinEvent(UUID playerUuid, String jobId) {
            super(playerUuid, jobId);
        }
    }

    public static class JobLeaveEvent extends JobEvent /* cancellable */ {
        public JobLeaveEvent(UUID playerUuid, String jobId) {
            super(playerUuid, jobId);
        }
    }

    public static class JobExperienceGainEvent extends JobEvent /* cancellable */ {
        private double amount;

        public JobExperienceGainEvent(UUID playerUuid, String jobId, double amount) {
            super(playerUuid, jobId);
            this.amount = amount;
        }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
    }

    public static class JobLevelUpEvent extends JobEvent {
        private final int newLevel;
        private final int skillPointsEarned;

        public JobLevelUpEvent(UUID playerUuid, String jobId, int newLevel, int skillPointsEarned) {
            super(playerUuid, jobId);
            this.newLevel = newLevel;
            this.skillPointsEarned = skillPointsEarned;
        }

        public int getNewLevel() { return newLevel; }
        public int getSkillPointsEarned() { return skillPointsEarned; }
    }

    public static class JobRewardCalculateEvent extends JobEvent /* cancellable */ {
        private final double baseAmount;
        private double levelMultiplier;
        private double skillMultiplier;
        private double permissionMultiplier;
        private double tempMultiplier;
        private double finalAmount;

        public JobRewardCalculateEvent(UUID playerUuid, String jobId, double baseAmount,
                                     double levelMultiplier, double skillMultiplier,
                                     double permissionMultiplier, double tempMultiplier) {
            super(playerUuid, jobId);
            this.baseAmount = baseAmount;
            this.levelMultiplier = levelMultiplier;
            this.skillMultiplier = skillMultiplier;
            this.permissionMultiplier = permissionMultiplier;
            this.tempMultiplier = tempMultiplier;
            recalculate();
        }

        public double getBaseAmount() { return baseAmount; }

        public double getLevelMultiplier() { return levelMultiplier; }
        public void setLevelMultiplier(double m) { this.levelMultiplier = m; recalculate(); }

        public double getSkillMultiplier() { return skillMultiplier; }
        public void setSkillMultiplier(double m) { this.skillMultiplier = m; recalculate(); }

        public double getPermissionMultiplier() { return permissionMultiplier; }
        public void setPermissionMultiplier(double m) { this.permissionMultiplier = m; recalculate(); }

        public double getTempMultiplier() { return tempMultiplier; }
        public void setTempMultiplier(double m) { this.tempMultiplier = m; recalculate(); }

        public double getFinalAmount() { return finalAmount; }
        public void setFinalAmount(double amount) { this.finalAmount = amount; }

        public void recalculate() {
            this.finalAmount = baseAmount * levelMultiplier * skillMultiplier * permissionMultiplier * tempMultiplier;
        }
    }

    public static class JobRewardPaidEvent extends JobEvent {
        private final double finalAmount;

        public JobRewardPaidEvent(UUID playerUuid, String jobId, double finalAmount) {
            super(playerUuid, jobId);
            this.finalAmount = finalAmount;
        }

        public double getFinalAmount() { return finalAmount; }
    }

    public static class JobDailyLimitReachedEvent extends JobEvent {
        private final double limit;
        private final double reachedAmount;

        public JobDailyLimitReachedEvent(UUID playerUuid, String jobId, double limit, double reachedAmount) {
            super(playerUuid, jobId);
            this.limit = limit;
            this.reachedAmount = reachedAmount;
        }

        public double getLimit() { return limit; }
        public double getReachedAmount() { return reachedAmount; }
    }

    public static class JobSkillUnlockEvent extends JobEvent /* cancellable */ {
        private final String skillId;
        private final int rank;

        public JobSkillUnlockEvent(UUID playerUuid, String jobId, String skillId, int rank) {
            super(playerUuid, jobId);
            this.skillId = skillId;
            this.rank = rank;
        }

        public String getSkillId() { return skillId; }
        public int getRank() { return rank; }
    }
}
