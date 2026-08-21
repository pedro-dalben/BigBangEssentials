package com.pedrodalben.bigbangessentials.npcs.api;

import java.util.Objects;

public final class NpcSkin {
    private final String playerName;
    private final String uuid;
    private final String textureValue;
    private final String textureSignature;
    private final String model; // "default" or "slim"
    private final long resolvedAt;

    public NpcSkin(String playerName, String uuid, String textureValue, String textureSignature, String model, long resolvedAt) {
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.uuid = uuid != null ? uuid : "";
        this.textureValue = textureValue != null ? textureValue : "";
        this.textureSignature = textureSignature != null ? textureSignature : "";
        this.model = model != null ? model : "default";
        this.resolvedAt = resolvedAt;
    }

    public static NpcSkin unresolved(String playerName) {
        return new NpcSkin(playerName, "", "", "", "default", 0L);
    }

    public String playerName() { return playerName; }
    public String uuid() { return uuid; }
    public String textureValue() { return textureValue; }
    public String textureSignature() { return textureSignature; }
    public String model() { return model; }
    public long resolvedAt() { return resolvedAt; }
    public boolean isResolved() { return !textureValue.isEmpty(); }
    public boolean isSlim() { return "slim".equalsIgnoreCase(model); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NpcSkin that)) return false;
        return resolvedAt == that.resolvedAt && playerName.equals(that.playerName)
            && textureValue.equals(that.textureValue) && model.equals(that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, textureValue, model, resolvedAt);
    }
}
