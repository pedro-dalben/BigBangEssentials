package com.pedrodalben.bigbangessentials.tablist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.config.TablistConfigValidator;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TablistConfigTest {

    private static final Gson GSON = new GsonBuilder().create();

    @BeforeAll
    static void beforeAll() {
        try { Bootstrap.bootStrap(); } catch (Throwable ignored) {}
    }

    @Test
    void defaultConfig_IsValid() {
        TablistConfig config = new TablistConfig();
        assertTrue(TablistConfigValidator.validate(config));
        assertTrue(config.tablist.enabled);
    }

    @Test
    void deserialize_ValidJson() {
        String json = """
                {
                    "_configVersion": 2,
                    "tablist": {
                        "enabled": true,
                        "performance": {
                            "fallbackRefreshTicks": 100,
                            "maxPacketUpdatesPerTick": 250
                        },
                        "headerFooter": {
                            "enabled": true,
                            "designs": [
                                {
                                    "id": "default",
                                    "priority": 0,
                                    "default": true,
                                    "header": ["&6Header"],
                                    "footer": ["&7Footer"]
                                }
                            ]
                        },
                        "playerList": {
                            "enabled": true,
                            "defaultFormat": "{prefix}{name}{suffix}"
                        }
                    }
                }""";
        TablistConfig config = GSON.fromJson(json, TablistConfig.class);
        assertNotNull(config);
        assertEquals(2, config._configVersion);
        assertTrue(config.tablist.enabled);
        assertEquals(100, config.tablist.performance.fallbackRefreshTicks);
        assertTrue(config.tablist.headerFooter.enabled);
        assertEquals(1, config.tablist.headerFooter.designs.size());
        assertEquals("default", config.tablist.headerFooter.designs.get(0).id);
        assertTrue(config.tablist.headerFooter.designs.get(0).isDefault);
        assertEquals("{prefix}{name}{suffix}", config.tablist.playerList.defaultFormat);
    }

    @Test
    void deserialize_WithNullValues_FallsBack() {
        String json = """
                {
                    "_configVersion": 2,
                    "tablist": {
                        "enabled": true,
                        "headerFooter": { "designs": [] },
                        "playerList": { "defaultFormat": "{name}" }
                    }
                }""";
        TablistConfig config = GSON.fromJson(json, TablistConfig.class);
        assertNotNull(config.tablist);
        assertTrue(config.tablist.headerFooter.designs.isEmpty());
        assertNotNull(config.tablist.playerList.groups);
        assertTrue(config.tablist.playerList.groups.isEmpty());
    }

    @Test
    void validator_RejectsNullConfig() {
        assertFalse(TablistConfigValidator.validate(null));
    }

    @Test
    void validator_FixesNegativeRefresh() {
        TablistConfig config = new TablistConfig();
        config.tablist.performance.fallbackRefreshTicks = -5;
        TablistConfigValidator.validate(config);
        assertEquals(100, config.tablist.performance.fallbackRefreshTicks);
    }

    @Test
    void validator_FixesNegativeMaxUpdates() {
        TablistConfig config = new TablistConfig();
        config.tablist.performance.maxPacketUpdatesPerTick = -1;
        TablistConfigValidator.validate(config);
        assertEquals(250, config.tablist.performance.maxPacketUpdatesPerTick);
    }

    @Test
    void deserialize_DesignSectionDefault() {
        String json = """
                {
                    "_configVersion": 2,
                    "tablist": {
                        "headerFooter": {
                            "designs": [
                                {"id": "a", "priority": 1, "default": false, "header": [], "footer": []},
                                {"id": "b", "priority": 2, "default": true, "header": [], "footer": []}
                            ]
                        },
                        "playerList": { "defaultFormat": "{name}" }
                    }
                }""";
        TablistConfig config = GSON.fromJson(json, TablistConfig.class);
        assertEquals(2, config.tablist.headerFooter.designs.size());
        assertFalse(config.tablist.headerFooter.designs.get(0).isDefault);
        assertTrue(config.tablist.headerFooter.designs.get(1).isDefault);
    }

    @Test
    void afkConfig_Defaults() {
        TablistConfig config = new TablistConfig();
        assertTrue(config.tablist.afk.enabled);
        assertEquals(" &7[AFK]", config.tablist.afk.format);
        assertTrue(config.tablist.afk.sortLast);
    }
}
