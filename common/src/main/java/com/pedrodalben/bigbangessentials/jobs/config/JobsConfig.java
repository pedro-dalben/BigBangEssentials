package com.pedrodalben.bigbangessentials.jobs.config;

import com.pedrodalben.bigbangessentials.jobs.rewards.CrateRewardDefinition;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotDefinition;
import com.pedrodalben.bigbangessentials.jobs.progression.RankMilestoneDefinition;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseObjective;

import java.util.*;

public class JobsConfig {

    private final Map<String, JobDefinition> professions;
    private final Map<String, JobSlotDefinition> slots;
    private final Map<String, RankMilestoneDefinition> milestones;
    private final GlobalConfig global;

    private JobsConfig(GlobalConfig global, Map<String, JobDefinition> professions,
                       Map<String, JobSlotDefinition> slots, Map<String, RankMilestoneDefinition> milestones) {
        this.global = Objects.requireNonNull(global, "global config");
        this.professions = Collections.unmodifiableMap(new LinkedHashMap<>(professions));
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        this.milestones = Collections.unmodifiableMap(new LinkedHashMap<>(milestones));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, JobDefinition> getProfessions() { return professions; }
    public Map<String, JobSlotDefinition> getSlots() { return slots; }
    public Map<String, RankMilestoneDefinition> getRankMilestones() { return milestones; }
    public GlobalConfig global() { return global; }

    public JobDefinition getJob(String id) {
        return id != null ? professions.get(id.toLowerCase()) : null;
    }

    public double getDailyLimitGlobal() { return global.dailyLimitGlobal; }
    public int getMaxActiveJobs() { return global.maxActiveJobs; }
    public String getDailyLimitTimezone() { return global.dailyLimitTimezone; }
    public String getDailyLimitResetTime() { return global.dailyLimitResetTime; }
    public int getMaxInProgressLicenses() { return global.maxInProgressLicenses; }
    public boolean isDailyLimitEnabled() { return global.dailyLimitEnabled; }
    public boolean isPreventEarningsWhileAfk() { return global.preventEarningsWhileAfk; }
    public boolean isPreventXpWhileAfk() { return global.preventXpWhileAfk; }
    public boolean isContinueXpAfterLimit() { return global.continueXpAfterLimit; }
    public int getSwitchCooldownMinutes() { return global.switchCooldownMinutes; }
    public String getPermissionPrefix() { return global.permissionPrefix; }
    public Map<String, String> getLegacyPermissionAliases() { return global.legacyPermissionAliases; }

    public static class Builder {
        private GlobalConfig global;
        private final Map<String, JobDefinition> professions = new LinkedHashMap<>();
        private final Map<String, JobSlotDefinition> slots = new LinkedHashMap<>();
        private final Map<String, RankMilestoneDefinition> milestones = new LinkedHashMap<>();

        public Builder global(GlobalConfig g) { this.global = g; return this; }
        public Builder addProfession(JobDefinition job) {
            professions.put(job.id.toLowerCase(), job);
            return this;
        }
        public Builder addSlot(JobSlotDefinition slot) {
            slots.put(slot.slotType(), slot);
            return this;
        }
        public Builder addAllSlots(Map<String, JobSlotDefinition> m) {
            slots.putAll(m);
            return this;
        }
        public Builder addMilestone(RankMilestoneDefinition milestone) {
            milestones.put(milestone.id(), milestone);
            return this;
        }
        public Builder addAllMilestones(Map<String, RankMilestoneDefinition> m) {
            milestones.putAll(m);
            return this;
        }
        public Builder addAllProfessions(Map<String, JobDefinition> m) {
            m.forEach((k, v) -> professions.put(k.toLowerCase(), v));
            return this;
        }
        public JobsConfig build() {
            if (global == null) throw new IllegalArgumentException("GlobalConfig is required");
            return new JobsConfig(global, professions, slots, milestones);
        }
    }

    public static class GlobalConfig {
        public final int schemaVersion;
        public final double dailyLimitGlobal;
        public final int maxActiveJobs;
        public final String dailyLimitTimezone;
        public final String dailyLimitResetTime;
        public final int maxInProgressLicenses;
        public final boolean dailyLimitEnabled;
        public final boolean preventEarningsWhileAfk;
        public final boolean preventXpWhileAfk;
        public final boolean continueXpAfterLimit;
        public final int switchCooldownMinutes;
        public final String permissionPrefix;
        public final Map<String, String> legacyPermissionAliases;

        private GlobalConfig(Builder b) {
            this.schemaVersion = b.schemaVersion;
            this.dailyLimitGlobal = b.dailyLimitGlobal;
            this.maxActiveJobs = b.maxActiveJobs;
            this.dailyLimitTimezone = b.dailyLimitTimezone;
            this.dailyLimitResetTime = b.dailyLimitResetTime;
            this.maxInProgressLicenses = b.maxInProgressLicenses;
            this.dailyLimitEnabled = b.dailyLimitEnabled;
            this.preventEarningsWhileAfk = b.preventEarningsWhileAfk;
            this.preventXpWhileAfk = b.preventXpWhileAfk;
            this.continueXpAfterLimit = b.continueXpAfterLimit;
            this.switchCooldownMinutes = b.switchCooldownMinutes;
            this.permissionPrefix = b.permissionPrefix;
            this.legacyPermissionAliases = Collections.unmodifiableMap(new LinkedHashMap<>(b.legacyPermissionAliases));
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            int schemaVersion = 2;
            double dailyLimitGlobal = 50000.0;
            int maxActiveJobs = 2;
            String dailyLimitTimezone = "America/Sao_Paulo";
            String dailyLimitResetTime = "00:00";
            int maxInProgressLicenses = 1;
            boolean dailyLimitEnabled = true;
            boolean preventEarningsWhileAfk = true;
            boolean preventXpWhileAfk = true;
            boolean continueXpAfterLimit = false;
            int switchCooldownMinutes = 30;
            String permissionPrefix = "bigbangessentials.jobs";
            final Map<String, String> legacyPermissionAliases = new LinkedHashMap<>();

            public Builder schemaVersion(int v) { this.schemaVersion = v; return this; }
            public Builder dailyLimitGlobal(double v) { this.dailyLimitGlobal = v; return this; }
            public Builder maxActiveJobs(int v) { this.maxActiveJobs = v; return this; }
            public Builder dailyLimitTimezone(String v) { this.dailyLimitTimezone = v; return this; }
            public Builder dailyLimitResetTime(String v) { this.dailyLimitResetTime = v; return this; }
            public Builder maxInProgressLicenses(int v) { this.maxInProgressLicenses = v; return this; }
            public Builder dailyLimitEnabled(boolean v) { this.dailyLimitEnabled = v; return this; }
            public Builder preventEarningsWhileAfk(boolean v) { this.preventEarningsWhileAfk = v; return this; }
            public Builder preventXpWhileAfk(boolean v) { this.preventXpWhileAfk = v; return this; }
            public Builder continueXpAfterLimit(boolean v) { this.continueXpAfterLimit = v; return this; }
            public Builder switchCooldownMinutes(int v) { this.switchCooldownMinutes = v; return this; }
            public Builder permissionPrefix(String v) { this.permissionPrefix = v; return this; }
            public Builder legacyPermissionAlias(String legacy, String canonical) {
                this.legacyPermissionAliases.put(legacy, canonical);
                return this;
            }
            public GlobalConfig build() { return new GlobalConfig(this); }
        }
    }

    public static class JobDefinition {
        public final String id;
        public final boolean enabled;
        public final String displayName;
        public final String shortDescription;
        public final String description;
        public final String icon;
        public final String category;
        public final int sortOrder;
        public final String permission;
        public final boolean visibleWithoutPermission;
        public final boolean unlockedByDefault;
        public final boolean licenseRequired;
        public final String requiredIntegration;
        public final int maxLevel;
        public final XpCurve xpCurve;
        public final double maxDailyEarnings;
        public final double moneyBonusPerLevel;
        public final double maxLevelMoneyBonus;
        public final int skillPointsEvery;
        public final boolean resetProgressOnLeave;
        public final List<JobLicenseObjective> licenseObjectives;
        public final Map<String, Map<String, ActionReward>> actions;
        public final Map<String, SkillDefinition> skills;
        public final Map<String, String> messages;
        public final Map<Integer, List<String>> levelUpRewards;
        public final HowToEarn howToEarn;
        public final List<CrateRewardDefinition> crateRewards;
        public final UnlockRequirements unlockRequirements;

        private JobDefinition(Builder b) {
            this.id = requireNonEmpty(b.id, "id");
            this.enabled = b.enabled;
            this.displayName = requireNonEmpty(b.displayName, "displayName");
            this.shortDescription = b.shortDescription != null ? b.shortDescription : "";
            this.description = b.description != null ? b.description : "";
            this.icon = b.icon != null ? b.icon : "minecraft:book";
            this.category = requireNonEmpty(b.category, "category");
            this.sortOrder = b.sortOrder;
            this.permission = b.permission != null ? b.permission : "bigbangessentials.jobs.profession." + b.id;
            this.visibleWithoutPermission = b.visibleWithoutPermission;
            this.unlockedByDefault = b.unlockedByDefault;
            this.licenseRequired = b.licenseRequired;
            this.requiredIntegration = b.requiredIntegration;
            this.maxLevel = b.maxLevel > 0 ? b.maxLevel : 100;
            this.xpCurve = b.xpCurve != null ? b.xpCurve : XpCurve.DEFAULT;
            this.maxDailyEarnings = b.maxDailyEarnings;
            this.moneyBonusPerLevel = b.moneyBonusPerLevel;
            this.maxLevelMoneyBonus = b.maxLevelMoneyBonus;
            this.skillPointsEvery = b.skillPointsEvery > 0 ? b.skillPointsEvery : 2;
            this.resetProgressOnLeave = b.resetProgressOnLeave;
            this.licenseObjectives = Collections.unmodifiableList(
                    b.licenseObjectives != null ? new ArrayList<>(b.licenseObjectives) : new ArrayList<>());
            this.actions = unmodifiableActions(b.actions);
            this.skills = Collections.unmodifiableMap(
                    b.skills != null ? new LinkedHashMap<>(b.skills) : new LinkedHashMap<>());
            this.messages = Collections.unmodifiableMap(
                    b.messages != null ? new LinkedHashMap<>(b.messages) : new LinkedHashMap<>());
            this.levelUpRewards = Collections.unmodifiableMap(
                    b.levelUpRewards != null ? new LinkedHashMap<>(b.levelUpRewards) : new LinkedHashMap<>());
            this.howToEarn = b.howToEarn != null ? b.howToEarn : HowToEarn.empty();
            this.crateRewards = Collections.unmodifiableList(
                    b.crateRewards != null ? new ArrayList<>(b.crateRewards) : new ArrayList<>());
            this.unlockRequirements = b.unlockRequirements != null ? b.unlockRequirements : UnlockRequirements.DEFAULT;
        }

        public static Builder builder(String id) { return new Builder(id); }

        public double getRequiredXp(int level) {
            return xpCurve.computeRequiredXp(level);
        }

        public ActionReward getReward(String configKey, String targetId) {
            Map<String, ActionReward> map = actions.get(configKey);
            if (map != null) return map.get(targetId);
            return null;
        }

        public ActionReward getWildcardReward(String configKey) {
            Map<String, ActionReward> map = actions.get(configKey);
            if (map != null) return map.get("*");
            return null;
        }

        private static String requireNonEmpty(String val, String field) {
            if (val == null || val.trim().isEmpty())
                throw new IllegalArgumentException(field + " cannot be null or empty");
            return val;
        }

        private static Map<String, Map<String, ActionReward>> unmodifiableActions(
                Map<String, Map<String, ActionReward>> actions) {
            if (actions == null) return Collections.emptyMap();
            Map<String, Map<String, ActionReward>> result = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, ActionReward>> entry : actions.entrySet()) {
                result.put(entry.getKey(),
                        Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
            }
            return Collections.unmodifiableMap(result);
        }

        public static class Builder {
            private final String id;
            private boolean enabled = true;
            private String displayName;
            private String shortDescription;
            private String description;
            private String icon;
            private String category;
            private int sortOrder;
            private String permission;
            private boolean visibleWithoutPermission = true;
            private boolean unlockedByDefault = true;
            private boolean licenseRequired = false;
            private String requiredIntegration;
            private int maxLevel = 100;
            private XpCurve xpCurve;
            private double maxDailyEarnings = -1;
            private double moneyBonusPerLevel = 0.5;
            private double maxLevelMoneyBonus = 50.0;
            private int skillPointsEvery = 2;
            private boolean resetProgressOnLeave = false;
            private List<JobLicenseObjective> licenseObjectives;
            private Map<String, Map<String, ActionReward>> actions;
            private Map<String, SkillDefinition> skills;
            private Map<String, String> messages;
            private Map<Integer, List<String>> levelUpRewards;
            private HowToEarn howToEarn;
            private List<CrateRewardDefinition> crateRewards;
            private UnlockRequirements unlockRequirements;

            private Builder(String id) { this.id = id; }

            public Builder enabled(boolean v) { enabled = v; return this; }
            public Builder displayName(String v) { displayName = v; return this; }
            public Builder shortDescription(String v) { shortDescription = v; return this; }
            public Builder description(String v) { description = v; return this; }
            public Builder icon(String v) { icon = v; return this; }
            public Builder category(String v) { category = v; return this; }
            public Builder sortOrder(int v) { sortOrder = v; return this; }
            public Builder permission(String v) { permission = v; return this; }
            public Builder visibleWithoutPermission(boolean v) { visibleWithoutPermission = v; return this; }
            public Builder unlockedByDefault(boolean v) { unlockedByDefault = v; return this; }
            public Builder licenseRequired(boolean v) { licenseRequired = v; return this; }
            public Builder requiredIntegration(String v) { requiredIntegration = v; return this; }
            public Builder maxLevel(int v) { maxLevel = v; return this; }
            public Builder xpCurve(XpCurve v) { xpCurve = v; return this; }
            public Builder maxDailyEarnings(double v) { maxDailyEarnings = v; return this; }
            public Builder moneyBonusPerLevel(double v) { moneyBonusPerLevel = v; return this; }
            public Builder maxLevelMoneyBonus(double v) { maxLevelMoneyBonus = v; return this; }
            public Builder skillPointsEvery(int v) { skillPointsEvery = v; return this; }
            public Builder resetProgressOnLeave(boolean v) { resetProgressOnLeave = v; return this; }
            public Builder licenseObjectives(List<JobLicenseObjective> v) { licenseObjectives = v; return this; }
            public Builder actions(Map<String, Map<String, ActionReward>> v) { actions = v; return this; }
            public Builder skills(Map<String, SkillDefinition> v) { skills = v; return this; }
            public Builder messages(Map<String, String> v) { messages = v; return this; }
            public Builder levelUpRewards(Map<Integer, List<String>> v) { levelUpRewards = v; return this; }
            public Builder howToEarn(HowToEarn v) { howToEarn = v; return this; }
            public Builder crateRewards(List<CrateRewardDefinition> v) { crateRewards = v; return this; }
            public Builder unlockRequirements(UnlockRequirements v) { unlockRequirements = v; return this; }
            public JobDefinition build() { return new JobDefinition(this); }
        }
    }

    public static class XpCurve {
        public static final XpCurve DEFAULT = new XpCurve("polynomial", 100.0, 1.0, 1.5);

        public final String type;
        public final double base;
        public final double multiplier;
        public final double exponent;

        public XpCurve(String type, double base, double multiplier, double exponent) {
            this.type = type != null ? type : "polynomial";
            this.base = base;
            this.multiplier = multiplier;
            this.exponent = exponent;
        }

        public double computeRequiredXp(int level) {
            if (level < 1) return base;
            if ("linear".equals(type)) {
                return base + multiplier * (level - 1);
            }
            return base * Math.pow(level, exponent) * multiplier;
        }
    }

    public static class HowToEarn {
        public final String moneyHeader;
        public final String xpHeader;
        public final List<String> moneyLines;
        public final List<String> xpLines;
        public final List<String> exampleTargets;

        public HowToEarn(String moneyHeader, String xpHeader,
                         List<String> moneyLines, List<String> xpLines,
                         List<String> exampleTargets) {
            this.moneyHeader = moneyHeader != null ? moneyHeader : "Como ganhar dinheiro";
            this.xpHeader = xpHeader != null ? xpHeader : "Como ganhar XP";
            this.moneyLines = moneyLines != null ? Collections.unmodifiableList(new ArrayList<>(moneyLines))
                    : Collections.emptyList();
            this.xpLines = xpLines != null ? Collections.unmodifiableList(new ArrayList<>(xpLines))
                    : Collections.emptyList();
            this.exampleTargets = exampleTargets != null ? Collections.unmodifiableList(new ArrayList<>(exampleTargets))
                    : Collections.emptyList();
        }

        public static HowToEarn empty() {
            return new HowToEarn(null, null, null, null, null);
        }
    }

    public static class ActionReward {
        public final double money;
        public final double xp;
        public final double chance;

        public ActionReward(double money, double xp) {
            this(money, xp, 1.0);
        }

        public ActionReward(double money, double xp, double chance) {
            this.money = money;
            this.xp = xp;
            this.chance = Math.max(0.0, Math.min(1.0, chance <= 0.0 ? 1.0 : chance));
        }
    }

    public static class SkillDefinition {
        public final String id;
        public final String name;
        public final String description;
        public final int maxLevel;
        public final int maxRank;
        public final int requiredLevel;
        public final int pointCost;
        public final List<String> dependencies;
        public final List<String> prerequisites;
        public final Map<String, Double> effects;

        public SkillDefinition(String id, String name, String description, int maxLevel, int maxRank,
                               int pointCost, List<String> prerequisites, Map<String, Double> effects) {
            this(id, name, description, maxLevel, maxRank, pointCost, prerequisites, effects, 1);
        }

        public SkillDefinition(String id, String name, String description, int maxLevel, int maxRank,
                               int pointCost, List<String> prerequisites, Map<String, Double> effects,
                               int requiredLevel) {
            this.id = id;
            this.name = name != null ? name : id;
            this.description = description != null ? description : "";
            this.maxLevel = maxLevel;
            this.maxRank = maxRank;
            this.requiredLevel = requiredLevel > 0 ? requiredLevel : 1;
            this.pointCost = pointCost;
            this.dependencies = Collections.unmodifiableList(new ArrayList<>());
            this.prerequisites = Collections.unmodifiableList(
                    new ArrayList<>(prerequisites != null ? prerequisites : Collections.emptyList()));
            this.effects = Collections.unmodifiableMap(
                    new LinkedHashMap<>(effects != null ? effects : Collections.emptyMap()));
        }

        public static Builder builder(String id) { return new Builder(id); }

        public static class Builder {
            private final String id;
            private String name;
            private String description;
            private int maxLevel = 5;
            private int maxRank = 1;
            private int requiredLevel = 1;
            private int pointCost = 1;
            private List<String> dependencies = new ArrayList<>();
            private List<String> prerequisites = new ArrayList<>();
            private Map<String, Double> effects = new LinkedHashMap<>();

            private Builder(String id) { this.id = id; }
            public Builder name(String v) { name = v; return this; }
            public Builder description(String v) { description = v; return this; }
            public Builder maxLevel(int v) { maxLevel = v; return this; }
            public Builder maxRank(int v) { maxRank = v; return this; }
            public Builder requiredLevel(int v) { requiredLevel = v; return this; }
            public Builder pointCost(int v) { pointCost = v; return this; }
            public Builder prerequisites(List<String> v) { prerequisites = v; return this; }
            public Builder effects(Map<String, Double> v) { effects = v; return this; }
            public SkillDefinition build() {
                return new SkillDefinition(id, name, description, maxLevel, maxRank,
                        pointCost, prerequisites, effects, requiredLevel);
            }
        }
    }
}
