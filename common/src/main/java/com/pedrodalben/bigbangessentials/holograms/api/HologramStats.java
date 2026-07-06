package com.pedrodalben.bigbangessentials.holograms.api;

import java.time.Instant;

public record HologramStats(
    int registeredHolograms,
    int persistentHolograms,
    int crateHolograms,
    int activePlayers,
    int activeViewerEntries,
    double averageVisiblePerPlayer,
    int pendingContentUpdates,
    long spawnPackets,
    long updatePackets,
    long destroyPackets,
    double averageUpdateNanos,
    int legacyEntitiesRemoved,
    Instant lastLegacyCleanup
) {
}
