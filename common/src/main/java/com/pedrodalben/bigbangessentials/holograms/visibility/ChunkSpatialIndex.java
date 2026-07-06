package com.pedrodalben.bigbangessentials.holograms.visibility;

import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ChunkSpatialIndex {
    private final Map<ResourceLocation, Map<Long, Set<String>>> index = new HashMap<>();
    private final Map<String, ChunkKey> reverse = new HashMap<>();

    public void add(String hologramId, HologramLocation location) {
        remove(hologramId);
        int chunkX = chunk(location.x());
        int chunkZ = chunk(location.z());
        long key = pack(chunkX, chunkZ);
        index.computeIfAbsent(location.dimensionId(), ignored -> new HashMap<>())
            .computeIfAbsent(key, ignored -> new HashSet<>())
            .add(hologramId);
        reverse.put(hologramId, new ChunkKey(location.dimensionId(), key));
    }

    public void remove(String hologramId) {
        ChunkKey existing = reverse.remove(hologramId);
        if (existing == null) {
            return;
        }
        Map<Long, Set<String>> byChunk = index.get(existing.dimension());
        if (byChunk == null) {
            return;
        }
        Set<String> ids = byChunk.get(existing.chunkKey());
        if (ids == null) {
            return;
        }
        ids.remove(hologramId);
        if (ids.isEmpty()) {
            byChunk.remove(existing.chunkKey());
        }
        if (byChunk.isEmpty()) {
            index.remove(existing.dimension());
        }
    }

    public Set<String> query(ResourceLocation dimension, double x, double z, int radiusBlocks) {
        Set<String> result = new HashSet<>();
        Map<Long, Set<String>> byChunk = index.get(dimension);
        if (byChunk == null || byChunk.isEmpty()) {
            return result;
        }

        int chunkRadius = Math.max(0, (int) Math.ceil(radiusBlocks / 16.0D));
        int centerChunkX = chunk(x);
        int centerChunkZ = chunk(z);
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Set<String> ids = byChunk.get(pack(centerChunkX + dx, centerChunkZ + dz));
                if (ids != null) {
                    result.addAll(ids);
                }
            }
        }
        return result;
    }

    public void clear() {
        index.clear();
        reverse.clear();
    }

    private static int chunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private record ChunkKey(ResourceLocation dimension, long chunkKey) {
    }
}
