package com.pedrodalben.bigbangessentials.jobs.rewards;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public record CrateRewardDefinition(
        List<String> actions,
        String keyId,
        String keyDisplayName,
        double chance,
        int amount,
        int minimumJobLevel,
        String requiredRankId,
        int dailyLimit,
        long cooldownSeconds,
        int priority,
        boolean oneRewardPerAction,
        boolean physicalKey
) {
    public static final Comparator<CrateRewardDefinition> PRIORITY_DESC = (a, b) -> Integer.compare(b.priority, a.priority);

    public CrateRewardDefinition {
        actions = actions != null ? Collections.unmodifiableList(actions) : List.of();
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("key-id cannot be null or blank");
        }
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between 0.0 and 1.0");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be >= 1");
        }
        if (minimumJobLevel < 1) {
            throw new IllegalArgumentException("minimum-job-level must be >= 1");
        }
        if (dailyLimit < 0) {
            throw new IllegalArgumentException("daily-limit must be >= 0");
        }
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("cooldown-seconds must be >= 0");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be >= 0");
        }
        if (keyDisplayName == null || keyDisplayName.isBlank()) {
            keyDisplayName = keyId;
        }
    }

    public boolean matchesAction(String actionType) {
        return actions.isEmpty() || actions.stream().anyMatch(a -> a.equalsIgnoreCase(actionType));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> actions = List.of();
        private String keyId;
        private String keyDisplayName;
        private double chance = 0.005;
        private int amount = 1;
        private int minimumJobLevel = 1;
        private String requiredRankId = null;
        private int dailyLimit = 3;
        private long cooldownSeconds = 1800;
        private int priority = 0;
        private boolean oneRewardPerAction = false;
        private boolean physicalKey = false;

        public Builder actions(List<String> v) { this.actions = v; return this; }
        public Builder keyId(String v) { this.keyId = v; return this; }
        public Builder keyDisplayName(String v) { this.keyDisplayName = v; return this; }
        public Builder chance(double v) { this.chance = v; return this; }
        public Builder amount(int v) { this.amount = v; return this; }
        public Builder minimumJobLevel(int v) { this.minimumJobLevel = v; return this; }
        public Builder requiredRankId(String v) { this.requiredRankId = v; return this; }
        public Builder dailyLimit(int v) { this.dailyLimit = v; return this; }
        public Builder cooldownSeconds(long v) { this.cooldownSeconds = v; return this; }
        public Builder priority(int v) { this.priority = v; return this; }
        public Builder oneRewardPerAction(boolean v) { this.oneRewardPerAction = v; return this; }
        public Builder physicalKey(boolean v) { this.physicalKey = v; return this; }

        public CrateRewardDefinition build() {
            return new CrateRewardDefinition(actions, keyId, keyDisplayName, chance, amount, minimumJobLevel,
                    requiredRankId, dailyLimit, cooldownSeconds, priority, oneRewardPerAction, physicalKey);
        }
    }
}
