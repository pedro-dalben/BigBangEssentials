package com.pedrodalben.bigbangessentials.npcs.api;

import java.util.Objects;
import java.util.regex.Pattern;

public final class NpcDefinition {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_-]{1,64}$");

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final NpcLocation location;
    private final NpcSkin skin;
    private final NpcAction action;
    private final NpcHologramConfig hologram;
    private final NpcLookSettings lookSettings;
    private final double viewDistance;
    private final double despawnDistance;
    private final NpcInteractionConfig interaction;

    public NpcDefinition(String id, boolean enabled, String displayName, NpcLocation location,
                         NpcSkin skin, NpcAction action, NpcHologramConfig hologram,
                         NpcLookSettings lookSettings, double viewDistance, double despawnDistance,
                         NpcInteractionConfig interaction) {
        this.id = normalizeId(id);
        this.enabled = enabled;
        this.displayName = displayName != null ? displayName : id;
        this.location = Objects.requireNonNull(location, "location");
        this.skin = Objects.requireNonNull(skin, "skin");
        this.action = action != null ? action : NpcAction.none();
        this.hologram = hologram != null ? hologram : NpcHologramConfig.disabled();
        this.lookSettings = lookSettings != null ? lookSettings : NpcLookSettings.disabled();
        this.viewDistance = Math.max(1.0, viewDistance);
        this.despawnDistance = Math.max(viewDistance, despawnDistance);
        this.interaction = interaction != null ? interaction : NpcInteractionConfig.defaults();
    }

    public static String normalizeId(String id) {
        if (id == null) throw new IllegalArgumentException("NPC id cannot be null");
        String normalized = id.trim().toLowerCase();
        if (!VALID_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid NPC id: '" + id + "'. Must match [a-z0-9_-]{1,64}");
        }
        return normalized;
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public String displayName() { return displayName; }
    public NpcLocation location() { return location; }
    public NpcSkin skin() { return skin; }
    public NpcAction action() { return action; }
    public NpcHologramConfig hologram() { return hologram; }
    public NpcLookSettings lookSettings() { return lookSettings; }
    public double viewDistance() { return viewDistance; }
    public double despawnDistance() { return despawnDistance; }
    public NpcInteractionConfig interaction() { return interaction; }

    public NpcDefinition withEnabled(boolean enabled) {
        return new NpcDefinition(id, enabled, displayName, location, skin, action, hologram,
            lookSettings, viewDistance, despawnDistance, interaction);
    }

    public NpcDefinition withLocation(NpcLocation newLocation) {
        return new NpcDefinition(id, enabled, displayName, newLocation, skin, action, hologram,
            lookSettings, viewDistance, despawnDistance, interaction);
    }

    public NpcDefinition withSkin(NpcSkin newSkin) {
        return new NpcDefinition(id, enabled, displayName, location, newSkin, action, hologram,
            lookSettings, viewDistance, despawnDistance, interaction);
    }

    public NpcDefinition withAction(NpcAction newAction) {
        return new NpcDefinition(id, enabled, displayName, location, skin, newAction, hologram,
            lookSettings, viewDistance, despawnDistance, interaction);
    }

    public NpcDefinition withDisplayName(String newDisplayName) {
        return new NpcDefinition(id, enabled, newDisplayName, location, skin, action, hologram,
            lookSettings, viewDistance, despawnDistance, interaction);
    }

    public NpcDefinition withHologram(NpcHologramConfig newHologram) {
        return new NpcDefinition(id, enabled, displayName, location, skin, action, newHologram,
            lookSettings, viewDistance, despawnDistance, interaction);
    }

    public NpcDefinition withLookSettings(NpcLookSettings newLook) {
        return new NpcDefinition(id, enabled, displayName, location, skin, action, hologram,
            newLook, viewDistance, despawnDistance, interaction);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NpcDefinition that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
