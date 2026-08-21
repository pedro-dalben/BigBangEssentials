package com.pedrodalben.bigbangessentials.rankup.domain;

public record RankupLuckPermsSettings(String group, boolean setAsPrimaryGroup, RankupPromotionMode mode) {
    public RankupLuckPermsSettings {
        if (group == null) group = "";
        if (mode == null) mode = RankupPromotionMode.REPLACE_LADDER_INHERITANCE_AND_PRIMARY;
    }

    public RankupLuckPermsSettings(String group, boolean setAsPrimaryGroup) {
        this(group, setAsPrimaryGroup, RankupPromotionMode.REPLACE_LADDER_INHERITANCE_AND_PRIMARY);
    }

    public RankupLuckPermsSettings withGroup(String group) {
        return new RankupLuckPermsSettings(group, setAsPrimaryGroup, mode);
    }

    public RankupLuckPermsSettings withSetAsPrimaryGroup(boolean setAsPrimaryGroup) {
        return new RankupLuckPermsSettings(group, setAsPrimaryGroup, mode);
    }

    public RankupLuckPermsSettings withMode(RankupPromotionMode mode) {
        return new RankupLuckPermsSettings(group, setAsPrimaryGroup, mode);
    }
}
