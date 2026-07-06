package com.pedrodalben.bigbangessentials.holograms.config;

import net.minecraft.world.entity.Display;

public record HologramConfig(
    boolean enabled,
    int defaultViewDistance,
    int maxViewDistance,
    int defaultRefreshIntervalTicks,
    int dynamicUpdateMinIntervalTicks,
    int maxLinesPerHologram,
    int maxCharactersPerLine,
    int maxHologramsPerPlayer,
    boolean shadow,
    boolean seeThrough,
    Display.BillboardConstraints billboard,
    int viewerSyncIntervalTicks,
    int maxViewerSyncsPerTick,
    int maxContentUpdatesPerTick,
    boolean spatialIndexEnabled,
    boolean debugMetrics,
    boolean cleanupEnabled,
    boolean cleanupOnServerStart,
    boolean cleanupOnEntityLoad,
    boolean persistenceEnabled
) {
    public static HologramConfig defaults() {
        return new HologramConfig(
            true,
            24,
            48,
            20,
            40,
            8,
            256,
            64,
            true,
            false,
            Display.BillboardConstraints.CENTER,
            10,
            50,
            50,
            true,
            false,
            true,
            true,
            true,
            true
        );
    }
}
