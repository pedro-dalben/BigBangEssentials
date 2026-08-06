package com.pedrodalben.bigbangessentials.npcs.render;

import net.minecraft.server.level.ServerPlayer;

public interface NpcPacketSender {
    void addPlayerInfo(ServerPlayer viewer, java.util.UUID uuid, String displayName,
                       String textureValue, String textureSignature);

    void removePlayerInfo(ServerPlayer viewer, java.util.UUID uuid, String displayName,
                          String textureValue, String textureSignature);

    void removeEntities(ServerPlayer viewer, int entityId);

    void rotateHead(ServerPlayer viewer, int entityId, float yaw);

    void teleportEntity(ServerPlayer viewer, int entityId, double x, double y, double z,
                        float yaw, float pitch);
}
