package com.pedrodalben.bigbangessentials.rankup.domain;

public class RankupLadder {
    private final String id;
    private final String displayName;
    private final String initialRankId;
    private final RankupPromotionMode luckPermsMode;
    private final boolean requireConfirmation;

    public RankupLadder(String id, String displayName, String initialRankId,
                        RankupPromotionMode luckPermsMode, boolean requireConfirmation) {
        this.id = id != null ? id.toLowerCase() : "main";
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : "&6Progression";
        this.initialRankId = initialRankId != null ? initialRankId.toLowerCase() : "";
        this.luckPermsMode = luckPermsMode != null ? luckPermsMode : RankupPromotionMode.REPLACE_LADDER_INHERITANCE_AND_PRIMARY;
        this.requireConfirmation = requireConfirmation;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String initialRankId() { return initialRankId; }
    public RankupPromotionMode luckPermsMode() { return luckPermsMode; }
    public boolean requireConfirmation() { return requireConfirmation; }

    public RankupLadder withId(String id) {
        return new RankupLadder(id, displayName, initialRankId, luckPermsMode, requireConfirmation);
    }

    public RankupLadder withDisplayName(String displayName) {
        return new RankupLadder(id, displayName, initialRankId, luckPermsMode, requireConfirmation);
    }

    public RankupLadder withInitialRankId(String initialRankId) {
        return new RankupLadder(id, displayName, initialRankId, luckPermsMode, requireConfirmation);
    }

    public RankupLadder withLuckPermsMode(RankupPromotionMode luckPermsMode) {
        return new RankupLadder(id, displayName, initialRankId, luckPermsMode, requireConfirmation);
    }

    public RankupLadder withRequireConfirmation(boolean requireConfirmation) {
        return new RankupLadder(id, displayName, initialRankId, luckPermsMode, requireConfirmation);
    }
}
