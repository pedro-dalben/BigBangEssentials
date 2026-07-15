package com.pedrodalben.bigbangessentials.holograms.service;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.visibility.ChunkSpatialIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class HologramRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(HologramRegistry.class);

    private final Map<String, ManagedHologram> holograms = new LinkedHashMap<>();
    private final ChunkSpatialIndex spatialIndex = new ChunkSpatialIndex();
    private final AtomicInteger nextEntityId = new AtomicInteger(1_500_000_000);

    public ManagedHologram add(HologramDefinition definition) {
        return add(definition, false, false);
    }

    public ManagedHologram add(HologramDefinition definition, boolean hasAnyPlaceholders, boolean playerScopedPlaceholders) {
        String id = HologramDefinition.normalizeId(definition.id());
        ManagedHologram existing = holograms.get(id);
        int entityId = existing != null ? existing.entityId : nextEntityId.getAndIncrement();
        UUID entityUuid = existing != null ? existing.entityUuid
            : UUID.nameUUIDFromBytes(("bigbang-hologram:" + id).getBytes(StandardCharsets.UTF_8));
        ManagedHologram hologram = new ManagedHologram(entityId, entityUuid, definition, hasAnyPlaceholders, playerScopedPlaceholders);
        holograms.put(id, hologram);
        spatialIndex.add(id, definition.location());
        LOGGER.debug("Registered hologram {}", id);
        return hologram;
    }

    public ManagedHologram remove(String id) {
        String normalized = HologramDefinition.normalizeId(id);
        ManagedHologram removed = holograms.remove(normalized);
        if (removed != null) {
            spatialIndex.remove(normalized);
            LOGGER.debug("Unregistered hologram {}", normalized);
        }
        return removed;
    }

    public ManagedHologram get(String id) {
        return holograms.get(HologramDefinition.normalizeId(id));
    }

    public boolean contains(String id) {
        return holograms.containsKey(HologramDefinition.normalizeId(id));
    }

    public Collection<HologramDefinition> getAll() {
        List<HologramDefinition> definitions = new ArrayList<>();
        for (ManagedHologram hologram : holograms.values()) {
            definitions.add(hologram.definition);
        }
        return definitions;
    }

    public Collection<ManagedHologram> getAllManaged() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    public void clear() {
        holograms.clear();
        spatialIndex.clear();
        LOGGER.debug("Cleared all holograms from registry");
    }

    public int size() {
        return holograms.size();
    }

    public Set<String> queryNearby(ResourceLocation dimension, double x, double z, int radius) {
        return spatialIndex.query(dimension, x, z, radius);
    }

    public int allocateEntityId() {
        return nextEntityId.getAndIncrement();
    }

    public record ComponentCache(Component component, long expiresAtTick, int page) {}

    public static final class ManagedHologram {
        private final int entityId;
        private final UUID entityUuid;
        private final HologramDefinition definition;
        private final boolean hasAnyPlaceholders;
        private final boolean playerScopedPlaceholders;
        private int activePage;
        private long nextUpdateTick = Long.MAX_VALUE;
        private ComponentCache globalCache;
        private final Map<UUID, ComponentCache> viewerCache = new ConcurrentHashMap<>();

        public ManagedHologram(int entityId, UUID entityUuid, HologramDefinition definition,
                               boolean hasAnyPlaceholders, boolean playerScopedPlaceholders) {
            this.entityId = entityId;
            this.entityUuid = entityUuid;
            this.definition = definition;
            this.hasAnyPlaceholders = hasAnyPlaceholders;
            this.playerScopedPlaceholders = playerScopedPlaceholders;
        }

        public int entityId() {
            return entityId;
        }

        public UUID entityUuid() {
            return entityUuid;
        }

        public HologramDefinition definition() {
            return definition;
        }

        public boolean hasAnyPlaceholders() {
            return hasAnyPlaceholders;
        }

        public boolean playerScopedPlaceholders() {
            return playerScopedPlaceholders;
        }

        public int activePage() {
            return activePage;
        }

        public void setActivePage(int activePage) {
            this.activePage = activePage;
        }

        public long nextUpdateTick() {
            return nextUpdateTick;
        }

        public void setNextUpdateTick(long nextUpdateTick) {
            this.nextUpdateTick = nextUpdateTick;
        }

        public ComponentCache globalCache() {
            return globalCache;
        }

        public void setGlobalCache(ComponentCache globalCache) {
            this.globalCache = globalCache;
        }

        public Map<UUID, ComponentCache> viewerCache() {
            return viewerCache;
        }
    }
}
