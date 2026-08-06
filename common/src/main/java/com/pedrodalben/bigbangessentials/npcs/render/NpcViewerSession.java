package com.pedrodalben.bigbangessentials.npcs.render;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcViewerSession {
    private final UUID playerUuid;
    private String dimension;
    private final Set<String> visibleNpcIds;
    private final Map<Integer, String> entityIdToNpc;
    private final Map<String, NpcViewState> npcStates;
    private volatile long lastSyncTick;

    public NpcViewerSession(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.visibleNpcIds = ConcurrentHashMap.newKeySet();
        this.entityIdToNpc = new ConcurrentHashMap<>();
        this.npcStates = new ConcurrentHashMap<>();
    }

    public UUID playerUuid() { return playerUuid; }
    public String dimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public Set<String> visibleNpcIds() { return visibleNpcIds; }
    public Map<Integer, String> entityIdToNpc() { return entityIdToNpc; }
    public Map<String, NpcViewState> npcStates() { return npcStates; }
    public long lastSyncTick() { return lastSyncTick; }
    public void setLastSyncTick(long tick) { this.lastSyncTick = tick; }

    public NpcViewState getState(String npcId) {
        return npcStates.computeIfAbsent(npcId, k -> new NpcViewState());
    }

    public void clear() {
        visibleNpcIds.clear();
        entityIdToNpc.clear();
        npcStates.clear();
    }

    public static final class NpcViewState {
        private int entityId;
        private String skinHash; // last skin texture hash sent
        private double lastX, lastY, lastZ;
        private float lastYaw, lastHeadYaw, lastPitch;
        private boolean slim;
        private long spawnedAt;

        public int entityId() { return entityId; }
        public void setEntityId(int id) { this.entityId = id; }
        public String skinHash() { return skinHash; }
        public void setSkinHash(String hash) { this.skinHash = hash; }
        public double lastX() { return lastX; }
        public double lastY() { return lastY; }
        public double lastZ() { return lastZ; }
        public float lastYaw() { return lastYaw; }
        public float lastHeadYaw() { return lastHeadYaw; }
        public float lastPitch() { return lastPitch; }
        public void setLastPosition(double x, double y, double z) { this.lastX = x; this.lastY = y; this.lastZ = z; }
        public void setLastYaw(float yaw) { this.lastYaw = yaw; }
        public void setLastHeadYaw(float hYaw) { this.lastHeadYaw = hYaw; }
        public void setLastPitch(float pitch) { this.lastPitch = pitch; }
        public boolean slim() { return slim; }
        public void setSlim(boolean slim) { this.slim = slim; }
        public long spawnedAt() { return spawnedAt; }
        public void setSpawnedAt(long time) { this.spawnedAt = time; }
    }
}
