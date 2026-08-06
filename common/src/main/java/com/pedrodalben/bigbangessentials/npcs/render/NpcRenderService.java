package com.pedrodalben.bigbangessentials.npcs.render;

import com.pedrodalben.bigbangessentials.npcs.api.NpcDefinition;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCache;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCacheEntry;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NpcRenderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcRenderService.class);
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(2_000_000_000);

    private final NpcViewerService viewerService;
    private final SkinCache skinCache;
    private final NpcPacketSender packetSender;
    private final Map<String, NpcRenderState> renderStates = new LinkedHashMap<>();

    public NpcRenderService(NpcViewerService viewerService, SkinCache skinCache) {
        this.viewerService = viewerService;
        this.skinCache = skinCache;
        this.packetSender = new NpcPacketSenderImpl();
    }

    public void register(NpcDefinition definition) {
        String id = definition.id();
        renderStates.computeIfAbsent(id, k -> {
            int entityId = NEXT_ENTITY_ID.getAndIncrement();
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

    public void spawn(ServerPlayer player, NpcDefinition npc) {
        String id = npc.id();
        NpcRenderState state = renderStates.get(id);
        if (state == null) return;

        NpcViewerSession session = viewerService.getSession(player);
        NpcViewerSession.NpcViewState vs = session.getState(id);
        vs.setEntityId(state.entityId);
        vs.setSpawnedAt(System.currentTimeMillis());

        skinCache.resolve(npc.skin().playerName()).thenAccept(entry -> {
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.execute(() -> sendSpawn(player, npc, state, entry, session, vs));
            }
        }).exceptionally(e -> {
            LOGGER.warn("Failed to resolve skin for NPC '{}': {}", id, e.getMessage());
            return null;
        });
    }

    private void sendSpawn(ServerPlayer player, NpcDefinition npc, NpcRenderState state,
                           SkinCacheEntry skinEntry, NpcViewerSession session, NpcViewerSession.NpcViewState vs) {
        try {
            java.util.UUID entityUuid = deriveUuid(npc.id());

            if (!skinEntry.textureValue().isEmpty()) {
                packetSender.addPlayerInfo(player, entityUuid, npc.id(),
                    skinEntry.textureValue(), skinEntry.textureSignature());
            }

            double x = npc.location().x();
            double y = npc.location().y();
            double z = npc.location().z();
            player.connection.send(new ClientboundAddEntityPacket(
                state.entityId, entityUuid, x, y, z,
                npc.location().pitch(), npc.location().yaw(),
                net.minecraft.world.entity.EntityType.PLAYER, 0,
                net.minecraft.world.phys.Vec3.ZERO, 0.0));

            byte skinLayers = (byte) 0x7F;
            player.connection.send(new ClientboundSetEntityDataPacket(
                state.entityId,
                List.of(new net.minecraft.network.syncher.SynchedEntityData.DataValue<>(
                    17, net.minecraft.network.syncher.EntityDataSerializers.BYTE, skinLayers))));

            packetSender.rotateHead(player, state.entityId, npc.location().yaw());

            if (!skinEntry.textureValue().isEmpty()) {
                packetSender.removePlayerInfo(player, entityUuid, npc.id(),
                    skinEntry.textureValue(), skinEntry.textureSignature());
            }

            vs.setSkinHash(skinEntry.textureValue());
            vs.setLastPosition(x, y, z);
            vs.setLastYaw(npc.location().yaw());
            vs.setLastHeadYaw(npc.location().yaw());
            vs.setLastPitch(npc.location().pitch());
            vs.setSlim("slim".equalsIgnoreCase(skinEntry.model()));

            session.visibleNpcIds().add(npc.id());
            session.entityIdToNpc().put(state.entityId, npc.id());
        } catch (Exception e) {
            LOGGER.warn("Failed to send NPC '{}' to player: {}", npc.id(), e.getMessage());
        }
    }

    public void despawn(ServerPlayer player, NpcDefinition npc) {
        NpcRenderState state = renderStates.get(npc.id());
        if (state == null) return;

        NpcViewerSession session = viewerService.getSession(player.getUUID());
        if (session == null) return;

        session.visibleNpcIds().remove(npc.id());
        session.entityIdToNpc().remove(state.entityId);

        packetSender.removeEntities(player, state.entityId);
    }

    public void sendLookUpdate(ServerPlayer player, NpcDefinition npc, float bodyYaw, float headYaw, float pitch,
                                NpcViewerSession.NpcViewState vs) {
        NpcRenderState state = renderStates.get(npc.id());
        if (state == null) return;

        if (vs.lastYaw() == bodyYaw && vs.lastHeadYaw() == headYaw && vs.lastPitch() == pitch) return;

        try {
            double x = npc.location().x();
            double y = npc.location().y();
            double z = npc.location().z();
            packetSender.rotateHead(player, state.entityId, headYaw);
            packetSender.teleportEntity(player, state.entityId, x, y, z, bodyYaw, pitch);

            vs.setLastYaw(bodyYaw);
            vs.setLastHeadYaw(headYaw);
            vs.setLastPitch(pitch);
        } catch (Exception e) {
            LOGGER.debug("Failed to send look update for NPC '{}': {}", npc.id(), e.getMessage());
        }
    }

    public void resetLook(ServerPlayer player, NpcDefinition npc, NpcViewerSession.NpcViewState vs) {
        float baseYaw = npc.location().yaw();
        float basePitch = npc.location().pitch();
        if (vs.lastYaw() == baseYaw && vs.lastHeadYaw() == baseYaw && vs.lastPitch() == basePitch) return;
        sendLookUpdate(player, npc, baseYaw, baseYaw, basePitch, vs);
    }

    public int visibleNpcCount(ServerPlayer player) {
        NpcViewerSession session = viewerService.getSession(player.getUUID());
        return session != null ? session.visibleNpcIds().size() : 0;
    }

    private static java.util.UUID deriveUuid(String npcId) {
        return java.util.UUID.nameUUIDFromBytes(("bigbang-npc:" + npcId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static final class NpcRenderState {
        private final int entityId;
        private NpcDefinition definition;

        NpcRenderState(int entityId, NpcDefinition definition) {
            this.entityId = entityId;
            this.definition = definition;
        }

        public int entityId() { return entityId; }
        public NpcDefinition definition() { return definition; }
    }
}
