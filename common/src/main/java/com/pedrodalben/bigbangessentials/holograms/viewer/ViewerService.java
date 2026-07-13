package com.pedrodalben.bigbangessentials.holograms.viewer;

import com.pedrodalben.bigbangessentials.holograms.render.RenderSnapshot;
import com.pedrodalben.bigbangessentials.holograms.render.RenderFingerprint;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ViewerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewerService.class);

    private final ConcurrentHashMap<UUID, ViewerSession> sessions = new ConcurrentHashMap<>();

    public static final class ViewerSession {
        private final UUID playerUuid;
        private volatile String dimension;
        private volatile int chunkX;
        private volatile int chunkZ;
        private final Set<String> visibleIds;
        private final Set<String> forcedShown;
        private final Set<String> forcedHidden;
        private final Map<String, Integer> currentPages;
        private final Map<String, RenderFingerprint> fingerprints;
        private final Map<Integer, String> entityIdToHologram;
        private volatile long lastFullSyncTick;
        private volatile boolean dirty;

        public ViewerSession(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.visibleIds = ConcurrentHashMap.newKeySet();
            this.forcedShown = ConcurrentHashMap.newKeySet();
            this.forcedHidden = ConcurrentHashMap.newKeySet();
            this.currentPages = new ConcurrentHashMap<>();
            this.fingerprints = new ConcurrentHashMap<>();
            this.entityIdToHologram = new ConcurrentHashMap<>();
        }

        public UUID playerUuid() { return playerUuid; }
        public String dimension() { return dimension; }
        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public Set<String> visibleIds() { return visibleIds; }
        public Set<String> forcedShown() { return forcedShown; }
        public Set<String> forcedHidden() { return forcedHidden; }
        public Map<String, Integer> currentPages() { return currentPages; }
        public Map<String, RenderFingerprint> fingerprints() { return fingerprints; }
        public Map<Integer, String> entityIdToHologram() { return entityIdToHologram; }
        public long lastFullSyncTick() { return lastFullSyncTick; }
        public boolean dirty() { return dirty; }

        public void setDimension(String dimension) { this.dimension = dimension; }
        public void setChunkX(int chunkX) { this.chunkX = chunkX; }
        public void setChunkZ(int chunkZ) { this.chunkZ = chunkZ; }
        public void setLastFullSyncTick(long tick) { this.lastFullSyncTick = tick; }
        public void setDirty(boolean dirty) { this.dirty = dirty; }
    }

    public ViewerSession getSession(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ViewerSession session = sessions.computeIfAbsent(uuid, ViewerSession::new);
        session.setDimension(player.level().dimension().location().toString());
        var chunkPos = player.chunkPosition();
        session.setChunkX(chunkPos.x);
        session.setChunkZ(chunkPos.z);
        return session;
    }

    public ViewerSession removeSession(UUID uuid) {
        return sessions.remove(uuid);
    }

    public void invalidate(ServerPlayer player) {
        ViewerSession session = sessions.get(player.getUUID());
        if (session != null) {
            session.visibleIds.clear();
        }
    }

    public boolean isVisible(ServerPlayer player, String hologramId) {
        ViewerSession session = sessions.get(player.getUUID());
        return session != null && session.visibleIds.contains(hologramId);
    }

    public void addVisible(ServerPlayer player, String hologramId, int entityId) {
        ViewerSession session = getSession(player);
        session.visibleIds.add(hologramId);
        session.entityIdToHologram.put(entityId, hologramId);
    }

    public boolean removeVisible(ServerPlayer player, String hologramId) {
        ViewerSession session = sessions.get(player.getUUID());
        if (session != null) {
            session.visibleIds.remove(hologramId);
            session.entityIdToHologram.values().removeIf(hologramId::equals);
            return true;
        }
        return false;
    }

    public int getCurrentPage(ServerPlayer player, String hologramId) {
        ViewerSession session = sessions.get(player.getUUID());
        if (session != null) {
            return session.currentPages.getOrDefault(hologramId, 0);
        }
        return 0;
    }

    public void setCurrentPage(ServerPlayer player, String hologramId, int page) {
        ViewerSession session = getSession(player);
        session.currentPages.put(hologramId, page);
    }

    public boolean hasFingerprint(ServerPlayer player, String hologramId, RenderFingerprint fingerprint) {
        ViewerSession session = sessions.get(player.getUUID());
        if (session != null) {
            RenderFingerprint existing = session.fingerprints.get(hologramId);
            return existing != null && existing.equals(fingerprint);
        }
        return false;
    }

    public void setFingerprint(ServerPlayer player, String hologramId, RenderFingerprint fingerprint) {
        ViewerSession session = getSession(player);
        session.fingerprints.put(hologramId, fingerprint);
    }

    public Set<String> getVisibleHolograms(ServerPlayer player) {
        ViewerSession session = sessions.get(player.getUUID());
        if (session != null) {
            return new HashSet<>(session.visibleIds);
        }
        return Collections.emptySet();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public void clear() {
        sessions.clear();
    }
}
