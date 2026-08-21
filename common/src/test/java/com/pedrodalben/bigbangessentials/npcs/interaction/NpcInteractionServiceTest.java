package com.pedrodalben.bigbangessentials.npcs.interaction;

import com.mojang.authlib.GameProfile;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.npcs.api.*;
import com.pedrodalben.bigbangessentials.npcs.render.NpcRenderService;
import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerService;
import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerSession;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCache;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCacheEntry;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinResolver;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NpcInteractionServiceTest {

    @BeforeAll
    static void bootStrapMinecraft() throws Exception {
        com.pedrodalben.bigbangessentials.npcs.MinecraftTestBootstrap.bootStrap();
    }

    private static final ResourceKey<Level> OVERWORLD =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));

    private static final class NoopResolver implements SkinResolver {
        @Override
        public SkinCacheEntry resolve(String playerName) {
            return SkinCacheEntry.negative(SkinCache.normalize(playerName), 600_000);
        }
    }

    private NpcDefinition npc(String id, NpcInteractionConfig interaction, boolean enabled) {
        NpcLocation loc = new NpcLocation(ResourceLocation.parse("minecraft:overworld"), 100, 64, 200, 0, 0);
        return new NpcDefinition(id, enabled, id, loc, NpcSkin.unresolved("Dalbesmr"),
            NpcAction.none(), NpcHologramConfig.disabled(), NpcLookSettings.disabled(), 48.0, 56.0, interaction);
    }

    private static final class Harness {
        final NpcViewerService viewers = new NpcViewerService();
        final NpcRenderService render;
        final NpcInteractionService service;
        final ServerPlayer player;
        final UUID playerUuid = UUID.randomUUID();

        Harness(NpcDefinition npc, ResourceKey<Level> playerDimension, double distance) {
            SkinCache skinCache = new SkinCache(new NoopResolver(), 24, 30, 10, 2, null);
            render = new NpcRenderService(viewers, skinCache, new com.pedrodalben.bigbangessentials.npcs.render.NpcPacketSender() {
                @Override public void addPlayerInfo(ServerPlayer v, UUID u, String n, String t, String s) {}
                @Override public void removePlayerInfo(ServerPlayer v, UUID u) {}
                @Override public void spawnEntity(ServerPlayer v, int i, UUID u, double x, double y, double z, float yaw, float p) {}
                @Override public void setSkinLayers(ServerPlayer v, int i, byte m) {}
                @Override public void removeEntities(ServerPlayer v, int i) {}
                @Override public void rotateHead(ServerPlayer v, int i, float y) {}
                @Override public void rotateBody(ServerPlayer v, int i, float y, float p) {}
            });
            service = new NpcInteractionService(viewers, render);
            render.register(npc);

            player = mock(ServerPlayer.class);
            when(player.getUUID()).thenReturn(playerUuid);
            when(player.getGameProfile()).thenReturn(new GameProfile(playerUuid, "Pedro"));
            Level level = mock(Level.class);
            when(level.dimension()).thenReturn(playerDimension);
            when(player.level()).thenReturn(level);
            when(player.distanceToSqr(anyDouble(), anyDouble(), anyDouble())).thenReturn(distance * distance);
            when(player.hasPermissions(anyInt())).thenReturn(false);
        }

        Harness withVisibleNpc(int entityId, String npcId) {
            NpcViewerSession session = viewers.createSession(playerUuid);
            session.entityIdToNpc().put(entityId, npcId);
            session.visibleNpcIds().add(npcId);
            return this;
        }
    }

    @Test
    void validClickExecutesAndAppliesCooldown() {
        NpcDefinition npc = npc("npc1", NpcInteractionConfig.defaults(), true);
        Harness h = new Harness(npc, OVERWORLD, 2.0).withVisibleNpc(12345, "npc1");

        assertTrue(h.service.handleClick(h.player, 12345));
        assertFalse(h.service.handleClick(h.player, 12345), "second click within cooldown must be denied");
    }

    @Test
    void unknownEntityIdIsIgnored() {
        NpcDefinition npc = npc("npc1", NpcInteractionConfig.defaults(), true);
        Harness h = new Harness(npc, OVERWORLD, 2.0).withVisibleNpc(12345, "npc1");
        assertFalse(h.service.handleClick(h.player, 99999));
    }

    @Test
    void clickFromAnotherViewerIsIgnored() {
        NpcDefinition npc = npc("npc1", NpcInteractionConfig.defaults(), true);
        Harness h = new Harness(npc, OVERWORLD, 2.0);
        // A different player's session owns the entity id — this player has no session.
        assertFalse(h.service.handleClick(h.player, 12345));
    }

    @Test
    void clickTooFarIsDenied() {
        NpcDefinition npc = npc("npc1", NpcInteractionConfig.defaults(), true); // distance 4.5
        Harness h = new Harness(npc, OVERWORLD, 10.0).withVisibleNpc(12345, "npc1");
        assertFalse(h.service.handleClick(h.player, 12345));
    }

    @Test
    void disabledNpcIsDenied() {
        NpcDefinition npc = npc("npc1", NpcInteractionConfig.defaults(), false);
        Harness h = new Harness(npc, OVERWORLD, 2.0).withVisibleNpc(12345, "npc1");
        assertFalse(h.service.handleClick(h.player, 12345));
    }

    @Test
    void differentDimensionIsDenied() {
        NpcDefinition npc = npc("npc1", NpcInteractionConfig.defaults(), true);
        Harness h = new Harness(npc, NETHER, 2.0).withVisibleNpc(12345, "npc1");
        assertFalse(h.service.handleClick(h.player, 12345));
    }

    @Test
    void zeroCooldownAllowsRepeatedClicks() {
        NpcDefinition npc = npc("npc1", new NpcInteractionConfig(4.5, 0, ""), true);
        Harness h = new Harness(npc, OVERWORLD, 2.0).withVisibleNpc(12345, "npc1");
        assertTrue(h.service.handleClick(h.player, 12345));
        assertTrue(h.service.handleClick(h.player, 12345), "cooldown 0 must allow repeated clicks");
    }

    @Test
    void permissionRequiredDeniesWithoutPermission() {
        NpcDefinition npc = npc("npc1", new NpcInteractionConfig(4.5, 750, "bigbangessentials.npcs.use"), true);
        Harness h = new Harness(npc, OVERWORLD, 2.0).withVisibleNpc(12345, "npc1");

        try (MockedStatic<PermissionAPI> mocked = mockStatic(PermissionAPI.class)) {
            mocked.when(() -> PermissionAPI.hasPermission(any(), anyString())).thenReturn(false);
            assertFalse(h.service.handleClick(h.player, 12345));
        }
    }

    @Test
    void opBypassesPermissionRequirement() {
        NpcDefinition npc = npc("npc1", new NpcInteractionConfig(4.5, 750, "bigbangessentials.npcs.use"), true);
        Harness h = new Harness(npc, OVERWORLD, 2.0).withVisibleNpc(12345, "npc1");
        when(h.player.hasPermissions(4)).thenReturn(true);

        assertTrue(h.service.handleClick(h.player, 12345));
    }
}
