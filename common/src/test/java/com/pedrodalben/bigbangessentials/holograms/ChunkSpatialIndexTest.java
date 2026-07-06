package com.pedrodalben.bigbangessentials.holograms;

import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import com.pedrodalben.bigbangessentials.holograms.visibility.ChunkSpatialIndex;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSpatialIndexTest {
    @Test
    void returnsOnlyNearbyIdsWithinChunkRadius() {
        ChunkSpatialIndex index = new ChunkSpatialIndex();
        index.add("bigbangessentials:test/a", new HologramLocation(Level.OVERWORLD, 10.0D, 70.0D, 10.0D));
        index.add("bigbangessentials:test/b", new HologramLocation(Level.OVERWORLD, 260.0D, 70.0D, 260.0D));

        Set<String> nearby = index.query(Level.OVERWORLD.location(), 0.0D, 0.0D, 48);
        assertTrue(nearby.contains("bigbangessentials:test/a"));
        assertFalse(nearby.contains("bigbangessentials:test/b"));
    }
}
