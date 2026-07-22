package com.pedrodalben.bigbangessentials.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigTemplateResourcesTest {
    @Test
    void requiredConfigTemplatesAreBundledAndValidJson() {
        for (String name : List.of("config.json", "economy.json", "permissions.json", "kits.json", "discord_auth.json", "tablist.json", "modules.json")) {
            try (InputStream input = getClass().getResourceAsStream("/data/config/bigbangessentials/" + name)) {
                assertNotNull(input, () -> "Missing bundled config template: " + name);
                JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                throw new AssertionError("Invalid bundled config template: " + name, e);
            }
        }
    }
}
