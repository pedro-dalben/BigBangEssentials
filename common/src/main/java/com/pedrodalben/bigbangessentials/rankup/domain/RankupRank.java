package com.pedrodalben.bigbangessentials.rankup.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RankupRank {
    private final String id;
    private final int order;
    private final String displayName;
    private final List<String> description;
    private final RankupIcon icon;
    private final RankupLuckPermsSettings luckPerms;
    private final RankupRequirements requirements;
    private final RankupActions actions;
    private final boolean enabled;

    public RankupRank(String id, int order, String displayName, List<String> description,
                      RankupIcon icon, RankupLuckPermsSettings luckPerms,
                      RankupRequirements requirements, RankupActions actions, boolean enabled) {
        this.id = id != null ? id.toLowerCase() : UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        this.order = order;
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : "&7" + this.id;
        this.description = description != null ? new ArrayList<>(description) : new ArrayList<>();
        this.icon = icon != null ? icon : new RankupIcon("minecraft:paper");
        this.luckPerms = luckPerms != null ? luckPerms : new RankupLuckPermsSettings(this.id, true);
        this.requirements = requirements != null ? requirements : new RankupRequirements(0.0, 0, RankupTaskMode.ALL, new ArrayList<>());
        this.actions = actions != null ? actions : new RankupActions(null, new ArrayList<>());
        this.enabled = enabled;
    }

    public String id() { return id; }
    public int order() { return order; }
    public String displayName() { return displayName; }
    public List<String> description() { return Collections.unmodifiableList(description); }
    public RankupIcon icon() { return icon; }
    public RankupLuckPermsSettings luckPerms() { return luckPerms; }
    public RankupRequirements requirements() { return requirements; }
    public RankupActions actions() { return actions; }
    public boolean enabled() { return enabled; }

    public RankupRank withId(String id) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    public RankupRank withOrder(int order) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    public RankupRank withDisplayName(String displayName) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    public RankupRank withDescription(List<String> description) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    public RankupRank withIcon(RankupIcon icon) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    public RankupRank withLuckPerms(RankupLuckPermsSettings luckPerms) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    public RankupRank withRequirements(RankupRequirements requirements) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    public RankupRank withActions(RankupActions actions) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    public RankupRank withEnabled(boolean enabled) {
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }
}
