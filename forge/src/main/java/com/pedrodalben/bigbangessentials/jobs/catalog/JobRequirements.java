package com.pedrodalben.bigbangessentials.jobs.catalog;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public record JobRequirements(
    int requiredRankOrder,
    String requiredRankId,
    boolean licenseRequired,
    List<String> licenseObjectiveIds,
    String slotType,
    String permissionNode,
    PermissionMode permissionMode,
    String requiredIntegration,
    boolean unlockedByDefault,
    int maxLevel,
    double maxDailyEarnings,
    double moneyBonusPerLevel,
    double maxLevelMoneyBonus,
    int skillPointsEvery,
    boolean resetProgressOnLeave,
    Map<JobActionType, List<JobActionRule>> actionRules,
    Map<String, String> displayMessages
) {
    public enum PermissionMode {
        NONE,
        ALL_REQUIREMENTS,
        RANK_OR_PERMISSION,
        RANK_AND_PERMISSION;

        public static PermissionMode fromString(String str) {
            if (str == null) return ALL_REQUIREMENTS;
            try {
                return valueOf(str.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ALL_REQUIREMENTS;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int requiredRankOrder = 0;
        private String requiredRankId = null;
        private boolean licenseRequired = false;
        private List<String> licenseObjectiveIds = Collections.emptyList();
        private String slotType = JobSlotType.COMMON_PRIMARY;
        private String permissionNode = null;
        private PermissionMode permissionMode = PermissionMode.ALL_REQUIREMENTS;
        private String requiredIntegration = null;
        private boolean unlockedByDefault = true;
        private int maxLevel = 100;
        private double maxDailyEarnings = -1.0;
        private double moneyBonusPerLevel = 0.5;
        private double maxLevelMoneyBonus = 50.0;
        private int skillPointsEvery = 2;
        private boolean resetProgressOnLeave = false;
        private Map<JobActionType, List<JobActionRule>> actionRules = new HashMap<>();
        private Map<String, String> displayMessages = new HashMap<>();

        public Builder requiredRankOrder(int v) { this.requiredRankOrder = v; return this; }
        public Builder requiredRankId(String v) { this.requiredRankId = v; return this; }
        public Builder licenseRequired(boolean v) { this.licenseRequired = v; return this; }
        public Builder licenseObjectiveIds(List<String> v) { this.licenseObjectiveIds = v; return this; }
        public Builder slotType(String v) { this.slotType = v; return this; }
        public Builder permissionNode(String v) { this.permissionNode = v; return this; }
        public Builder permissionMode(PermissionMode v) { this.permissionMode = v; return this; }
        public Builder requiredIntegration(String v) { this.requiredIntegration = v; return this; }
        public Builder unlockedByDefault(boolean v) { this.unlockedByDefault = v; return this; }
        public Builder maxLevel(int v) { this.maxLevel = v; return this; }
        public Builder maxDailyEarnings(double v) { this.maxDailyEarnings = v; return this; }
        public Builder moneyBonusPerLevel(double v) { this.moneyBonusPerLevel = v; return this; }
        public Builder maxLevelMoneyBonus(double v) { this.maxLevelMoneyBonus = v; return this; }
        public Builder skillPointsEvery(int v) { this.skillPointsEvery = v; return this; }
        public Builder resetProgressOnLeave(boolean v) { this.resetProgressOnLeave = v; return this; }
        public Builder actionRules(Map<JobActionType, List<JobActionRule>> v) { this.actionRules = v; return this; }
        public Builder displayMessages(Map<String, String> v) { this.displayMessages = v; return this; }

        public JobRequirements build() {
            return new JobRequirements(requiredRankOrder, requiredRankId, licenseRequired,
                licenseObjectiveIds, slotType, permissionNode, permissionMode, requiredIntegration,
                unlockedByDefault, maxLevel, maxDailyEarnings, moneyBonusPerLevel, maxLevelMoneyBonus,
                skillPointsEvery, resetProgressOnLeave, actionRules, displayMessages);
        }
    }
}
