package com.pedrodalben.bigbangessentials.npcs.config;

import com.google.gson.*;
import com.pedrodalben.bigbangessentials.npcs.api.*;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class NpcConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcConfigStore.class);
    private static final String DIR = "npcs/";
    private static final String FILE = "npcs.json";
    private static final String TMP = "npcs.json.tmp";
    private static final String BAK = "npcs.json.bak";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path configDir;
    private final Path examplePath;

    public NpcConfigStore() {
        this.configDir = ResourceUtil.getConfigDirectoryPath().resolve(DIR);
        this.examplePath = configDir.resolve("npcs.example.json");
        ensureDirectory();
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create NPC config directory: {}", configDir, e);
        }
    }

    public NpcConfig load() {
        Path file = configDir.resolve(FILE);
        if (!Files.exists(file)) {
            createDefault(file);
        }

        try (Reader reader = new FileReader(file.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                LOGGER.error("NPC config is empty, loading defaults");
                return NpcConfig.defaults();
            }
            return parseRoot(root);
        } catch (Exception e) {
            LOGGER.error("Failed to load NPC config from {}, trying backup", file, e);
            Path bak = configDir.resolve(BAK);
            if (Files.exists(bak)) {
                try (Reader bakReader = new FileReader(bak.toFile())) {
                    JsonObject bakRoot = GSON.fromJson(bakReader, JsonObject.class);
                    if (bakRoot != null) return parseRoot(bakRoot);
                } catch (Exception e2) {
                    LOGGER.error("Failed to load NPC config backup", e2);
                }
            }
            return NpcConfig.defaults();
        }
    }

    public void save(NpcConfig config) {
        Path file = configDir.resolve(FILE);
        Path tmp = configDir.resolve(TMP);
        Path bak = configDir.resolve(BAK);

        try {
            JsonObject root = serializeRoot(config);
            String json = GSON.toJson(root);

            Files.writeString(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            String verify = Files.readString(tmp);
            GSON.fromJson(verify, JsonObject.class);

            if (Files.exists(file)) {
                Files.move(file, bak, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.error("Failed to save NPC config atomically to {}", file, e);
        }
    }

    public void createExample() {
        if (Files.exists(examplePath)) return;
        NpcConfig defaults = NpcConfig.defaults();
        try {
            Files.writeString(examplePath, GSON.toJson(serializeRoot(defaults)), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Failed to create NPC example config", e);
        }
    }

    private void createDefault(Path file) {
        NpcConfig defaults = NpcConfig.defaults();
        try {
            Files.writeString(file, GSON.toJson(serializeRoot(defaults)), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to create default NPC config", e);
        }
    }

    private NpcConfig parseRoot(JsonObject root) {
        int schemaVersion = getInt(root, "schemaVersion", 1);

        JsonObject defaultsObj = root.has("defaults") ? root.getAsJsonObject("defaults") : new JsonObject();
        double defaultViewDistance = getDouble(defaultsObj, "viewDistance", 48.0);
        double defaultDespawnDistance = getDouble(defaultsObj, "despawnDistance", 56.0);
        double defaultInteractionDistance = getDouble(defaultsObj, "interactionDistance", 4.5);
        long defaultCooldownMillis = getLong(defaultsObj, "interactionCooldownMillis", 750);

        JsonObject perfObj = root.has("performance") ? root.getAsJsonObject("performance") : new JsonObject();
        int visibilityScanIntervalTicks = getInt(perfObj, "visibilityScanIntervalTicks", 10);
        int maxViewerSyncsPerTick = getInt(perfObj, "maxViewerSyncsPerTick", 50);
        int maxSpawnsPerTick = getInt(perfObj, "maxSpawnsPerTick", 20);
        int maxDespawnsPerTick = getInt(perfObj, "maxDespawnsPerTick", 50);
        int maxLookUpdatesPerTick = getInt(perfObj, "maxLookUpdatesPerTick", 200);

        JsonObject skinCacheObj = root.has("skinCache") ? root.getAsJsonObject("skinCache") : new JsonObject();
        int freshTtlHours = getInt(skinCacheObj, "freshTtlHours", 24);
        int staleTtlDays = getInt(skinCacheObj, "staleTtlDays", 30);
        int negativeCacheMinutes = getInt(skinCacheObj, "negativeCacheMinutes", 10);
        int maxConcurrentRequests = getInt(skinCacheObj, "maxConcurrentRequests", 2);
        int connectTimeoutMillis = getInt(skinCacheObj, "connectTimeoutMillis", 3000);
        int requestTimeoutMillis = getInt(skinCacheObj, "requestTimeoutMillis", 5000);

        Map<String, NpcDefinition> npcs = new LinkedHashMap<>();
        if (root.has("npcs") && root.get("npcs").isJsonObject()) {
            JsonObject npcsObj = root.getAsJsonObject("npcs");
            for (Map.Entry<String, JsonElement> entry : npcsObj.entrySet()) {
                try {
                    NpcDefinition def = parseNpc(entry.getKey(), entry.getValue().getAsJsonObject(), defaultViewDistance, defaultDespawnDistance, defaultInteractionDistance, defaultCooldownMillis);
                    npcs.put(def.id(), def);
                } catch (Exception e) {
                    LOGGER.error("Invalid NPC entry '{}': {} — NPC disabled", entry.getKey(), e.getMessage());
                }
            }
        }

        return new NpcConfig(schemaVersion, defaultViewDistance, defaultDespawnDistance,
            defaultInteractionDistance, defaultCooldownMillis,
            visibilityScanIntervalTicks, maxViewerSyncsPerTick, maxSpawnsPerTick,
            maxDespawnsPerTick, maxLookUpdatesPerTick,
            freshTtlHours, staleTtlDays, negativeCacheMinutes,
            maxConcurrentRequests, connectTimeoutMillis, requestTimeoutMillis, npcs);
    }

    private NpcDefinition parseNpc(String id, JsonObject obj, double defaultVd, double defaultDd, double defaultId, long defaultCd) {
        boolean enabled = getBool(obj, "enabled", true);
        String displayName = obj.has("displayName") ? obj.get("displayName").getAsString() : id;

        JsonObject locObj = obj.has("location") ? obj.getAsJsonObject("location") : new JsonObject();
        ResourceLocation dimension = ResourceLocation.parse(locObj.has("dimension") ? locObj.get("dimension").getAsString() : "minecraft:overworld");
        double x = getDouble(locObj, "x", 0.0);
        double y = getDouble(locObj, "y", 64.0);
        double z = getDouble(locObj, "z", 0.0);
        float yaw = getFloat(locObj, "yaw", 0.0f);
        float pitch = getFloat(locObj, "pitch", 0.0f);
        NpcLocation location = new NpcLocation(dimension, x, y, z, yaw, pitch);

        NpcSkin skin;
        if (obj.has("skin") && obj.get("skin").isJsonObject()) {
            JsonObject skinObj = obj.getAsJsonObject("skin");
            String playerName = skinObj.has("playerName") ? skinObj.get("playerName").getAsString() : "Steve";
            skin = NpcSkin.unresolved(playerName);
        } else {
            skin = NpcSkin.unresolved("Steve");
        }

        NpcAction action = NpcAction.none();
        if (obj.has("action") && obj.get("action").isJsonObject()) {
            JsonObject actionObj = obj.getAsJsonObject("action");
            String typeStr = actionObj.has("type") ? actionObj.get("type").getAsString() : "NONE";
            NpcActionType type = NpcActionType.valueOf(typeStr.toUpperCase());
            String command = actionObj.has("command") ? actionObj.get("command").getAsString() : "";
            action = new NpcAction(type, command);
        }

        NpcHologramConfig hologram = NpcHologramConfig.disabled();
        if (obj.has("hologram") && obj.get("hologram").isJsonObject()) {
            JsonObject holObj = obj.getAsJsonObject("hologram");
            boolean holEnabled = getBool(holObj, "enabled", true);
            List<String> lines = new ArrayList<>();
            if (holObj.has("lines") && holObj.get("lines").isJsonArray()) {
                for (JsonElement line : holObj.getAsJsonArray("lines")) {
                    lines.add(line.getAsString());
                }
            }
            double offsetY = getDouble(holObj, "offsetY", 2.25);
            double viewDist = getDouble(holObj, "viewDistance", 32.0);
            boolean shadow = getBool(holObj, "shadow", true);
            boolean seeThrough = getBool(holObj, "seeThrough", false);
            hologram = new NpcHologramConfig(holEnabled, lines, offsetY, viewDist, shadow, seeThrough);
        }

        NpcLookSettings lookSettings = NpcLookSettings.defaults();
        if (obj.has("lookAtPlayers") && obj.get("lookAtPlayers").isJsonObject()) {
            JsonObject lookObj = obj.getAsJsonObject("lookAtPlayers");
            boolean lookEnabled = getBool(lookObj, "enabled", true);
            double range = getDouble(lookObj, "range", 10.0);
            int interval = getInt(lookObj, "updateIntervalTicks", 4);
            double minAngle = getDouble(lookObj, "minimumAngleChange", 2.0);
            double maxYaw = getDouble(lookObj, "maxYawFromBase", 100.0);
            double maxUp = getDouble(lookObj, "maxPitchUp", 45.0);
            double maxDown = getDouble(lookObj, "maxPitchDown", 35.0);
            boolean rotateBody = getBool(lookObj, "rotateBody", true);
            boolean resetOut = getBool(lookObj, "resetWhenOutOfRange", true);
            lookSettings = new NpcLookSettings(lookEnabled, range, interval, minAngle, maxYaw, maxUp, maxDown, rotateBody, resetOut);
        }

        double viewDistance = getDouble(obj, "viewDistance", defaultVd);
        double despawnDistance = getDouble(obj, "despawnDistance", defaultDd);

        NpcInteractionConfig interaction = NpcInteractionConfig.defaults();
        if (obj.has("interaction") && obj.get("interaction").isJsonObject()) {
            JsonObject intObj = obj.getAsJsonObject("interaction");
            double dist = getDouble(intObj, "distance", defaultId);
            long cd = getLong(intObj, "cooldownMillis", defaultCd);
            String perm = intObj.has("permission") ? intObj.get("permission").getAsString() : "";
            interaction = new NpcInteractionConfig(dist, cd, perm);
        }

        return new NpcDefinition(id, enabled, displayName, location, skin, action, hologram, lookSettings,
            viewDistance, despawnDistance, interaction);
    }

    private JsonObject serializeRoot(NpcConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", config.schemaVersion());

        JsonObject defaults = new JsonObject();
        defaults.addProperty("viewDistance", config.defaultViewDistance());
        defaults.addProperty("despawnDistance", config.defaultDespawnDistance());
        defaults.addProperty("interactionDistance", config.defaultInteractionDistance());
        defaults.addProperty("interactionCooldownMillis", config.defaultCooldownMillis());
        root.add("defaults", defaults);

        JsonObject perf = new JsonObject();
        perf.addProperty("visibilityScanIntervalTicks", config.visibilityScanIntervalTicks());
        perf.addProperty("maxViewerSyncsPerTick", config.maxViewerSyncsPerTick());
        perf.addProperty("maxSpawnsPerTick", config.maxSpawnsPerTick());
        perf.addProperty("maxDespawnsPerTick", config.maxDespawnsPerTick());
        perf.addProperty("maxLookUpdatesPerTick", config.maxLookUpdatesPerTick());
        root.add("performance", perf);

        JsonObject skinCache = new JsonObject();
        skinCache.addProperty("freshTtlHours", config.freshTtlHours());
        skinCache.addProperty("staleTtlDays", config.staleTtlDays());
        skinCache.addProperty("negativeCacheMinutes", config.negativeCacheMinutes());
        skinCache.addProperty("maxConcurrentRequests", config.maxConcurrentRequests());
        skinCache.addProperty("connectTimeoutMillis", config.connectTimeoutMillis());
        skinCache.addProperty("requestTimeoutMillis", config.requestTimeoutMillis());
        root.add("skinCache", skinCache);

        JsonObject npcs = new JsonObject();
        for (NpcDefinition npc : config.npcs().values()) {
            npcs.add(npc.id(), serializeNpc(npc));
        }
        root.add("npcs", npcs);

        return root;
    }

    private JsonObject serializeNpc(NpcDefinition npc) {
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", npc.enabled());
        obj.addProperty("displayName", npc.displayName());

        JsonObject loc = new JsonObject();
        loc.addProperty("dimension", npc.location().dimension().toString());
        loc.addProperty("x", npc.location().x());
        loc.addProperty("y", npc.location().y());
        loc.addProperty("z", npc.location().z());
        loc.addProperty("yaw", npc.location().yaw());
        loc.addProperty("pitch", npc.location().pitch());
        obj.add("location", loc);

        JsonObject skin = new JsonObject();
        skin.addProperty("playerName", npc.skin().playerName());
        obj.add("skin", skin);

        JsonObject action = new JsonObject();
        action.addProperty("type", npc.action().type().name());
        action.addProperty("command", npc.action().command());
        obj.add("action", action);

        JsonObject hol = new JsonObject();
        hol.addProperty("enabled", npc.hologram().enabled());
        JsonArray lines = new JsonArray();
        for (String line : npc.hologram().lines()) lines.add(line);
        hol.add("lines", lines);
        hol.addProperty("offsetY", npc.hologram().offsetY());
        hol.addProperty("viewDistance", npc.hologram().viewDistance());
        hol.addProperty("shadow", npc.hologram().shadow());
        hol.addProperty("seeThrough", npc.hologram().seeThrough());
        obj.add("hologram", hol);

        JsonObject look = new JsonObject();
        look.addProperty("enabled", npc.lookSettings().enabled());
        look.addProperty("range", npc.lookSettings().range());
        look.addProperty("updateIntervalTicks", npc.lookSettings().updateIntervalTicks());
        look.addProperty("minimumAngleChange", npc.lookSettings().minimumAngleChange());
        look.addProperty("maxYawFromBase", npc.lookSettings().maxYawFromBase());
        look.addProperty("maxPitchUp", npc.lookSettings().maxPitchUp());
        look.addProperty("maxPitchDown", npc.lookSettings().maxPitchDown());
        look.addProperty("rotateBody", npc.lookSettings().rotateBody());
        look.addProperty("resetWhenOutOfRange", npc.lookSettings().resetWhenOutOfRange());
        obj.add("lookAtPlayers", look);

        obj.addProperty("viewDistance", npc.viewDistance());
        obj.addProperty("despawnDistance", npc.despawnDistance());

        JsonObject interaction = new JsonObject();
        interaction.addProperty("distance", npc.interaction().distance());
        interaction.addProperty("cooldownMillis", npc.interaction().cooldownMillis());
        interaction.addProperty("permission", npc.interaction().permission());
        obj.add("interaction", interaction);

        return obj;
    }

    private static int getInt(JsonObject obj, String key, int defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive() && ((JsonPrimitive) el).isNumber()) return el.getAsInt();
        return defaultValue;
    }

    private static long getLong(JsonObject obj, String key, long defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive() && ((JsonPrimitive) el).isNumber()) return el.getAsLong();
        return defaultValue;
    }

    private static double getDouble(JsonObject obj, String key, double defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive() && ((JsonPrimitive) el).isNumber()) return el.getAsDouble();
        return defaultValue;
    }

    private static float getFloat(JsonObject obj, String key, float defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive() && ((JsonPrimitive) el).isNumber()) return el.getAsFloat();
        return defaultValue;
    }

    private static boolean getBool(JsonObject obj, String key, boolean defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive()) {
            JsonPrimitive prim = (JsonPrimitive) el;
            if (prim.isBoolean()) return prim.getAsBoolean();
            if (prim.isString()) return Boolean.parseBoolean(prim.getAsString());
        }
        return defaultValue;
    }
}
