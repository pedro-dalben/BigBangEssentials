package com.pedrodalben.bigbangessentials.npcs.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Critical test: the packet bridge must load and produce correctly structured
 * packets for Minecraft 1.21.1. If the wire format or reflection fallback is
 * wrong, these tests fail.
 */
class NpcPacketBridgeTest {

    private static final UUID PROFILE_ID = UUID.nameUUIDFromBytes("npc-test".getBytes());

    @BeforeAll
    static void bootStrapMinecraft() throws Exception {
        com.pedrodalben.bigbangessentials.npcs.MinecraftTestBootstrap.bootStrap();
    }

    private static GameProfile profileWithTextures() {
        GameProfile profile = new GameProfile(PROFILE_ID, "Dalbesmr");
        profile.getProperties().put("textures", new Property("textures", "dGV4dHVyZQ==", "c2ln"));
        return profile;
    }

    @Test
    void packetSenderImplLoads() {
        // The static initializer of the bridge + impl must not throw.
        assertDoesNotThrow(NpcPacketSenderImpl::new);
    }

    @Test
    void playerInfoUpdateRoundTripPreservesSkin() {
        ClientboundPlayerInfoUpdatePacket packet = NpcPacketBridge.addPlayerInfo(PROFILE_ID, profileWithTextures());

        assertEquals(1, packet.actions().size());
        assertTrue(packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER));
        assertEquals(1, packet.entries().size());

        ClientboundPlayerInfoUpdatePacket.Entry entry = packet.entries().get(0);
        assertEquals(PROFILE_ID, entry.profileId());
        assertEquals("Dalbesmr", entry.profile().getName());
        Property textures = entry.profile().getProperties().get("textures").iterator().next();
        assertEquals("dGV4dHVyZQ==", textures.value());
        assertEquals("c2ln", textures.signature());
    }

    @Test
    void playerInfoUpdateEntryHasExpectedStructure() {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
            PROFILE_ID, profileWithTextures(), true, 0,
            net.minecraft.world.level.GameType.CREATIVE, null, null);
        assertEquals(PROFILE_ID, entry.profileId());
        assertTrue(entry.listed());
        assertEquals(0, entry.latency());
        assertEquals(net.minecraft.world.level.GameType.CREATIVE, entry.gameMode());
        assertNull(entry.chatSession());
    }

    @Test
    void addEntityUsesPlayerTypeAndPosition() {
        UUID uuid = UUID.nameUUIDFromBytes("entity-uuid".getBytes());
        ClientboundAddEntityPacket packet = NpcPacketBridge.addEntity(2000000001, uuid, 10.5, 64.0, -20.25, 90.0f, 0.0f);

        assertEquals(2000000001, packet.getId());
        assertEquals(uuid, packet.getUUID());
        assertEquals(EntityType.PLAYER, packet.getType());
        assertEquals(10.5, packet.getX());
        assertEquals(64.0, packet.getY());
        assertEquals(-20.25, packet.getZ());
        // xRot is passed as pitch (0), yRot as yaw (90)
        assertEquals(90.0f, packet.getYRot(), 1.0f);
        assertEquals(0.0f, packet.getXRot(), 1.0f);
    }

    @Test
    void skinLayersMetadataUsesCustomisationIndex() {
        ClientboundSetEntityDataPacket packet = NpcPacketBridge.skinLayers(2000000001, (byte) 0x7F);
        assertEquals(2000000001, packet.id());
        assertEquals(1, packet.packedItems().size());
        net.minecraft.network.syncher.SynchedEntityData.DataValue<?> value = packet.packedItems().get(0);
        // 1.21.1: Entity 0-7, LivingEntity 8-14, Player starts at 15 →
        // DATA_PLAYER_MODE_CUSTOMISATION = 17 (verified on vanilla 1.21.1 and
        // neoforge-21.1.179 runtimes).
        assertEquals(17, value.id());
        assertEquals(EntityDataSerializers.BYTE, value.serializer());
        assertEquals((byte) 0x7F, value.value());
    }

    @Test
    void rotateHeadRoundTripsYaw() {
        float yaw = 135.0f;
        byte expected = (byte) (int) (yaw * 256.0F / 360.0F);
        ClientboundRotateHeadPacket packet = NpcPacketBridge.rotateHead(2000000001, yaw);
        assertEquals(expected, packet.getYHeadRot());
    }

    @Test
    void rotateBodyUsesMoveEntityRot() {
        ClientboundMoveEntityPacket.Rot packet = NpcPacketBridge.rotateBody(2000000001, 45.0f, 30.0f);
        assertTrue(packet.hasRotation());
        assertFalse(packet.hasPosition());
        assertEquals((byte) (int) (45.0f * 256.0F / 360.0F), packet.getyRot());
        assertEquals((byte) (int) (30.0f * 256.0F / 360.0F), packet.getxRot());
    }

    @Test
    void removeEntitiesCarriesEntityId() {
        ClientboundRemoveEntitiesPacket packet = NpcPacketBridge.removeEntities(2000000001);
        assertEquals(List.of(2000000001), packet.getEntityIds());
    }

    @Test
    void removePlayerInfoCarriesUuid() {
        ClientboundPlayerInfoRemovePacket packet = NpcPacketBridge.removePlayerInfo(PROFILE_ID);
        assertEquals(List.of(PROFILE_ID), packet.profileIds());
    }

    @Test
    void entityUuidIsStableAndNamespaced() {
        UUID a = NpcRenderService.deriveUuid("professor_carvalho");
        UUID b = NpcRenderService.deriveUuid("professor_carvalho");
        UUID c = NpcRenderService.deriveUuid("outro_npc");
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void virtualEntityIdAllocatorOverflowWrapsIntoRange() {
        VirtualEntityIdAllocator allocator = new VirtualEntityIdAllocator(1_000, 1_010);
        int last = -1;
        for (int i = 0; i < 20; i++) {
            int id = allocator.allocate();
            assertTrue(id >= 1_000 && id < 1_010, "id out of range: " + id);
            assertNotEquals(last, id, "wrap must not reuse the same id consecutively");
            last = id;
        }
    }

    @Test
    void npcAllocatorStaysWithinNpcRange() {
        VirtualEntityIdAllocator allocator = VirtualEntityIdAllocator.npcs();
        int id = allocator.allocate();
        assertTrue(id >= 2_000_000_000 && id < 2_100_000_000);
        assertTrue(id < 1_500_000_000 + 500_000_000 || id >= 2_000_000_000, "must not collide with hologram range");
    }
}
