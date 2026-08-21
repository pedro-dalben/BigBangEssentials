package com.pedrodalben.bigbangessentials.npcs.render;

import com.mojang.authlib.GameProfile;
import com.pedrodalben.bigbangessentials.npcs.api.*;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCache;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCacheEntry;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinResolver;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

class NpcRenderServiceTest {

    @BeforeAll
    static void bootStrapMinecraft() throws Exception {
        com.pedrodalben.bigbangessentials.npcs.MinecraftTestBootstrap.bootStrap();
        // Mockito's inline mock maker cannot retransform the NeoForge-patched
        // Minecraft classes in a plain JUnit JVM ("class redefinition failed:
        // invalid class"). The spawn/reskin/despawn packet flow is validated
        // manually on a real server; in CI these tests are skipped.
        try {
            MinecraftServer server = mock(MinecraftServer.class);
            ServerPlayer player = mock(ServerPlayer.class);
            Level level = mock(Level.class);
            when(player.level()).thenReturn(level);
            when(player.getServer()).thenReturn(server);
        } catch (Throwable t) {
            Assumptions.abort("Minecraft classes are not mockable in this JVM; NPC render runtime is validated manually");
        }
    }

    static final class FakeResolver implements SkinResolver {
        final ConcurrentHashMap<String, SkinCacheEntry> results = new ConcurrentHashMap<>();
        final AtomicInteger calls = new AtomicInteger();
        volatile CountDownLatch blockLatch;
        volatile boolean resolvedBlocked;

        void queue(String playerName, String texture) {
            results.put(SkinCache.normalize(playerName), SkinCacheEntry.resolved(
                SkinCache.normalize(playerName), playerName,
                "11111111-1111-1111-1111-111111111111", texture, "sig", "default", 3600_000L));
        }

        @Override
        public SkinCacheEntry resolve(String playerName) {
            calls.incrementAndGet();
            CountDownLatch latch = blockLatch;
            if (latch != null) {
                resolvedBlocked = true;
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            SkinCacheEntry entry = results.get(SkinCache.normalize(playerName));
            return entry != null ? entry : SkinCacheEntry.negative(SkinCache.normalize(playerName), 600_000);
        }
    }

    static final class RecordingSender implements NpcPacketSender {
        final java.util.List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override public void addPlayerInfo(ServerPlayer viewer, UUID uuid, String displayName, String textureValue, String textureSignature) { calls.add("addPlayerInfo"); }
        @Override public void removePlayerInfo(ServerPlayer viewer, UUID uuid) { calls.add("removePlayerInfo"); }
        @Override public void spawnEntity(ServerPlayer viewer, int entityId, UUID uuid, double x, double y, double z, float yaw, float pitch) { calls.add("spawnEntity"); }
        @Override public void setSkinLayers(ServerPlayer viewer, int entityId, byte mask) { calls.add("setSkinLayers"); }
        @Override public void removeEntities(ServerPlayer viewer, int entityId) { calls.add("removeEntities"); }
        @Override public void rotateHead(ServerPlayer viewer, int entityId, float yaw) { calls.add("rotateHead"); }
        @Override public void rotateBody(ServerPlayer viewer, int entityId, float yaw, float pitch) { calls.add("rotateBody"); }
    }

    private static NpcDefinition npcDef() {
        NpcLocation loc = new NpcLocation(ResourceLocation.parse("minecraft:overworld"), 100, 64, 200, 0, 0);
        return new NpcDefinition("test_npc", true, "Test NPC", loc, NpcSkin.unresolved("Dalbesmr"),
            NpcAction.none(), NpcHologramConfig.disabled(), NpcLookSettings.defaults(), 48.0, 56.0,
            NpcInteractionConfig.defaults());
    }

    private static MinecraftServer inlineServer() {
        MinecraftServer server = mock(MinecraftServer.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(server).execute(any(Runnable.class));
        return server;
    }

    private static ServerPlayer mockPlayer(MinecraftServer server) {
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getGameProfile()).thenReturn(new GameProfile(uuid, "Pedro"));
        Level level = mock(Level.class);
        when(level.dimension()).thenReturn(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse("minecraft:overworld")));
        when(player.level()).thenReturn(level);
        when(player.getServer()).thenReturn(server);
        when(player.distanceToSqr(anyDouble(), anyDouble(), anyDouble())).thenReturn(1.0);
        return player;
    }

    @Test
    void spawnWithCachedSkinSendsFullSequenceAndMarksVisible() {
        FakeResolver resolver = new FakeResolver();
        SkinCache skinCache = new SkinCache(resolver, 24, 30, 10, 2, null);
        skinCache.seedForTest("Dalbesmr", SkinCacheEntry.resolved(
            "dalbesmr", "Dalbesmr", "11111111-1111-1111-1111-111111111111",
            "cached-tex", "sig", "default", 3600_000L));
        try {
            NpcViewerService viewers = new NpcViewerService();
            RecordingSender sender = new RecordingSender();
            NpcRenderService render = new NpcRenderService(viewers, skinCache, sender);
            NpcDefinition npc = npcDef();
            render.register(npc);

            ServerPlayer player = mockPlayer(inlineServer());
            viewers.createSession(player.getUUID());

            render.spawn(player, npc);

            assertEquals(List.of("addPlayerInfo", "spawnEntity", "setSkinLayers", "rotateHead"), sender.calls);
            NpcViewerSession session = viewers.getSession(player.getUUID());
            assertNotNull(session);
            assertTrue(session.visibleNpcIds().contains("test_npc"));
            assertTrue(session.entityIdToNpc().values().contains("test_npc"), "entity id must be mapped to the npc");
            assertTrue(session.getState("test_npc").isVisible());
            assertEquals("cached-tex", session.getState("test_npc").skinHash());
        } finally {
            skinCache.shutdown();
        }
    }

    @Test
    void duplicateSpawnSendsPacketsOnlyOnce() {
        FakeResolver resolver = new FakeResolver();
        SkinCache skinCache = new SkinCache(resolver, 24, 30, 10, 2, null);
        skinCache.seedForTest("Dalbesmr", SkinCacheEntry.resolved(
            "dalbesmr", "Dalbesmr", "11111111-1111-1111-1111-111111111111",
            "tex", "sig", "default", 3600_000L));
        try {
            NpcViewerService viewers = new NpcViewerService();
            RecordingSender sender = new RecordingSender();
            NpcRenderService render = new NpcRenderService(viewers, skinCache, sender);
            NpcDefinition npc = npcDef();
            render.register(npc);

            ServerPlayer player = mockPlayer(inlineServer());
            viewers.createSession(player.getUUID());

            render.spawn(player, npc);
            render.spawn(player, npc); // must be rejected by the state machine

            long playerInfoAdds = sender.calls.stream().filter("addPlayerInfo"::equals).count();
            long entitySpawns = sender.calls.stream().filter("spawnEntity"::equals).count();
            assertEquals(1, playerInfoAdds, "second spawn must not re-send player info");
            assertEquals(1, entitySpawns, "second spawn must not re-spawn the entity");
        } finally {
            skinCache.shutdown();
        }
    }

    @Test
    void slowSkinResolvesToFallbackThenReskinsWhenAvailable() throws Exception {
        FakeResolver resolver = new FakeResolver();
        resolver.blockLatch = new CountDownLatch(1);
        SkinCache skinCache = new SkinCache(resolver, 24, 30, 10, 2, null);
        try {
            NpcViewerService viewers = new NpcViewerService();
            RecordingSender sender = new RecordingSender();
            NpcRenderService render = new NpcRenderService(viewers, skinCache, sender);
            NpcDefinition npc = npcDef();
            render.register(npc);

            ServerPlayer player = mockPlayer(inlineServer());
            viewers.createSession(player.getUUID());

            render.spawn(player, npc);

            // No skin available yet → the NPC spawns with the fallback skin.
            assertEquals(List.of("spawnEntity", "setSkinLayers", "rotateHead"), sender.calls,
                "NPC must be visible even while the skin is unresolved");
            NpcViewerSession session = viewers.getSession(player.getUUID());
            assertTrue(session.getState("test_npc").isVisible());
            assertEquals("", session.getState("test_npc").skinHash());

            // The skin resolves → the NPC is re-skinned.
            resolver.queue("Dalbesmr", "real-tex");
            resolver.blockLatch.countDown();

            awaitSkin(sender);
            assertTrue(sender.calls.contains("removePlayerInfo"), "reskin must drop the old player info");
            assertTrue(sender.calls.contains("addPlayerInfo"), "reskin must re-add player info with textures");
            assertTrue(sender.calls.contains("removeEntities"), "reskin must re-create the entity");
            assertEquals("real-tex", session.getState("test_npc").skinHash());
        } finally {
            skinCache.shutdown();
        }
    }

    @Test
    void despawnCleansUpEntityAndPlayerInfo() {
        FakeResolver resolver = new FakeResolver();
        resolver.queue("Dalbesmr", "tex");
        SkinCache skinCache = new SkinCache(resolver, 24, 30, 10, 2, null);
        try {
            NpcViewerService viewers = new NpcViewerService();
            RecordingSender sender = new RecordingSender();
            NpcRenderService render = new NpcRenderService(viewers, skinCache, sender);
            NpcDefinition npc = npcDef();
            render.register(npc);

            ServerPlayer player = mockPlayer(inlineServer());
            viewers.createSession(player.getUUID());
            render.spawn(player, npc);
            sender.calls.clear();

            render.despawn(player, npc);
            assertTrue(sender.calls.contains("removeEntities"));
            assertTrue(sender.calls.contains("removePlayerInfo"));
            assertFalse(viewers.getSession(player.getUUID()).visibleNpcIds().contains("test_npc"));
            assertFalse(viewers.getSession(player.getUUID()).getState("test_npc").isVisible());
        } finally {
            skinCache.shutdown();
        }
    }

    private static void awaitSkin(RecordingSender sender) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!sender.calls.contains("addPlayerInfo") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(sender.calls.contains("addPlayerInfo"), "reskin should happen within the timeout");
    }
}
