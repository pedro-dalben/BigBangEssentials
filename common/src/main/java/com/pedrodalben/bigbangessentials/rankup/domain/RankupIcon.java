package com.pedrodalben.bigbangessentials.rankup.domain;

public record RankupIcon(String item, int customModelData) {
    public RankupIcon {
        if (item == null || item.isBlank()) item = "minecraft:paper";
    }

    public RankupIcon(String item) {
        this(item, 0);
    }

    public RankupIcon withItem(String item) {
        return new RankupIcon(item, customModelData);
    }

    public RankupIcon withCustomModelData(int customModelData) {
        return new RankupIcon(item, customModelData);
    }
}
