package com.pedrodalben.bigbangessentials.rankup.domain;

public enum RankupTaskMode {
    ALL,
    ANY;

    public static RankupTaskMode fromString(String raw) {
        if (raw == null || raw.isBlank()) return ALL;
        return switch (raw.toUpperCase()) {
            case "ANY" -> ANY;
            default -> ALL;
        };
    }
}
