package com.pedrodalben.bigbangessentials.npcs;

import com.pedrodalben.bigbangessentials.npcs.api.*;
import com.pedrodalben.bigbangessentials.npcs.config.NpcConfig;
import com.pedrodalben.bigbangessentials.npcs.config.NpcConfigStore;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCache;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCacheEntry;
import com.pedrodalben.bigbangessentials.npcs.skin.MojangSkinResolver;
import com.pedrodalben.bigbangessentials.npcs.spatial.NpcSpatialIndex;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NpcModuleTest {

    // ─── NpcDefinition ID Validation ────────────────────────────────

    @Test
    @DisplayName("should normalize id to lowercase")
    void normalizeIdLowercase() {
        assertEquals("warp_end", NpcDefinition.normalizeId("WARP_END"));
        assertEquals("shop_npc", NpcDefinition.normalizeId("Shop_NPC"));
    }

    @Test
    @DisplayName("should reject invalid id characters")
    void rejectInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.normalizeId("npc name"));
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.normalizeId("npc.name"));
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.normalizeId(null));
    }

    @Test
    @DisplayName("should accept valid ids")
    void acceptValidIds() {
        assertEquals("warp_end", NpcDefinition.normalizeId("warp_end"));
        assertEquals("shop", NpcDefinition.normalizeId("shop"));
        assertEquals("test-npc-01", NpcDefinition.normalizeId("test-npc-01"));
    }

    // ─── NpcAction ───────────────────────────────────────────────────

    @Test
    @DisplayName("should strip leading slash")
    void stripSlash() {
        NpcAction action = NpcAction.playerCommand("/warp end");
        assertEquals("warp end", action.command());
        assertEquals(NpcActionType.PLAYER_COMMAND, action.type());
    }

    @Test
    @DisplayName("should preserve command without slash")
    void preserveNoSlash() {
        NpcAction action = NpcAction.playerCommand("warp end");
        assertEquals("warp end", action.command());
    }

    @Test
    @DisplayName("should support console command")
    void consoleCommand() {
        NpcAction action = NpcAction.consoleCommand("/give {player} diamond 1");
        assertEquals("give {player} diamond 1", action.command());
        assertEquals(NpcActionType.CONSOLE_COMMAND, action.type());
    }

    @Test
    @DisplayName("should support none action")
    void noneAction() {
        NpcAction action = NpcAction.none();
        assertEquals(NpcActionType.NONE, action.type());
        assertEquals("", action.command());
    }

    // ─── NpcLocation ─────────────────────────────────────────────────

    @Test
    @DisplayName("should preserve location fields")
    void locationFields() {
        NpcLocation loc = new NpcLocation(ResourceLocation.parse("minecraft:overworld"), 100.5, 64.0, -200.3, 90f, 0f);
        assertEquals("minecraft:overworld", loc.dimension().toString());
        assertEquals(100.5, loc.x());
        assertEquals(64.0, loc.y());
        assertEquals(-200.3, loc.z());
        assertEquals(90f, loc.yaw());
        assertEquals(0f, loc.pitch());
    }

    // ─── SkinCache Normalization ─────────────────────────────────────

    @Test
    @DisplayName("should normalize skin names to lowercase trimmed")
    void normalizeSkinNames() {
        assertEquals("dalbesmr", SkinCache.normalize("Dalbesmr"));
        assertEquals("dalbesmr", SkinCache.normalize("dalbesmr"));
        assertEquals("dalbesmr", SkinCache.normalize("DALBESMR"));
        assertEquals("dalbesmr", SkinCache.normalize("  Dalbesmr  "));
        assertEquals("", SkinCache.normalize(null));
    }

    // ─── SkinCacheEntry ──────────────────────────────────────────────

    @Test
    @DisplayName("should detect fresh entry")
    void freshEntry() {
        SkinCacheEntry entry = SkinCacheEntry.resolved("test", "Test", "uuid", "tex", "sig", "default", 3600_000);
        assertTrue(entry.isFresh());
        assertFalse(entry.negative());
    }

    @Test
    @DisplayName("should detect stale entry")
    void staleEntry() {
        SkinCacheEntry entry = new SkinCacheEntry("test", "Test", "uuid", "tex", "sig", "default",
            System.currentTimeMillis() - 86_400_000, System.currentTimeMillis() - 1000, false);
        assertTrue(entry.isStale(30 * 86_400_000L));
    }

    @Test
    @DisplayName("should detect fully expired entry")
    void expiredEntry() {
        SkinCacheEntry entry = new SkinCacheEntry("test", "Test", "uuid", "tex", "sig", "default",
            System.currentTimeMillis() - 31 * 86_400_000L, System.currentTimeMillis() - 30 * 86_400_000L, false);
        assertTrue(entry.isExpired(30 * 86_400_000L));
    }

    @Test
    @DisplayName("should detect negative entry")
    void negativeEntry() {
        SkinCacheEntry entry = SkinCacheEntry.negative("test", 600_000);
        assertTrue(entry.negative());
        assertTrue(entry.isFresh());
    }

    // ─── Mojang UUID formatting ─────────────────────────────────────

    @Test
    @DisplayName("should format UUID with dashes")
    void formatUuid() {
        String result = MojangSkinResolver.formatUuid("1234567890abcdef1234567890abcdef");
        assertEquals("12345678-90ab-cdef-1234-567890abcdef", result);
    }

    // ─── NpcSpatialIndex ─────────────────────────────────────────────

    @Test
    @DisplayName("should find NPCs by chunk proximity")
    void spatialIndexQuery() {
        NpcSpatialIndex index = new NpcSpatialIndex();
        ResourceLocation dim = ResourceLocation.parse("minecraft:overworld");
        NpcLocation loc = new NpcLocation(dim, 100, 64, 200, 0, 0);
        index.add("npc1", loc);

        Set<String> result = index.query(dim, 100, 200, 64);
        assertTrue(result.contains("npc1"));
        assertEquals(1, index.size());
    }

    @Test
    @DisplayName("should not find NPCs in different dimension")
    void spatialIndexDifferentDimension() {
        NpcSpatialIndex index = new NpcSpatialIndex();
        ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld");
        ResourceLocation nether = ResourceLocation.parse("minecraft:the_nether");
        index.add("npc1", new NpcLocation(overworld, 100, 64, 200, 0, 0));

        Set<String> result = index.query(nether, 100, 200, 64);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should remove NPC from index")
    void spatialIndexRemove() {
        NpcSpatialIndex index = new NpcSpatialIndex();
        ResourceLocation dim = ResourceLocation.parse("minecraft:overworld");
        index.add("npc1", new NpcLocation(dim, 100, 64, 200, 0, 0));
        index.remove("npc1");
        assertEquals(0, index.size());
    }

    @Test
    @DisplayName("should find NPC at chunk boundary")
    void spatialIndexChunkBoundary() {
        NpcSpatialIndex index = new NpcSpatialIndex();
        ResourceLocation dim = ResourceLocation.parse("minecraft:overworld");
        index.add("npc1", new NpcLocation(dim, 0, 64, 0, 0, 0));
        index.add("npc2", new NpcLocation(dim, 200, 64, 200, 0, 0));

        Set<String> near = index.query(dim, 10, 10, 32);
        assertTrue(near.contains("npc1"));
        assertFalse(near.contains("npc2"));
    }

    // ─── NpcInteractionConfig ────────────────────────────────────────

    @Test
    @DisplayName("should have empty permission by default")
    void defaultNoPermission() {
        NpcInteractionConfig config = NpcInteractionConfig.defaults();
        assertFalse(config.hasPermission());
        assertEquals(4.5, config.distance());
        assertEquals(750, config.cooldownMillis());
    }

    @Test
    @DisplayName("should recognize permission when set")
    void hasPermission() {
        NpcInteractionConfig config = new NpcInteractionConfig(5.0, 1000, "bigbangessentials.npcs.use");
        assertTrue(config.hasPermission());
        assertEquals("bigbangessentials.npcs.use", config.permission());
    }

    // ─── NpcLookSettings ─────────────────────────────────────────────

    @Test
    @DisplayName("should have default look settings enabled")
    void defaultLookSettings() {
        NpcLookSettings settings = NpcLookSettings.defaults();
        assertTrue(settings.enabled());
        assertEquals(10.0, settings.range());
        assertEquals(4, settings.updateIntervalTicks());
        assertTrue(settings.rotateBody());
    }

    // ─── NpcHologramConfig ──────────────────────────────────────────

    @Test
    @DisplayName("should create default hologram config")
    void defaultHologram() {
        NpcHologramConfig config = NpcHologramConfig.defaults("Test NPC");
        assertTrue(config.enabled());
        assertEquals(1, config.lines().size());
        assertEquals("Test NPC", config.lines().get(0));
    }

    @Test
    @DisplayName("should create disabled hologram config")
    void disabledHologram() {
        NpcHologramConfig config = NpcHologramConfig.disabled();
        assertFalse(config.enabled());
    }

    // ─── NpcConfig ───────────────────────────────────────────────────

    @Test
    @DisplayName("should create default NPC config")
    void defaultConfig() {
        NpcConfig config = NpcConfig.defaults();
        assertEquals(1, config.schemaVersion());
        assertEquals(48.0, config.defaultViewDistance());
        assertEquals(56.0, config.defaultDespawnDistance());
        assertTrue(config.npcs().isEmpty());
    }
}
