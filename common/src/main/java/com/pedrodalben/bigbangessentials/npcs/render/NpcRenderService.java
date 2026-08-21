package com.pedrodalben.bigbangessentials.npcs.render;

import com.pedrodalben.bigbangessentials.npcs.api.NpcDefinition;
import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerSession.NpcViewState;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCache;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCacheEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders virtual player NPCs per viewer.
 *
 * <p>Spawn is transactional: the NPC is only marked visible after all packets
 * were sent successfully, and a failed spawn is rolled back and retried by the
 * next visibility scan. The NPC never depends on Mojang being reachable — when
 * the skin is not resolved yet the NPC spawns with the default skin and is
 * re-skinned as soon as the profile textures arrive.</p>
 */
public class NpcRenderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcRenderService.class);
    private static final byte ALL_SKIN_LAYERS = (byte) 0x7F; // cape, jacket, sleeves, pants, hat

    /** Gate used to revalidate against the manager before/after async skin resolution. */
    public interface NpcRenderGate {
        boolean npcModuleActive();
        Optional<NpcDefinition> find(String id);
    }

    private final NpcViewerService viewerService;
    private final SkinCache skinCache;
    private final NpcPacketSender packetSender;
    private final Map<String, NpcRenderState> renderStates = new LinkedHashMap<>();

    private volatile NpcRenderGate gate;
    private volatile boolean shuttingDown;

    private final AtomicInteger failedSpawns = new AtomicInteger();
    private final AtomicInteger packetFailures = new AtomicInteger();
    private final AtomicInteger reskinsApplied = new AtomicInteger();

    public NpcRenderService(NpcViewerService viewerService, SkinCache skinCache) {
        this(viewerService, skinCache, new NpcPacketSenderImpl());
    }

    public NpcRenderService(NpcViewerService viewerService, SkinCache skinCache, NpcPacketSender packetSender) {
        this.viewerService = viewerService;
        this.skinCache = skinCache;
        this.packetSender = packetSender;
    }

    public void setGate(NpcRenderGate gate) {
        this.gate = gate;
    }

    public void register(NpcDefinition definition) {
        String id = definition.id();
        renderStates.computeIfAbsent(id, k -> {
            int entityId = VirtualEntityIdAllocator.npcs().allocate();
            return new NpcRenderState(entityId, definition);
        });
        renderStates.get(id).definition = definition;
    }

    public void unregister(String id) {
        renderStates.remove(id);
    }

    public NpcRenderState getState(String id) {
        return renderStates.get(id);
    }

    public void shutdown() {
        shuttingDown = true;
    }

    /**
     * Spawns the NPC for the viewer. Never blocks on skin resolution: if the
     * skin is already cached the NPC spawns with it immediately; otherwise it
     * spawns with the default skin and is re-skinned when the profile loads.
     */
    public void spawn(ServerPlayer player, NpcDefinition npc) {
        NpcRenderState state = renderStates.get(npc.id());
        if (state == null) return;
        NpcViewerSession session = viewerService.getSession(player);
        if (session == null) return;

        NpcViewState vs = session.getState(npc.id());
        if (!vs.beginSpawn()) return; // already spawning / visible for this viewer

        CompletableFuture<SkinCacheEntry> future = skinCache.resolve(npc.skin().playerName());
        SkinCacheEntry immediate = future.isDone() ? future.getNow(null) : null;

        // Spawn immediately with whatever is available — Mojang latency must not
        // keep the NPC invisible.
        finalizeSpawn(player, npc, state, vs, immediate);

        String npcId = npc.id();
        future.whenComplete((entry, error) -> {
            MinecraftServer server = player.getServer();
            if (server == null) return;
            server.execute(() -> onSkinResolved(player, npcId, state, vs, entry, error));
        });
    }

    /** Attempts to apply the real skin to an NPC that spawned with the fallback. */
    public boolean tryReskin(ServerPlayer player, NpcDefinition npc, NpcViewState vs) {
        if (shuttingDown) return false;
        if (!vs.isVisible()) return false;
        if (vs.skinHash() != null && !vs.skinHash().isEmpty()) return false;
        long now = System.currentTimeMillis();
        if (now - vs.lastReskinAttempt() < 10_000) return false;
        vs.setLastReskinAttempt(now);

        NpcRenderState state = renderStates.get(npc.id());
        if (state == null) return false;

        String npcId = npc.id();
        CompletableFuture<SkinCacheEntry> future = skinCache.resolve(npc.skin().playerName());
        future.whenComplete((entry, error) -> {
            MinecraftServer server = player.getServer();
            if (server == null) return;
            server.execute(() -> onSkinResolved(player, npcId, state, vs, entry, error));
        });
        return true;
    }

    private void finalizeSpawn(ServerPlayer player, NpcDefinition npc, NpcRenderState state,
                               NpcViewState vs, SkinCacheEntry skinEntry) {
        UUID entityUuid = deriveUuid(npc.id());
        if (!validSpawnTarget(player, npc, vs)) {
            return;
        }

        boolean hasSkin = skinEntry != null && !skinEntry.negative() && !skinEntry.textureValue().isEmpty();
        try {
            if (hasSkin) {
                packetSender.addPlayerInfo(player, entityUuid, npc.id(),
                    skinEntry.textureValue(), skinEntry.textureSignature());
            }
            packetSender.spawnEntity(player, state.entityId, entityUuid,
                npc.location().x(), npc.location().y(), npc.location().z(),
                npc.location().yaw(), npc.location().pitch());
            packetSender.setSkinLayers(player, state.entityId, ALL_SKIN_LAYERS);
            packetSender.rotateHead(player, state.entityId, npc.location().yaw());

            vs.setEntityId(state.entityId);
            vs.setSpawnedAt(System.currentTimeMillis());
            vs.setSkinHash(hasSkin ? skinEntry.textureValue() : "");
            vs.setSlim(hasSkin && "slim".equalsIgnoreCase(skinEntry.model()));
            vs.setLastPosition(npc.location().x(), npc.location().y(), npc.location().z());
            vs.setLastYaw(npc.location().yaw());
            vs.setLastHeadYaw(npc.location().yaw());
            vs.setLastPitch(npc.location().pitch());
            vs.markVisible();

            NpcViewerSession session = viewerService.getSession(player.getUUID());
            if (session != null) {
                session.visibleNpcIds().add(npc.id());
                session.entityIdToNpc().put(state.entityId, npc.id());
            }
        } catch (Exception e) {
            rollbackSpawn(player, state, vs, entityUuid);
            packetFailures.incrementAndGet();
            vs.markSpawnFailed(e.getMessage());
            LOGGER.warn("[NPC] Spawn failed npc={} viewer={} entityId={} stage=PACKETS error={}",
                npc.id(), player.getGameProfile().getName(), state.entityId, e.getMessage());
        }
    }

    /** Revalidates the viewer/NPC before sending packets; aborts cleanly when stale. */
    private boolean validSpawnTarget(ServerPlayer player, NpcDefinition npc, NpcViewState vs) {
        if (shuttingDown) {
            vs.onSpawnAborted();
            return false;
        }
        if (viewerService.getSession(player.getUUID()) == null) {
            vs.onSpawnAborted();
            return false;
        }
        if (gate == null) return true;

        if (!gate.npcModuleActive()) {
            vs.onSpawnAborted();
            return false;
        }
        NpcDefinition current = gate.find(npc.id()).orElse(null);
        if (current == null || !current.enabled()) {
            vs.onSpawnAborted();
            return false;
        }
        if (!player.level().dimension().location().equals(current.location().dimension())) {
            vs.onSpawnAborted();
            return false;
        }
        double distSq = player.distanceToSqr(current.location().x(), current.location().y(), current.location().z());
        if (distSq > current.viewDistance() * current.viewDistance()) {
            vs.onSpawnAborted();
            return false;
        }
        return true;
    }

    private void rollbackSpawn(ServerPlayer player, NpcRenderState state, NpcViewState vs, UUID uuid) {
        try {
            packetSender.removeEntities(player, state.entityId);
        } catch (Exception ignored) {
        }
        try {
            packetSender.removePlayerInfo(player, uuid);
        } catch (Exception ignored) {
        }
        vs.onSpawnAborted();
    }

    private void onSkinResolved(ServerPlayer player, String npcId, NpcRenderState state,
                                NpcViewState vs, SkinCacheEntry entry, Throwable error) {
        if (shuttingDown) return;
        NpcViewerSession session = viewerService.getSession(player.getUUID());
        if (session == null) return;                    // viewer left
        if (!session.visibleNpcIds().contains(npcId)) return; // no longer visible to this viewer
        if (!vs.isVisible()) return;
        if (entry == null || entry.negative() || entry.textureValue().isEmpty()) return; // keep fallback
        if (entry.textureValue().equals(vs.skinHash())) return; // skin unchanged

        NpcDefinition current = state.definition();
        if (gate != null) {
            current = gate.find(npcId).orElse(null);
            if (current == null || !current.enabled()) return;
        }
        reskin(player, npcId, state, vs, entry);
    }

    /** Applies a real skin to an already-visible NPC (falls back to default skin at spawn). */
    private void reskin(ServerPlayer player, String npcId, NpcRenderState state,
                        NpcViewState vs, SkinCacheEntry entry) {
        UUID uuid = deriveUuid(npcId);
        NpcDefinition npc = state.definition();
        try {
            packetSender.removePlayerInfo(player, uuid);
            packetSender.addPlayerInfo(player, uuid, npc.id(), entry.textureValue(), entry.textureSignature());
            // Re-create the entity so the client re-reads the PlayerInfo (skin).
            packetSender.removeEntities(player, state.entityId);
            packetSender.spawnEntity(player, state.entityId, uuid,
                npc.location().x(), npc.location().y(), npc.location().z(),
                npc.location().yaw(), npc.location().pitch());
            packetSender.setSkinLayers(player, state.entityId, ALL_SKIN_LAYERS);
            packetSender.rotateHead(player, state.entityId, vs.lastHeadYaw());
            vs.setSkinHash(entry.textureValue());
            vs.setSlim("slim".equalsIgnoreCase(entry.model()));
            reskinsApplied.incrementAndGet();
        } catch (Exception e) {
            packetFailures.incrementAndGet();
            LOGGER.warn("[NPC] Reskin failed npc={} viewer={} entityId={} error={}",
                npcId, player.getGameProfile().getName(), state.entityId, e.getMessage());
        }
    }

    public void despawn(ServerPlayer player, NpcDefinition npc) {
        NpcRenderState state = renderStates.get(npc.id());
        NpcViewerSession session = viewerService.getSession(player.getUUID());
        if (state == null || session == null) return;

        session.visibleNpcIds().remove(npc.id());
        session.entityIdToNpc().remove(state.entityId);

        NpcViewState vs = session.getState(npc.id());
        try {
            packetSender.removeEntities(player, state.entityId);
            packetSender.removePlayerInfo(player, deriveUuid(npc.id()));
        } catch (Exception e) {
            LOGGER.debug("Failed to despawn NPC '{}' for player: {}", npc.id(), e.getMessage());
        }
        vs.onDespawn();
    }

    public void sendLookUpdate(ServerPlayer player, NpcDefinition npc, float bodyYaw, float headYaw, float pitch,
                               NpcViewState vs) {
        NpcRenderState state = renderStates.get(npc.id());
        if (state == null) return;

        if (vs.lastYaw() == bodyYaw && vs.lastHeadYaw() == headYaw && vs.lastPitch() == pitch) return;

        try {
            packetSender.rotateHead(player, state.entityId, headYaw);
            packetSender.rotateBody(player, state.entityId, bodyYaw, pitch);
            vs.setLastYaw(bodyYaw);
            vs.setLastHeadYaw(headYaw);
            vs.setLastPitch(pitch);
        } catch (Exception e) {
            LOGGER.debug("Failed to send look update for NPC '{}': {}", npc.id(), e.getMessage());
        }
    }

    public void resetLook(ServerPlayer player, NpcDefinition npc, NpcViewState vs) {
        float baseYaw = npc.location().yaw();
        float basePitch = npc.location().pitch();
        if (vs.lastYaw() == baseYaw && vs.lastHeadYaw() == baseYaw && vs.lastPitch() == basePitch) return;
        sendLookUpdate(player, npc, baseYaw, baseYaw, basePitch, vs);
    }

    public int visibleNpcCount(ServerPlayer player) {
        NpcViewerSession session = viewerService.getSession(player.getUUID());
        return session != null ? session.visibleNpcIds().size() : 0;
    }

    public int pendingSpawnCount() {
        int count = 0;
        for (NpcViewerSession session : viewerService.allSessions()) {
            for (NpcViewState vs : session.npcStates().values()) {
                if (vs.isSpawnInProgress()) count++;
            }
        }
        return count;
    }

    public int failedSpawns() { return failedSpawns.get(); }
    public int packetFailures() { return packetFailures.get(); }
    public int reskinsApplied() { return reskinsApplied.get(); }

    public static UUID deriveUuid(String npcId) {
        return UUID.nameUUIDFromBytes(("bigbang-npc:" + npcId).getBytes(StandardCharsets.UTF_8));
    }

    public static final class NpcRenderState {
        private final int entityId;
        private volatile NpcDefinition definition;

        NpcRenderState(int entityId, NpcDefinition definition) {
            this.entityId = entityId;
            this.definition = definition;
        }

        public int entityId() { return entityId; }
        public NpcDefinition definition() { return definition; }
    }
}
