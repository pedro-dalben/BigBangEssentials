package com.pedrodalben.bigbangessentials.jobs.catalog;

import java.util.List;
import java.util.Collections;

public record JobCrateTierProfile(
    boolean crateKeysEnabled,
    CrateTier beginnerTier,
    CrateTier intermediateTier,
    CrateTier advancedTier,
    List<CrateTierReward> actionDrops,
    List<CrateTierReward> levelMilestones,
    List<CrateTierReward> contractRewards,
    List<CrateTierReward> rankMilestones
) {
    public static final JobCrateTierProfile DEFAULT = new JobCrateTierProfile(
        false,
        CrateTier.unconfigured("beginner", "Caixa Iniciante"),
        CrateTier.unconfigured("intermediate", "Caixa Intermediária"),
        CrateTier.unconfigured("advanced", "Caixa Avançada"),
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList()
    );

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean crateKeysEnabled = false;
        private CrateTier beginnerTier = CrateTier.unconfigured("beginner", "Caixa Iniciante");
        private CrateTier intermediateTier = CrateTier.unconfigured("intermediate", "Caixa Intermediária");
        private CrateTier advancedTier = CrateTier.unconfigured("advanced", "Caixa Avançada");
        private List<CrateTierReward> actionDrops = Collections.emptyList();
        private List<CrateTierReward> levelMilestones = Collections.emptyList();
        private List<CrateTierReward> contractRewards = Collections.emptyList();
        private List<CrateTierReward> rankMilestones = Collections.emptyList();

        public Builder crateKeysEnabled(boolean v) { this.crateKeysEnabled = v; return this; }
        public Builder beginnerTier(CrateTier v) { this.beginnerTier = v; return this; }
        public Builder intermediateTier(CrateTier v) { this.intermediateTier = v; return this; }
        public Builder advancedTier(CrateTier v) { this.advancedTier = v; return this; }
        public Builder actionDrops(List<CrateTierReward> v) { this.actionDrops = v; return this; }
        public Builder levelMilestones(List<CrateTierReward> v) { this.levelMilestones = v; return this; }
        public Builder contractRewards(List<CrateTierReward> v) { this.contractRewards = v; return this; }
        public Builder rankMilestones(List<CrateTierReward> v) { this.rankMilestones = v; return this; }

        public JobCrateTierProfile build() {
            return new JobCrateTierProfile(crateKeysEnabled, beginnerTier,
                intermediateTier, advancedTier, actionDrops,
                levelMilestones, contractRewards, rankMilestones);
        }
    }

    public record CrateTier(
        String tierId,
        String displayName,
        boolean enabled,
        String crateId,
        String keyType,
        boolean virtualKey
    ) {
        public static CrateTier unconfigured(String tierId, String displayName) {
            return new CrateTier(tierId, displayName, false, null, null, true);
        }

        public boolean isConfigured() {
            return crateId != null && keyType != null;
        }

        public boolean isAvailable() {
            return enabled && isConfigured();
        }
    }

    public record CrateTierReward(
        String tierId,
        int minimumJobLevel,
        int maximumJobLevel,
        double chance,
        boolean actionWeightMultiplier,
        int maxPerDay,
        long cooldownSeconds,
        int amount
    ) {
        public static CrateTierReward actionDrop(String tierId, int minLevel, int maxLevel,
                                                  double chance, int maxPerDay, long cooldownSeconds) {
            return new CrateTierReward(tierId, minLevel, maxLevel, chance, true, maxPerDay, cooldownSeconds, 1);
        }

        public static CrateTierReward milestone(int jobLevel, String tierId, int amount) {
            return new CrateTierReward(tierId, jobLevel, jobLevel, 1.0, false, 0, 0, amount);
        }
    }
}
