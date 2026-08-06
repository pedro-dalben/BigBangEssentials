package com.pedrodalben.bigbangessentials.npcs.api;

public final class NpcInteractionConfig {
    private final double distance;
    private final long cooldownMillis;
    private final String permission;

    public NpcInteractionConfig(double distance, long cooldownMillis, String permission) {
        this.distance = Math.max(0.5, distance);
        this.cooldownMillis = Math.max(0, cooldownMillis);
        this.permission = permission != null ? permission.trim() : "";
    }

    public static NpcInteractionConfig defaults() {
        return new NpcInteractionConfig(4.5, 750, "");
    }

    public double distance() { return distance; }
    public long cooldownMillis() { return cooldownMillis; }
    public String permission() { return permission; }
    public boolean hasPermission() { return !permission.isEmpty(); }
}
