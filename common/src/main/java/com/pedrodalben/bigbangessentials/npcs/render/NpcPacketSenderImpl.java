package com.pedrodalben.bigbangessentials.npcs.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.util.*;

public class NpcPacketSenderImpl implements NpcPacketSender {

    private static final Constructor<?> PACKET_CONSTRUCTOR;

    static {
        try {
            Class<?> entryClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Constructor<?> entryCtor = entryClass.getDeclaredConstructor(
                UUID.class, GameProfile.class, boolean.class, int.class,
                net.minecraft.world.level.GameType.class, Component.class, Object.class);
            entryCtor.setAccessible(true);

            Class<?> packetClass = ClientboundPlayerInfoUpdatePacket.class;
            PACKET_CONSTRUCTOR = packetClass.getDeclaredConstructor(Set.class);
            PACKET_CONSTRUCTOR.setAccessible(true);

            ENTRY_CTOR = entryCtor;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NPC packet sender", e);
        }
    }

    private static final Constructor<?> ENTRY_CTOR;

    @Override
    public void addPlayerInfo(ServerPlayer viewer, UUID uuid, String displayName,
                               String textureValue, String textureSignature) {
        try {
            GameProfile profile = new GameProfile(uuid, displayName);
            if (textureValue != null && !textureValue.isEmpty()) {
                profile.getProperties().put("textures",
                    new Property("textures", textureValue, textureSignature != null ? textureSignature : ""));
            }

            Object entry = ENTRY_CTOR.newInstance(uuid, profile, false, 0,
                net.minecraft.world.level.GameType.CREATIVE, Component.literal(displayName), null);
            @SuppressWarnings("unchecked")
            ClientboundPlayerInfoUpdatePacket packet = (ClientboundPlayerInfoUpdatePacket)
                PACKET_CONSTRUCTOR.newInstance(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), Set.of(entry));
            viewer.connection.send(packet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send player info add for NPC", e);
        }
    }

    @Override
    public void removePlayerInfo(ServerPlayer viewer, UUID uuid, String displayName,
                                  String textureValue, String textureSignature) {
        try {
            GameProfile profile = new GameProfile(uuid, displayName);
            if (textureValue != null && !textureValue.isEmpty()) {
                profile.getProperties().put("textures",
                    new Property("textures", textureValue, textureSignature != null ? textureSignature : ""));
            }

            Object entry = ENTRY_CTOR.newInstance(uuid, profile, true, 0,
                net.minecraft.world.level.GameType.CREATIVE, Component.literal(displayName), null);
            @SuppressWarnings("unchecked")
            ClientboundPlayerInfoUpdatePacket packet = (ClientboundPlayerInfoUpdatePacket)
                PACKET_CONSTRUCTOR.newInstance(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), Set.of(entry));
            viewer.connection.send(packet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove player info for NPC", e);
        }
    }

    @Override
    public void removeEntities(ServerPlayer viewer, int entityId) {
        viewer.connection.send(new ClientboundRemoveEntitiesPacket(new int[]{entityId}));
    }

    @Override
    public void rotateHead(ServerPlayer viewer, int entityId, float yaw) {
        byte yawByte = (byte) ((int) (yaw * 256.0F / 360.0F));
        viewer.connection.send(new ClientboundMoveEntityPacket.Rot(entityId, yawByte, (byte) 0, true));
    }

    @Override
    public void teleportEntity(ServerPlayer viewer, int entityId, double x, double y, double z,
                                float yaw, float pitch) {
        byte yawByte = (byte) ((int) (yaw * 256.0F / 360.0F));
        byte pitchByte = (byte) ((int) (pitch * 256.0F / 360.0F));
        viewer.connection.send(new ClientboundMoveEntityPacket.PosRot(entityId,
            (short) 0, (short) 0, (short) 0,
            yawByte, pitchByte, true));
    }
}
