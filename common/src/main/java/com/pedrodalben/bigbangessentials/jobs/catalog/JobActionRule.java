package com.pedrodalben.bigbangessentials.jobs.catalog;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record JobActionRule(
    String ruleId,
    JobActionType actionType,
    boolean enabled,
    List<String> targetIds,
    List<String> targetTags,
    List<String> recipeIds,
    List<String> recipeTags,
    List<String> itemIds,
    List<String> itemTags,
    List<String> blockIds,
    List<String> blockTags,
    List<String> entities,
    List<String> species,
    List<String> forms,
    List<String> biomes,
    List<String> structures,
    List<String> dimensions,
    List<String> tiers,
    List<String> stations,
    List<String> tools,
    boolean requireNaturalOrigin,
    boolean requireNoCommandOrigin,
    String maturityState,
    double actionWeight,
    long cooldownMilliseconds,
    int dailyLimit,
    String exclusivityGroup,
    double baseCoins,
    double baseXp,
    double coinMultiplierPerLevel,
    double xpMultiplierPerLevel,
    int baseFragments,
    String crateKeyId,
    double keyChance,
    double keyWeight,
    int keyMaxPerDay,
    long keyCooldownMilliseconds,
    Map<String, Double> levelMultipliers,
    List<String> additionalChecks
) {
    public static Builder builder(JobActionType actionType) {
        return new Builder(actionType);
    }

    public static class Builder {
        private final JobActionType actionType;
        private String ruleId = java.util.UUID.randomUUID().toString().substring(0, 8);
        private boolean enabled = true;
        private List<String> targetIds = Collections.emptyList();
        private List<String> targetTags = Collections.emptyList();
        private List<String> recipeIds = Collections.emptyList();
        private List<String> recipeTags = Collections.emptyList();
        private List<String> itemIds = Collections.emptyList();
        private List<String> itemTags = Collections.emptyList();
        private List<String> blockIds = Collections.emptyList();
        private List<String> blockTags = Collections.emptyList();
        private List<String> entities = Collections.emptyList();
        private List<String> species = Collections.emptyList();
        private List<String> forms = Collections.emptyList();
        private List<String> biomes = Collections.emptyList();
        private List<String> structures = Collections.emptyList();
        private List<String> dimensions = Collections.emptyList();
        private List<String> tiers = Collections.emptyList();
        private List<String> stations = Collections.emptyList();
        private List<String> tools = Collections.emptyList();
        private boolean requireNaturalOrigin = false;
        private boolean requireNoCommandOrigin = false;
        private String maturityState = null;
        private double actionWeight = 1.0;
        private long cooldownMilliseconds = 0;
        private int dailyLimit = 0;
        private String exclusivityGroup = null;
        private double baseCoins = 0.0;
        private double baseXp = 0.0;
        private double coinMultiplierPerLevel = 0.01;
        private double xpMultiplierPerLevel = 0.01;
        private int baseFragments = 0;
        private String crateKeyId = null;
        private double keyChance = 0.0;
        private double keyWeight = 1.0;
        private int keyMaxPerDay = 0;
        private long keyCooldownMilliseconds = 0;
        private Map<String, Double> levelMultipliers = Collections.emptyMap();
        private List<String> additionalChecks = Collections.emptyList();

        private Builder(JobActionType actionType) { this.actionType = actionType; }

        public Builder ruleId(String v) { this.ruleId = v; return this; }
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder targetIds(List<String> v) { this.targetIds = v; return this; }
        public Builder targetTags(List<String> v) { this.targetTags = v; return this; }
        public Builder recipeIds(List<String> v) { this.recipeIds = v; return this; }
        public Builder recipeTags(List<String> v) { this.recipeTags = v; return this; }
        public Builder itemIds(List<String> v) { this.itemIds = v; return this; }
        public Builder itemTags(List<String> v) { this.itemTags = v; return this; }
        public Builder blockIds(List<String> v) { this.blockIds = v; return this; }
        public Builder blockTags(List<String> v) { this.blockTags = v; return this; }
        public Builder entities(List<String> v) { this.entities = v; return this; }
        public Builder species(List<String> v) { this.species = v; return this; }
        public Builder forms(List<String> v) { this.forms = v; return this; }
        public Builder biomes(List<String> v) { this.biomes = v; return this; }
        public Builder structures(List<String> v) { this.structures = v; return this; }
        public Builder dimensions(List<String> v) { this.dimensions = v; return this; }
        public Builder tiers(List<String> v) { this.tiers = v; return this; }
        public Builder stations(List<String> v) { this.stations = v; return this; }
        public Builder tools(List<String> v) { this.tools = v; return this; }
        public Builder requireNaturalOrigin(boolean v) { this.requireNaturalOrigin = v; return this; }
        public Builder requireNoCommandOrigin(boolean v) { this.requireNoCommandOrigin = v; return this; }
        public Builder maturityState(String v) { this.maturityState = v; return this; }
        public Builder actionWeight(double v) { this.actionWeight = v; return this; }
        public Builder cooldownMilliseconds(long v) { this.cooldownMilliseconds = v; return this; }
        public Builder dailyLimit(int v) { this.dailyLimit = v; return this; }
        public Builder exclusivityGroup(String v) { this.exclusivityGroup = v; return this; }
        public Builder baseCoins(double v) { this.baseCoins = v; return this; }
        public Builder baseXp(double v) { this.baseXp = v; return this; }
        public Builder coinMultiplierPerLevel(double v) { this.coinMultiplierPerLevel = v; return this; }
        public Builder xpMultiplierPerLevel(double v) { this.xpMultiplierPerLevel = v; return this; }
        public Builder baseFragments(int v) { this.baseFragments = v; return this; }
        public Builder crateKeyId(String v) { this.crateKeyId = v; return this; }
        public Builder keyChance(double v) { this.keyChance = v; return this; }
        public Builder keyWeight(double v) { this.keyWeight = v; return this; }
        public Builder keyMaxPerDay(int v) { this.keyMaxPerDay = v; return this; }
        public Builder keyCooldownMilliseconds(long v) { this.keyCooldownMilliseconds = v; return this; }
        public Builder levelMultipliers(Map<String, Double> v) { this.levelMultipliers = v; return this; }
        public Builder additionalChecks(List<String> v) { this.additionalChecks = v; return this; }

        public JobActionRule build() {
            return new JobActionRule(ruleId, actionType, enabled, targetIds, targetTags,
                recipeIds, recipeTags, itemIds, itemTags, blockIds, blockTags,
                entities, species, forms, biomes, structures, dimensions, tiers,
                stations, tools, requireNaturalOrigin, requireNoCommandOrigin, maturityState,
                actionWeight, cooldownMilliseconds, dailyLimit, exclusivityGroup,
                baseCoins, baseXp, coinMultiplierPerLevel, xpMultiplierPerLevel,
                baseFragments, crateKeyId, keyChance, keyWeight, keyMaxPerDay,
                keyCooldownMilliseconds, levelMultipliers, additionalChecks);
        }
    }
}
