package com.pedrodalben.bigbangessentials.holograms.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.world.entity.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HologramConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(HologramConfigStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "holograms.json";

    public HologramConfig load() {
        Path path = ResourceUtil.getConfigPath(FILE_NAME);
        HologramConfig defaults = HologramConfig.defaults();
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                writeDefaults(path, defaults);
                return defaults;
            }

            JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
            if (root == null) {
                writeDefaults(path, defaults);
                return defaults;
            }

            JsonObject defaultsObj = root.has("defaults") ? root.getAsJsonObject("defaults") : new JsonObject();
            JsonObject perfObj = root.has("performance") ? root.getAsJsonObject("performance") : new JsonObject();
            JsonObject legacyObj = root.has("legacyCrates") ? root.getAsJsonObject("legacyCrates") : new JsonObject();
            JsonObject persistenceObj = root.has("persistence") ? root.getAsJsonObject("persistence") : new JsonObject();

            return new HologramConfig(
                bool(root, "enabled", defaults.enabled()),
                integer(defaultsObj, "viewDistance", defaults.defaultViewDistance()),
                integer(defaultsObj, "maxViewDistance", defaults.maxViewDistance()),
                integer(defaultsObj, "refreshIntervalTicks", defaults.defaultRefreshIntervalTicks()),
                integer(defaultsObj, "dynamicUpdateMinIntervalTicks", defaults.dynamicUpdateMinIntervalTicks()),
                integer(defaultsObj, "maxLinesPerHologram", defaults.maxLinesPerHologram()),
                integer(defaultsObj, "maxCharactersPerLine", defaults.maxCharactersPerLine()),
                integer(defaultsObj, "maxHologramsPerPlayer", defaults.maxHologramsPerPlayer()),
                bool(defaultsObj, "shadow", defaults.shadow()),
                bool(defaultsObj, "seeThrough", defaults.seeThrough()),
                billboard(defaultsObj, "billboard", defaults.billboard()),
                integer(perfObj, "viewerSyncIntervalTicks", defaults.viewerSyncIntervalTicks()),
                integer(perfObj, "maxViewerSyncsPerTick", defaults.maxViewerSyncsPerTick()),
                integer(perfObj, "maxContentUpdatesPerTick", defaults.maxContentUpdatesPerTick()),
                bool(perfObj, "spatialIndexEnabled", defaults.spatialIndexEnabled()),
                bool(perfObj, "debugMetrics", defaults.debugMetrics()),
                bool(legacyObj, "cleanupEnabled", defaults.cleanupEnabled()),
                bool(legacyObj, "cleanupOnServerStart", defaults.cleanupOnServerStart()),
                bool(legacyObj, "cleanupOnEntityLoad", defaults.cleanupOnEntityLoad()),
                bool(persistenceObj, "enabled", defaults.persistenceEnabled())
            );
        } catch (Exception e) {
            LOGGER.warn("Failed to load hologram config. Falling back to defaults: {}", e.getMessage());
            return defaults;
        }
    }

    private void writeDefaults(Path path, HologramConfig defaults) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", defaults.enabled());

        JsonObject defaultsObj = new JsonObject();
        defaultsObj.addProperty("viewDistance", defaults.defaultViewDistance());
        defaultsObj.addProperty("maxViewDistance", defaults.maxViewDistance());
        defaultsObj.addProperty("refreshIntervalTicks", defaults.defaultRefreshIntervalTicks());
        defaultsObj.addProperty("dynamicUpdateMinIntervalTicks", defaults.dynamicUpdateMinIntervalTicks());
        defaultsObj.addProperty("maxLinesPerHologram", defaults.maxLinesPerHologram());
        defaultsObj.addProperty("maxCharactersPerLine", defaults.maxCharactersPerLine());
        defaultsObj.addProperty("maxHologramsPerPlayer", defaults.maxHologramsPerPlayer());
        defaultsObj.addProperty("shadow", defaults.shadow());
        defaultsObj.addProperty("seeThrough", defaults.seeThrough());
        defaultsObj.addProperty("billboard", defaults.billboard().name());
        root.add("defaults", defaultsObj);

        JsonObject performanceObj = new JsonObject();
        performanceObj.addProperty("viewerSyncIntervalTicks", defaults.viewerSyncIntervalTicks());
        performanceObj.addProperty("maxViewerSyncsPerTick", defaults.maxViewerSyncsPerTick());
        performanceObj.addProperty("maxContentUpdatesPerTick", defaults.maxContentUpdatesPerTick());
        performanceObj.addProperty("spatialIndexEnabled", defaults.spatialIndexEnabled());
        performanceObj.addProperty("debugMetrics", defaults.debugMetrics());
        root.add("performance", performanceObj);

        JsonObject legacyObj = new JsonObject();
        legacyObj.addProperty("cleanupEnabled", defaults.cleanupEnabled());
        legacyObj.addProperty("cleanupOnServerStart", defaults.cleanupOnServerStart());
        legacyObj.addProperty("cleanupOnEntityLoad", defaults.cleanupOnEntityLoad());
        root.add("legacyCrates", legacyObj);

        JsonObject persistenceObj = new JsonObject();
        persistenceObj.addProperty("enabled", defaults.persistenceEnabled());
        root.add("persistence", persistenceObj);

        Files.writeString(path, GSON.toJson(root));
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static Display.BillboardConstraints billboard(JsonObject json, String key, Display.BillboardConstraints fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        try {
            return Display.BillboardConstraints.valueOf(json.get(key).getAsString().trim().toUpperCase());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
