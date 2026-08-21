package com.pedrodalben.bigbangessentials.jobs.rewards;

public class JobKeyDropRule {
    private final double baseChance;
    private final int maxKeysPerJobPerDay;
    private final int maxKeysTotalPerDay;
    private final long cooldownSeconds;
    private final double levelMultiplierPerLevel;

    public JobKeyDropRule(double baseChance, int maxKeysPerJobPerDay, int maxKeysTotalPerDay, long cooldownSeconds, double levelMultiplierPerLevel) {
        this.baseChance = baseChance;
        this.maxKeysPerJobPerDay = maxKeysPerJobPerDay;
        this.maxKeysTotalPerDay = maxKeysTotalPerDay;
        this.cooldownSeconds = cooldownSeconds;
        this.levelMultiplierPerLevel = levelMultiplierPerLevel;
    }

    public static JobKeyDropRule defaultConfig() {
        return new JobKeyDropRule(0.005, 3, 5, 1800L, 0.0002);
    }

    public double getBaseChance() { return baseChance; }
    public int getMaxKeysPerJobPerDay() { return maxKeysPerJobPerDay; }
    public int getMaxKeysTotalPerDay() { return maxKeysTotalPerDay; }
    public long getCooldownSeconds() { return cooldownSeconds; }
    public double getLevelMultiplierPerLevel() { return levelMultiplierPerLevel; }

    public double calculateChance(int jobLevel) {
        return baseChance + (jobLevel * levelMultiplierPerLevel);
    }
}
