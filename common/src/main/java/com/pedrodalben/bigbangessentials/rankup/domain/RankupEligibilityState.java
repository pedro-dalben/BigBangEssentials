package com.pedrodalben.bigbangessentials.rankup.domain;

/**
 * Explicit states representing a player's eligibility status for promotion.
 */
public enum RankupEligibilityState {
    NO_CONFIGURATION("§cNo configuration active"),
    CONFIGURATION_ERROR("§cConfiguration error"),
    INTEGRATION_ERROR("§cLuckPerms or dependency unavailable"),
    LOADING("§eLoading progress from database..."),
    NO_CURRENT_RANK("§eNo current rank assigned"),
    MAX_RANK("§aHighest rank reached"),
    PROMOTION_IN_PROGRESS("§ePromotion currently in progress..."),
    BLOCKED_BY_TASKS("§cIncomplete tasks required"),
    BLOCKED_BY_MONEY("§cInsufficient money required"),
    BLOCKED_BY_GEMS("§cInsufficient gems required"),
    BLOCKED_BY_MULTIPLE_REQUIREMENTS("§cMultiple requirements incomplete"),
    READY("§aReady for promotion!");

    private final String defaultStatusText;

    RankupEligibilityState(String defaultStatusText) {
        this.defaultStatusText = defaultStatusText;
    }

    public String defaultStatusText() {
        return defaultStatusText;
    }

    public boolean isReady() {
        return this == READY;
    }

    public boolean isBlockedByRequirements() {
        return this == BLOCKED_BY_TASKS || this == BLOCKED_BY_MONEY
                || this == BLOCKED_BY_GEMS || this == BLOCKED_BY_MULTIPLE_REQUIREMENTS;
    }
}
