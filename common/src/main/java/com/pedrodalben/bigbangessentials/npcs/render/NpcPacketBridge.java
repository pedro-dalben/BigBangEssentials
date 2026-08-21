package com.pedrodalben.bigbangessentials.npcs.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
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
 * Builds the vanilla Minecraft 1.21.1 packets needed to render a virtual player
 * (NPC) on the client, without creating a real {@link net.minecraft.server.level.ServerPlayer}.
 *
 * <p>The two packets that have no public constructor accepting raw data
 * ({@link ClientboundPlayerInfoUpdatePacket} with a list of {@code Entry}
 * records, and {@link ClientboundRotateHeadPacket}) are built by writing the
 * exact wire format with the public {@link FriendlyByteBuf} APIs and decoding
 * through the vanilla {@code STREAM_CODEC} — the same codec the client uses.
 * This is loader-safe (works on Fabric's remapped runtime and NeoForge) and is
 * verified by {@code NpcPacketBridgeTest}.</p>
 *
 * <p>The only reflective lookup is the entity-data index of
 * {@link Player#DATA_PLAYER_MODE_CUSTOMISATION} (skin layers). On remapped
 * runtimes the field name is unavailable, so the canonical 1.21.1 index is used
 * as a verified fallback. See {@link #resolveSkinLayersDataId()}.</p>
 */
public final class NpcPacketBridge {

    /**
     * Verified 1.21.1 data index of {@code Player.DATA_PLAYER_MODE_CUSTOMISATION}:
     * Entity has 8 data fields (0–7), LivingEntity 7 (8–14), so Player starts
     * at 15 — absorption 15, score 16, customisation 17, main hand 18. Verified
     * at runtime against both the vanilla 1.21.1 merged jar (Fabric) and
     * neoforge-21.1.179. Used only on remapped runtimes (Fabric production)
     * where the field name is obfuscated and reflection cannot resolve it.
     */
    private static final int FALLBACK_SKIN_LAYERS_DATA_ID = 17;

    private static final int PLAYER_MODEL_CUSTOMISATION_DATA_ID = resolveSkinLayersDataId();

    private NpcPacketBridge() {
    }

    // ------------------------------------------------------------------
    // Player info (skin) — ADD_PLAYER
    // ------------------------------------------------------------------

    /**
     * Builds the {@code ADD_PLAYER} packet that registers the NPC profile with
     * the client, which is required <em>before</em> {@link #addEntity} so the
     * client can create a {@code RemotePlayer} with the NPC skin.
     *
     * <p>A bare {@code ADD_PLAYER} (without {@code UPDATE_LISTED}) never adds
     * the NPC to the client tab list — this is how the NPC stays out of TAB
     * while its skin keeps working.</p>
     */
    public static ClientboundPlayerInfoUpdatePacket addPlayerInfo(UUID profileId, GameProfile profile) {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
            profileId, profile, true, 0, GameType.CREATIVE, null, null);
        return buildPlayerInfo(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), List.of(entry));
    }

    /**
     * Serializes a player-info update exactly as vanilla's {@code write()}
     * does and decodes it through the vanilla codec. Only the actions this
     * module emits are supported (ADD_PLAYER, UPDATE_LISTED); unknown actions
     * write no extra data and would produce a malformed packet, so callers must
     * restrict themselves to the supported set.
     */
    static ClientboundPlayerInfoUpdatePacket buildPlayerInfo(
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions,
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(Unpooled.buffer()), RegistryAccess.EMPTY);
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
                        // INITIALIZE_CHAT / UPDATE_DISPLAY_NAME are never emitted
                        // by this module; writing them requires state this bridge
                        // does not carry, so they are intentionally unsupported.
                    }
                }
            }
        });
        return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
    }

    private static void writeProperties(FriendlyByteBuf buffer, PropertyMap properties) {
        buffer.writeVarInt(properties.size());
        for (Property property : properties.values()) {
            Utf8String.write(buffer, property.name(), 64);
            Utf8String.write(buffer, property.value(), 32767);
            buffer.writeNullable(property.signature(), (buf, signature) -> Utf8String.write(buf, signature, 1024));
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
        return ClientboundRotateHeadPacket.STREAM_CODEC.decode(buffer);
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
                && dataAccessor.serializer() == EntityDataSerializers.BYTE) {
                return dataAccessor.id();
            }
        } catch (Throwable ignored) {
            // Remapped runtimes (Fabric production) obfuscate field names; fall back.
        }
        return FALLBACK_SKIN_LAYERS_DATA_ID;
    }
}
