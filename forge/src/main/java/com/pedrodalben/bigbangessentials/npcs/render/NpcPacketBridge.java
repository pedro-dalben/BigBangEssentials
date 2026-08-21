package com.pedrodalben.bigbangessentials.npcs.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the vanilla Minecraft 1.20.1 packets needed to render a virtual player
 * (NPC) on the client, without creating a real {@link net.minecraft.server.level.ServerPlayer}.
 */
public final class NpcPacketBridge {

    private static final int FALLBACK_SKIN_LAYERS_DATA_ID = 17;

    private static final int PLAYER_MODEL_CUSTOMISATION_DATA_ID = resolveSkinLayersDataId();

    private NpcPacketBridge() {
    }

    // ------------------------------------------------------------------
    // Player info (skin) — ADD_PLAYER
    // ------------------------------------------------------------------

    public static ClientboundPlayerInfoUpdatePacket addPlayerInfo(UUID profileId, GameProfile profile) {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
            profileId, profile, true, 0, GameType.CREATIVE, null, null);
        return buildPlayerInfo(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), List.of(entry));
    }

    static ClientboundPlayerInfoUpdatePacket buildPlayerInfo(
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions,
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnumSet(actions, ClientboundPlayerInfoUpdatePacket.Action.class);
        buffer.writeCollection(entries, (buf, entry) -> {
            buf.writeUUID(entry.profileId());
            for (ClientboundPlayerInfoUpdatePacket.Action action : actions) {
                switch (action) {
                    case ADD_PLAYER -> {
                        GameProfile profile = Objects.requireNonNull(entry.profile());
                        buf.writeUtf(profile.getName(), 16);
                        writeProperties(buf, profile.getProperties());
                    }
                    case UPDATE_LISTED -> buf.writeBoolean(entry.listed());
                    case UPDATE_GAME_MODE -> buf.writeVarInt(entry.gameMode().getId());
                    case UPDATE_LATENCY -> buf.writeVarInt(entry.latency());
                    default -> {
                    }
                }
            }
        });
        return new ClientboundPlayerInfoUpdatePacket(buffer);
    }

    private static void writeProperties(FriendlyByteBuf buffer, PropertyMap properties) {
        buffer.writeVarInt(properties.size());
        for (Property property : properties.values()) {
            buffer.writeUtf(property.getName(), 64);
            buffer.writeUtf(property.getValue(), 32767);
            buffer.writeNullable(property.getSignature(), (buf, signature) -> buf.writeUtf(signature, 1024));
        }
    }

    // ------------------------------------------------------------------
    // Entity spawn / despawn / metadata
    // ------------------------------------------------------------------

    public static ClientboundAddEntityPacket addEntity(int entityId, UUID uuid,
                                                       double x, double y, double z,
                                                       float yaw, float pitch) {
        return new ClientboundAddEntityPacket(
            entityId, uuid, x, y, z,
            pitch,        // xRot
            yaw,          // yRot
            EntityType.PLAYER,
            0,
            Vec3.ZERO,
            yaw);         // yHeadRot — head starts aligned with body
    }

    public static ClientboundSetEntityDataPacket skinLayers(int entityId, byte mask) {
        return new ClientboundSetEntityDataPacket(entityId,
            List.of(new SynchedEntityData.DataValue<>(
                PLAYER_MODEL_CUSTOMISATION_DATA_ID, EntityDataSerializers.BYTE, mask)));
    }

    public static ClientboundRemoveEntitiesPacket removeEntities(int entityId) {
        return new ClientboundRemoveEntitiesPacket(new int[]{entityId});
    }

    public static ClientboundPlayerInfoRemovePacket removePlayerInfo(UUID uuid) {
        return new ClientboundPlayerInfoRemovePacket(List.of(uuid));
    }

    // ------------------------------------------------------------------
    // Rotation
    // ------------------------------------------------------------------

    public static ClientboundRotateHeadPacket rotateHead(int entityId, float yaw) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(entityId);
        buffer.writeByte((byte) (int) (yaw * 256.0F / 360.0F));
        return new ClientboundRotateHeadPacket(buffer);
    }

    public static ClientboundMoveEntityPacket.Rot rotateBody(int entityId, float yaw, float pitch) {
        return new ClientboundMoveEntityPacket.Rot(entityId,
            (byte) (int) (yaw * 256.0F / 360.0F),
            (byte) (int) (pitch * 256.0F / 360.0F),
            true);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private static int resolveSkinLayersDataId() {
        try {
            Field field = Player.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
            field.setAccessible(true);
            Object accessor = field.get(null);
            if (accessor instanceof EntityDataAccessor<?> dataAccessor
                && dataAccessor.getSerializer() == EntityDataSerializers.BYTE) {
                return dataAccessor.getId();
            }
        } catch (Throwable ignored) {
        }
        return FALLBACK_SKIN_LAYERS_DATA_ID;
    }
}
