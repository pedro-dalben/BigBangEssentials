package com.pedrodalben.bigbangessentials.npcs.render;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Sends the vanilla 1.21.1 packets that render a virtual NPC to a viewer.
 * Implementations are thin wrappers over {@link NpcPacketBridge}.
 */
public interface NpcPacketSender {

    /** Registers the NPC profile (with skin textures) in the viewer's player info. */
    void addPlayerInfo(ServerPlayer viewer, UUID uuid, String displayName,
                       String textureValue, String textureSignature);

    /** Removes the NPC profile from the viewer's player info (despawn cleanup). */
    void removePlayerInfo(ServerPlayer viewer, UUID uuid);

    /** Spawns the NPC entity (EntityType.PLAYER) for the viewer. */
    void spawnEntity(ServerPlayer viewer, int entityId, UUID uuid,
                     double x, double y, double z, float yaw, float pitch);

    /** Sets the skin layers (second layer / cape / etc.) for the NPC entity. */
    void setSkinLayers(ServerPlayer viewer, int entityId, byte mask);

    /** Removes the NPC entity from the viewer. */
    void removeEntities(ServerPlayer viewer, int entityId);

    /** Rotates the NPC head independently of the body. */
    void rotateHead(ServerPlayer viewer, int entityId, float yaw);

    /** Rotates the NPC body yaw and pitch (look-at support). */
    void rotateBody(ServerPlayer viewer, int entityId, float yaw, float pitch);
}
