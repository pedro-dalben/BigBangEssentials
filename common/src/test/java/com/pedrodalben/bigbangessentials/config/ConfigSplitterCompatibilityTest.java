package com.pedrodalben.bigbangessentials.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSplitterCompatibilityTest {
    @Test
    void flatModuleFlagsWinAndLegacyNestedFlagsFillMissingValues() {
        JsonObject modules = JsonParser.parseString("""
            {
              "modules": {"jobsEnabled": false, "teleportationEnabled": false},
              "jobsEnabled": true,
              "rankupEnabled": true
            }
            """).getAsJsonObject();

        JsonObject normalized = ConfigSplitter.normalizeModulesForTest(modules);

        assertTrue(normalized.get("jobsEnabled").getAsBoolean());
        assertFalse(normalized.has("modules"));
        assertFalse(normalized.get("teleportationEnabled").getAsBoolean());
        assertTrue(normalized.get("rankupEnabled").getAsBoolean());
    }

    @Test
    void targetWorldMigratesToOrderedWorldListAndPlaceholderIsFixed() {
        JsonObject teleportation = JsonParser.parseString("""
            {
              "teleportation": {
                "randomTeleportSettings": {
                  "targetWorld": "bigbangworld:mundo_mineracao",
                  "defaultLocation": "{bigbangworld:mundo_mineracao}"
                }
              }
            }
            """).getAsJsonObject();

        JsonObject normalized = ConfigSplitter.normalizeTeleportationForTest(teleportation);
        JsonObject settings = normalized.getAsJsonObject("teleportation")
            .getAsJsonObject("randomTeleportSettings");

        assertEquals("bigbangworld:mundo_mineracao", settings.getAsJsonArray("world").get(0).getAsString());
        assertEquals("{world}", settings.get("defaultLocation").getAsString());
        assertEquals("bigbangworld:mundo_mineracao", settings.get("targetWorld").getAsString());
    }
}
