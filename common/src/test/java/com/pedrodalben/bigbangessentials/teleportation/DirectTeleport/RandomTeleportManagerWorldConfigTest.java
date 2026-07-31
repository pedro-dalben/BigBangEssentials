package com.pedrodalben.bigbangessentials.teleportation.DirectTeleport;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RandomTeleportManagerWorldConfigTest {

    @Test
    void normalizesAliasesAndFullIds() {
        assertEquals("minecraft:overworld", RandomTeleportManager.normalizeWorldId(" OVERWORLD "));
        assertEquals("minecraft:the_nether", RandomTeleportManager.normalizeWorldId("nether"));
        assertEquals("minecraft:the_end", RandomTeleportManager.normalizeWorldId("end"));
        assertEquals("bigbangcraft:minerar", RandomTeleportManager.normalizeWorldId("bigbangcraft:minerar"));
        assertNull(RandomTeleportManager.normalizeWorldId("not a world id"));
    }

    @Test
    void absentOrEmptyListKeepsCurrentWorld() {
        assertNull(RandomTeleportManager.normalizeConfiguredWorlds(null));
        assertNull(RandomTeleportManager.normalizeConfiguredWorlds(new JsonArray()));
        assertEquals("minecraft:nether", RandomTeleportManager.selectConfiguredWorld(
                "minecraft:nether", null, Set.of("minecraft:nether")));
    }

    @Test
    void currentWorldWinsThenFirstLoadedConfiguredWorldIsUsed() {
        Set<String> loaded = Set.of("minecraft:overworld", "bigbangcraft:minerar");
        assertEquals("minecraft:overworld", RandomTeleportManager.selectConfiguredWorld(
                "minecraft:overworld", List.of("bigbangcraft:minerar", "minecraft:overworld"), loaded));
        assertEquals("bigbangcraft:minerar", RandomTeleportManager.selectConfiguredWorld(
                "minecraft:the_end", List.of("missing:world", "bigbangcraft:minerar"), loaded));
        assertNull(RandomTeleportManager.selectConfiguredWorld(
                "minecraft:the_end", List.of("missing:world"), loaded));
    }

    @Test
    void cacheKeyIncludesWorldAndLocationName() {
        assertNotEquals(RandomTeleportManager.cacheKey("minecraft:overworld", "default"),
                RandomTeleportManager.cacheKey("bigbangcraft:minerar", "default"));
        assertNotEquals(RandomTeleportManager.cacheKey("minecraft:overworld", "default"),
                RandomTeleportManager.cacheKey("minecraft:overworld", "mine"));
        assertEquals(List.of("minecraft:overworld", "bigbangcraft:minerar"),
                RandomTeleportManager.normalizeConfiguredWorlds(JsonParser.parseString(
                        "[\"overworld\", \"bigbangcraft:minerar\", \"overworld\"]").getAsJsonArray()));
    }
}
