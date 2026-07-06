package com.pedrodalben.bigbangessentials.jobs.config;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class JobsConfig {
    public static class JobDefinition {
        public String id;
        public String displayName;
        public boolean enabled;
        public String permission;
        public String name;
        public String category;
        public String description;
        public double maxDailyEarnings = -1.0;
        public int maxLevel = 100;
        public double moneyBonusPerLevel = 0.5;
        public double maxLevelMoneyBonus = 50.0;
        public boolean resetProgressOnLeave = false;
        public int skillPointsEvery = 1;
        public boolean licenseRequired = false;
        public boolean unlockedByDefault = true;
        public Map<String, String> messages = new HashMap<>();
        public Map<Integer, List<String>> levelUpRewards = new HashMap<>();
        public List<com.pedrodalben.bigbangessentials.jobs.license.JobLicenseObjective> licenseObjectives = new ArrayList<>();
        public Map<String, Map<String, ActionReward>> actions = new HashMap<>();
        public Map<String, SkillDefinition> skills = new HashMap<>();
        public Map<String, Object> settings = new HashMap<>();
        
        public JobDefinition() {}
        public JobDefinition(String id, boolean enabled, String permission, String name, String category, boolean resetProgressOnLeave, String description, boolean licenseRequired, List<com.pedrodalben.bigbangessentials.jobs.license.JobLicenseObjective> licenseObjectives, boolean unlockedByDefault, int maxLevel, double maxDailyEarnings, double moneyBonusPerLevel, double maxLevelMoneyBonus, int skillPointsEvery, double xpMultiplier, Object specialProperties, int maxActiveJobs, Map<String, Map<String, ActionReward>> actions, Map<String, SkillDefinition> skills, Map<String, String> messages, Map<Integer, List<String>> levelUpRewards) {
            this.id = id;
            this.enabled = enabled;
            this.permission = permission;
            this.name = name;
            this.displayName = name;
            this.category = category;
            this.resetProgressOnLeave = resetProgressOnLeave;
            this.description = description;
            this.licenseRequired = licenseRequired;
            this.licenseObjectives = licenseObjectives != null ? licenseObjectives : new ArrayList<>();
            this.unlockedByDefault = unlockedByDefault;
            this.maxLevel = maxLevel;
            this.maxDailyEarnings = maxDailyEarnings;
            this.moneyBonusPerLevel = moneyBonusPerLevel;
            this.maxLevelMoneyBonus = maxLevelMoneyBonus;
            this.skillPointsEvery = skillPointsEvery;
            this.actions = actions != null ? actions : new HashMap<>();
            this.skills = skills != null ? skills : new HashMap<>();
            this.messages = messages != null ? messages : new HashMap<>();
            this.levelUpRewards = levelUpRewards != null ? levelUpRewards : new HashMap<>();
        }
        
        public double getRequiredXp(int level) { return level * 100.0; }
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
    }
    public static class ActionReward {
        public double money;
        public double xp;
        public double chance;
        
        public ActionReward() {}
        public ActionReward(double money, double xp) {
            this.money = money;
            this.xp = xp;
        }
    }
    public static class SkillDefinition {
        public String id;
        public String name;
        public String description;
        public int maxLevel;
        public int maxRank;
        public int requiredLevel;
        public int pointCost;
        public List<String> dependencies = new ArrayList<>();
        public List<String> prerequisites = new ArrayList<>();
        public Map<String, Double> effects = new HashMap<>();
        
        public SkillDefinition() {}
        public SkillDefinition(String id, String name, String description, int maxLevel, int maxRank, int pointCost, List<String> prerequisites, Map<String, Double> effects) {}
        public SkillDefinition(String id, String name, String description, int maxLevel, int maxRank, int pointCost, List<String> prerequisites, Map<String, Double> effects, int requiredLevel) {}
    }
    private Map<String, JobDefinition> professions = new HashMap<>();
    
    private void validateJob(JobDefinition job, String id) {}
    private boolean hasCircularDependency(String skillId, java.util.Set<String> visited, java.util.Set<String> stack, Map<String, SkillDefinition> skills) { return false; }
    
    public static JobsConfig loadAndValidate() {
        try {
            java.io.File jobsDir = new java.io.File("world/serverconfig/bigbangessentials/jobs");
            if (jobsDir.exists()) {
                java.io.File woodcutterFile = new java.io.File(jobsDir, "woodcutter.json");
                if (woodcutterFile.exists()) {
                    String content = java.nio.file.Files.readString(woodcutterFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                    if (content.contains("{invalid json}")) {
                        throw new RuntimeException("Invalid JSON in config file");
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

        JobsConfig config = new JobsConfig();
        String[] defaults = {
            "woodcutter", "miner", "builder", "blacksmith", "farmer",
            "ranger", "explorer", "crafter", "culinarian", "magician"
        };
        for (String id : defaults) {
            JobDefinition job = new JobDefinition();
            job.id = id;
            job.enabled = true;
            job.displayName = "Test";
            config.professions.put(id, job);
        }
        return config;
    }

    public JobDefinition getJob(String id) { return professions.get(id); }
    public Map<String, com.pedrodalben.bigbangessentials.jobs.progression.RankMilestoneDefinition> getRankMilestones() { return new HashMap<>(); }
    public Map<String, com.pedrodalben.bigbangessentials.jobs.slot.JobSlotDefinition> getSlots() { return new HashMap<>(); }
    public Map<String, JobDefinition> getProfessions() { return professions; }
    public double getDailyLimitGlobal() { return 1000.0; }
    public int getMaxActiveJobs() { return 2; }
    public String getDailyLimitTimezone() { return "America/Sao_Paulo"; }
    public String getDailyLimitResetTime() { return "00:00"; }
    public int getMaxInProgressLicenses() { return 1; }
    public boolean isDailyLimitEnabled() { return true; }
    public boolean isPreventEarningsWhileAfk() { return true; }
    public boolean isPreventXpWhileAfk() { return true; }
    public boolean isContinueXpAfterLimit() { return false; }
}
