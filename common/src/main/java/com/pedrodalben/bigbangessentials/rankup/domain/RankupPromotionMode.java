package com.pedrodalben.bigbangessentials.rankup.domain;

public enum RankupPromotionMode {
    SET_PRIMARY_GROUP,
    REPLACE_LADDER_INHERITANCE_AND_PRIMARY;

    public static RankupPromotionMode fromString(String raw) {
        if (raw == null || raw.isBlank()) return REPLACE_LADDER_INHERITANCE_AND_PRIMARY;
        return switch (raw.toUpperCase().replace('-', '_').replace(' ', '_')) {
            case "SET_PRIMARY_GROUP" -> SET_PRIMARY_GROUP;
            default -> REPLACE_LADDER_INHERITANCE_AND_PRIMARY;
        };
    }
}
