package com.pedrodalben.bigbangessentials.npcs.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Sends NPC packets to a viewer using {@link NpcPacketBridge}. No reflection
 * on packet internals: the packets are built with public vanilla APIs (and the
 * vanilla codecs) so they serialize exactly like the vanilla server would.
 */
public class NpcPacketSenderImpl implements NpcPacketSender {

    @Override
    public void addPlayerInfo(ServerPlayer viewer, UUID uuid, String displayName,
                              String textureValue, String textureSignature) {
        GameProfile profile = new GameProfile(uuid, truncate(displayName));
        if (textureValue != null && !textureValue.isEmpty()) {
            profile.getProperties().put("textures",
                new Property("textures", textureValue, textureSignature != null ? textureSignature : ""));
        }
        viewer.connection.send(NpcPacketBridge.addPlayerInfo(uuid, profile));
    }

    @Override
    public void removePlayerInfo(ServerPlayer viewer, UUID uuid) {
        viewer.connection.send(NpcPacketBridge.removePlayerInfo(uuid));
    }

    @Override
    public void spawnEntity(ServerPlayer viewer, int entityId, UUID uuid,
                            double x, double y, double z, float yaw, float pitch) {
        viewer.connection.send(NpcPacketBridge.addEntity(entityId, uuid, x, y, z, yaw, pitch));
    }

    @Override
    public void setSkinLayers(ServerPlayer viewer, int entityId, byte mask) {
        viewer.connection.send(NpcPacketBridge.skinLayers(entityId, mask));
    }

    @Override
    public void removeEntities(ServerPlayer viewer, int entityId) {
        viewer.connection.send(NpcPacketBridge.removeEntities(entityId));
    }

    @Override
    public void rotateHead(ServerPlayer viewer, int entityId, float yaw) {
        viewer.connection.send(NpcPacketBridge.rotateHead(entityId, yaw));
    }

    @Override
    public void rotateBody(ServerPlayer viewer, int entityId, float yaw, float pitch) {
        viewer.connection.send(NpcPacketBridge.rotateBody(entityId, yaw, pitch));
    }

    private static String truncate(String name) {
        if (name == null) return "";
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
