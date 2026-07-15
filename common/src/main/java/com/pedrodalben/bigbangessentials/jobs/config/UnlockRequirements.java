package com.pedrodalben.bigbangessentials.jobs.config;

public record UnlockRequirements(
        boolean unlockedByDefault,
        String requiredRankId,
        int requiredRankOrder,
        String permission
) {
    public static final UnlockRequirements DEFAULT = new UnlockRequirements(true, null, 0, null);

    public UnlockRequirements {
        if (requiredRankId != null && requiredRankId.isBlank()) {
            requiredRankId = null;
        }
        if (permission != null && permission.isBlank()) {
            permission = null;
        }
        requiredRankOrder = Math.max(0, requiredRankOrder);
    }

    public boolean hasRankRequirement() {
        return requiredRankId != null || requiredRankOrder > 0;
    }

    public boolean hasPermissionRequirement() {
        return permission != null;
    }
}
