package com.pedrodalben.bigbangessentials.jobs.catalog;

import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record JobRewardProfile(
    double baseCoins,
    double baseXp,
    double coinMultiplierPerLevel,
    double xpMultiplierPerLevel,
    int baseFragments,
    int fragmentMilestoneInterval,
    int fragmentMilestoneBonus,
    List<String> directItemIds,
    List<Integer> directItemAmounts,
    String crateKeyId,
    double keyChance,
    double keyWeight,
    int keyMaxPerDay,
    long keyCooldownMilliseconds,
    CrateKeyGrantSource keyGrantSource,
    boolean awardPendingOnFullInventory,
    Map<Integer, JobLevelReward> levelUpRewards
) {
    public static final JobRewardProfile DEFAULT = new JobRewardProfile(
        0.0, 0.0, 0.01, 0.01, 0, 10, 5,
        Collections.emptyList(), Collections.emptyList(),
        null, 0.0, 1.0, 0, 0,
        CrateKeyGrantSource.JOB_LUCK,
        true, Collections.emptyMap()
    );

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private double baseCoins = 0.0;
        private double baseXp = 0.0;
        private double coinMultiplierPerLevel = 0.01;
        private double xpMultiplierPerLevel = 0.01;
        private int baseFragments = 0;
        private int fragmentMilestoneInterval = 10;
        private int fragmentMilestoneBonus = 5;
        private List<String> directItemIds = Collections.emptyList();
        private List<Integer> directItemAmounts = Collections.emptyList();
        private String crateKeyId = null;
        private double keyChance = 0.0;
        private double keyWeight = 1.0;
        private int keyMaxPerDay = 0;
        private long keyCooldownMilliseconds = 0;
        private CrateKeyGrantSource keyGrantSource = CrateKeyGrantSource.JOB_LUCK;
        private boolean awardPendingOnFullInventory = true;
        private Map<Integer, JobLevelReward> levelUpRewards = Collections.emptyMap();

        public Builder baseCoins(double v) { this.baseCoins = v; return this; }
        public Builder baseXp(double v) { this.baseXp = v; return this; }
        public Builder coinMultiplierPerLevel(double v) { this.coinMultiplierPerLevel = v; return this; }
        public Builder xpMultiplierPerLevel(double v) { this.xpMultiplierPerLevel = v; return this; }
        public Builder baseFragments(int v) { this.baseFragments = v; return this; }
        public Builder fragmentMilestoneInterval(int v) { this.fragmentMilestoneInterval = v; return this; }
        public Builder fragmentMilestoneBonus(int v) { this.fragmentMilestoneBonus = v; return this; }
        public Builder directItemIds(List<String> v) { this.directItemIds = v; return this; }
        public Builder directItemAmounts(List<Integer> v) { this.directItemAmounts = v; return this; }
        public Builder crateKeyId(String v) { this.crateKeyId = v; return this; }
        public Builder keyChance(double v) { this.keyChance = v; return this; }
        public Builder keyWeight(double v) { this.keyWeight = v; return this; }
        public Builder keyMaxPerDay(int v) { this.keyMaxPerDay = v; return this; }
        public Builder keyCooldownMilliseconds(long v) { this.keyCooldownMilliseconds = v; return this; }
        public Builder keyGrantSource(CrateKeyGrantSource v) { this.keyGrantSource = v; return this; }
        public Builder awardPendingOnFullInventory(boolean v) { this.awardPendingOnFullInventory = v; return this; }
        public Builder levelUpRewards(Map<Integer, JobLevelReward> v) { this.levelUpRewards = v; return this; }

        public JobRewardProfile build() {
            return new JobRewardProfile(baseCoins, baseXp, coinMultiplierPerLevel,
                xpMultiplierPerLevel, baseFragments, fragmentMilestoneInterval,
                fragmentMilestoneBonus, directItemIds, directItemAmounts,
                crateKeyId, keyChance, keyWeight, keyMaxPerDay, keyCooldownMilliseconds,
                keyGrantSource, awardPendingOnFullInventory, levelUpRewards);
        }
    }

    public record JobLevelReward(int coins, int xp, int fragments, String keyId, int keyAmount) {
        public static final JobLevelReward NONE = new JobLevelReward(0, 0, 0, null, 0);
    }
}
