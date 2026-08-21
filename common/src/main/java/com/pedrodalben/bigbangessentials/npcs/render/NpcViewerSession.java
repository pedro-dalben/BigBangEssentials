package com.pedrodalben.bigbangessentials.npcs.render;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-viewer rendering state. All mutations happen on the server thread.
 */
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

    public NpcViewState getStateIfPresent(String npcId) {
        return npcStates.get(npcId);
    }

    public void clear() {
        visibleNpcIds.clear();
        entityIdToNpc.clear();
        npcStates.clear();
    }

    /** Explicit per-viewer/NPC render state (see task: no eternal SPAWNING, no invisible NPC marked visible). */
    public enum NpcViewerRenderState {
        NOT_VISIBLE,
        RESOLVING_SKIN,
        SPAWNING,
        VISIBLE,
        DESPAWNING,
        FAILED
    }

    public static final class NpcViewState {
        private volatile NpcViewerRenderState renderState = NpcViewerRenderState.NOT_VISIBLE;
        private int entityId;
        private String skinHash; // last skin texture hash sent to this viewer
        private double lastX, lastY, lastZ;
        private float lastYaw, lastHeadYaw, lastPitch;
        private boolean slim;
        private long spawnedAt;
        private String lastError;
        private long lastReskinAttempt;

        public NpcViewerRenderState renderState() { return renderState; }

        /**
         * Marks the spawn as started. Returns {@code false} when the NPC is
         * already spawning/visible/pending for this viewer, preventing
         * duplicate spawn sequences.
         */
        public boolean beginSpawn() {
            NpcViewerRenderState current = renderState;
            if (current == NpcViewerRenderState.SPAWNING
                || current == NpcViewerRenderState.VISIBLE
                || current == NpcViewerRenderState.RESOLVING_SKIN) {
                return false;
            }
            renderState = NpcViewerRenderState.SPAWNING;
            return true;
        }

        /** Called when a spawn attempt is abandoned (viewer left, NPC removed, etc.). */
        public void onSpawnAborted() {
            if (renderState == NpcViewerRenderState.SPAWNING
                || renderState == NpcViewerRenderState.RESOLVING_SKIN) {
                renderState = NpcViewerRenderState.NOT_VISIBLE;
            }
        }

        /** Marks the spawn attempt as failed; the next visibility scan may retry. */
        public void markSpawnFailed(String error) {
            renderState = NpcViewerRenderState.FAILED;
            lastError = error;
        }

        /** Marks the NPC as successfully spawned and visible for this viewer. */
        public void markVisible() {
            renderState = NpcViewerRenderState.VISIBLE;
        }

        public void onDespawn() {
            renderState = NpcViewerRenderState.NOT_VISIBLE;
        }

        public boolean isVisible() {
            return renderState == NpcViewerRenderState.VISIBLE;
        }

        public boolean isSpawnInProgress() {
            return renderState == NpcViewerRenderState.SPAWNING
                || renderState == NpcViewerRenderState.RESOLVING_SKIN;
        }

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
        public String lastError() { return lastError; }
        public long lastReskinAttempt() { return lastReskinAttempt; }
        public void setLastReskinAttempt(long time) { this.lastReskinAttempt = time; }
    }
}
