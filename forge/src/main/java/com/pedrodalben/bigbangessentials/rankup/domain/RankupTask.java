package com.pedrodalben.bigbangessentials.rankup.domain;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RankupTask {
    private final String id;
    private final String displayName;
    private final List<String> description;
    private final ObjectiveActionType type;
    private final int target;
    private final RankupTaskFilter filters;
    private final boolean enabled;

    public RankupTask(String id, String displayName, List<String> description, ObjectiveActionType type,
                      int target, RankupTaskFilter filters, boolean enabled) {
        this.id = id != null ? id.toLowerCase() : UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : "&7" + this.id;
        this.description = description != null ? new ArrayList<>(description) : new ArrayList<>();
        this.type = type != null ? type : ObjectiveActionType.UNKNOWN;
        this.target = Math.max(0, target);
        this.filters = filters != null ? filters : new RankupTaskFilter();
        this.enabled = enabled;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public List<String> description() { return Collections.unmodifiableList(description); }
    public ObjectiveActionType type() { return type; }
    public int target() { return target; }
    public RankupTaskFilter filters() { return filters; }
    public boolean enabled() { return enabled; }

    public RankupTask withId(String id) {
        return new RankupTask(id, displayName, description, type, target, filters, enabled);
    }

    public RankupTask withDisplayName(String displayName) {
        return new RankupTask(id, displayName, description, type, target, filters, enabled);
    }

    public RankupTask withDescription(List<String> description) {
        return new RankupTask(id, displayName, description, type, target, filters, enabled);
    }

    public RankupTask withType(ObjectiveActionType type) {
        return new RankupTask(id, displayName, description, type, target, filters, enabled);
    }

    public RankupTask withTarget(int target) {
        return new RankupTask(id, displayName, description, type, target, filters, enabled);
    }

    public RankupTask withFilters(RankupTaskFilter filters) {
        return new RankupTask(id, displayName, description, type, target, filters, enabled);
    }

    public RankupTask withEnabled(boolean enabled) {
        return new RankupTask(id, displayName, description, type, target, filters, enabled);
    }
}
