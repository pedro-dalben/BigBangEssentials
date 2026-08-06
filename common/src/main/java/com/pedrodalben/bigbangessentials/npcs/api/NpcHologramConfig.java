package com.pedrodalben.bigbangessentials.npcs.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NpcHologramConfig {
    private final boolean enabled;
    private final List<String> lines;
    private final double offsetY;
    private final double viewDistance;
    private final boolean shadow;
    private final boolean seeThrough;

    public NpcHologramConfig(boolean enabled, List<String> lines, double offsetY, double viewDistance, boolean shadow, boolean seeThrough) {
        this.enabled = enabled;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines != null ? lines : List.of()));
        this.offsetY = Math.max(0.0, offsetY);
        this.viewDistance = Math.max(1.0, viewDistance);
        this.shadow = shadow;
        this.seeThrough = seeThrough;
    }

    public static NpcHologramConfig defaults(String displayName) {
        return new NpcHologramConfig(true, List.of(displayName), 2.25, 32.0, true, false);
    }

    public static NpcHologramConfig disabled() {
        return new NpcHologramConfig(false, List.of(), 2.25, 32.0, true, false);
    }

    public boolean enabled() { return enabled; }
    public List<String> lines() { return lines; }
    public double offsetY() { return offsetY; }
    public double viewDistance() { return viewDistance; }
    public boolean shadow() { return shadow; }
    public boolean seeThrough() { return seeThrough; }

    public NpcHologramConfig withEnabled(boolean enabled) {
        return new NpcHologramConfig(enabled, lines, offsetY, viewDistance, shadow, seeThrough);
    }

    public NpcHologramConfig withLines(List<String> lines) {
        return new NpcHologramConfig(enabled, lines, offsetY, viewDistance, shadow, seeThrough);
    }
}
