package com.pedrodalben.bigbangessentials.holograms.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinitionBuilder;
import com.pedrodalben.bigbangessentials.holograms.api.HologramFlag;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPage;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPersistenceMode;
import com.pedrodalben.bigbangessentials.holograms.api.HologramRendererType;
import com.pedrodalben.bigbangessentials.holograms.api.HologramUpdatePolicy;
import com.pedrodalben.bigbangessentials.holograms.api.HologramVisibilityPolicy;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class HologramPersistenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HologramPersistenceService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String INDEX_FILE_NAME = "index.json";
    private static final String HOLOGRAMS_SUBDIR = "holograms/";
    private static final long DEBOUNCE_INTERVAL_MS = 500;
    private static final String QUARANTINE_DIR = ".quarantine";
    private static final String TMP_EXTENSION = ".tmp";
    private static final String BAK_EXTENSION = ".bak";
    private static final String JSON_EXTENSION = ".json";

    private final Path storageDir;
    private final Path indexFile;
    private final Path quarantineDir;
    private final Set<String> index = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> pendingSaves = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debouncedSaver;

    public HologramPersistenceService() {
        this.storageDir = ResourceUtil.getConfigPath(HOLOGRAMS_SUBDIR);
        this.indexFile = storageDir.resolve(INDEX_FILE_NAME);
        this.quarantineDir = storageDir.resolve(QUARANTINE_DIR);

        try {
            Files.createDirectories(storageDir);
            Files.createDirectories(quarantineDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create hologram persistence directories: {}", e.getMessage(), e);
        }

        this.debouncedSaver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hologram-persistence-saver");
            t.setDaemon(true);
            return t;
        });

        debouncedSaver.scheduleWithFixedDelay(
            this::processPendingSaves,
            DEBOUNCE_INTERVAL_MS,
            DEBOUNCE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Reads index.json, loads each per-file hologram, returns all valid definitions.
     * Invalid files are quarantined to {@code .quarantine/}.
     * Missing index entries are cleaned from the index.
     */
    public List<HologramDefinition> loadAll() {
        List<HologramDefinition> definitions = new ArrayList<>();

        List<String> ids = readIndex();
        index.clear();
        index.addAll(ids);

        for (String id : ids) {
            Path hologramFile = storageDir.resolve(id + JSON_EXTENSION);
            if (!Files.exists(hologramFile)) {
                LOGGER.warn("Hologram file missing for index entry '{}': {}", id, hologramFile);
                index.remove(id);
                writeIndex();
                continue;
            }

            try {
                String content = Files.readString(hologramFile);
                JsonObject json = GSON.fromJson(content, JsonObject.class);
                if (json == null) {
                    quarantineFile(hologramFile, "null JSON root");
                    continue;
                }

                HologramDefinition definition = readDefinition(json);
                definitions.add(definition);

                int fileVersion = json.has("schemaVersion") ? json.get("schemaVersion").getAsInt() : 0;
                if (fileVersion < CURRENT_SCHEMA_VERSION) {
                    LOGGER.info("Migrating hologram '{}' from schema v{} to v{}", id, fileVersion, CURRENT_SCHEMA_VERSION);
                    backupFile(hologramFile);
                    writeHologramFile(definition);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load hologram '{}', quarantining: {}", id, e.getMessage());
                quarantineFile(hologramFile, e.getMessage());
            }
        }

        return definitions;
    }

    /**
     * Saves a single hologram to its per-file JSON.
     * Write is immediate (atomic via tmp + rename); index update is debounced.
     */
    public void save(HologramDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        String id = definition.id();

        pendingSaves.put(id, System.currentTimeMillis());
        writeHologramFile(definition);
        index.add(id);
    }

    /**
     * Saves all definitions in batch. Each file is written atomically,
     * then the index is flushed immediately.
     */
    public void saveAll(Collection<HologramDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions must not be null");
        Set<String> savedIds = new HashSet<>();
        for (HologramDefinition definition : definitions) {
            writeHologramFile(definition);
            index.add(definition.id());
            savedIds.add(definition.id());
        }
        writeIndex();
        pendingSaves.keySet().removeAll(savedIds);
    }

    /**
     * Deletes the hologram file and removes it from the index immediately.
     */
    public void delete(String id) {
        Objects.requireNonNull(id, "id must not be null");
        index.remove(id);
        writeIndex();
        pendingSaves.remove(id);

        Path hologramFile = storageDir.resolve(id + JSON_EXTENSION);
        try {
            Files.deleteIfExists(hologramFile);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete hologram file '{}': {}", id, e.getMessage());
        }
    }

    /**
     * Force-writes all pending index updates immediately.
     */
    public void flush() {
        if (!pendingSaves.isEmpty()) {
            pendingSaves.clear();
            writeIndex();
        }
    }

    /**
     * Creates backup copies ({@code .bak}) of all hologram JSON files.
     * Index file is excluded.
     */
    public void backupAll() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, "*" + JSON_EXTENSION)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (INDEX_FILE_NAME.equals(fileName)) {
                    continue;
                }
                backupFile(file);
            }
            LOGGER.info("Backup of all hologram files completed.");
        } catch (IOException e) {
            LOGGER.warn("Failed to create backup of hologram files: {}", e.getMessage(), e);
        }
    }

    /**
     * Shuts down the debounced saver, flushing pending writes first.
     */
    public void shutdown() {
        flush();
        debouncedSaver.shutdown();
        try {
            if (!debouncedSaver.awaitTermination(2, TimeUnit.SECONDS)) {
                debouncedSaver.shutdownNow();
            }
        } catch (InterruptedException e) {
            debouncedSaver.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ---- internal ----

    private void processPendingSaves() {
        if (pendingSaves.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean indexChanged = false;

        Iterator<Map.Entry<String, Long>> it = pendingSaves.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() >= DEBOUNCE_INTERVAL_MS) {
                it.remove();
                indexChanged = true;
            }
        }

        if (indexChanged) {
            writeIndex();
        }
    }

    private void writeHologramFile(HologramDefinition definition) {
        String id = definition.id();
        Path hologramFile = storageDir.resolve(id + JSON_EXTENSION);
        Path tempFile = storageDir.resolve(id + TMP_EXTENSION);

        try {
            JsonObject json = writeDefinition(definition);
            String jsonContent = GSON.toJson(json);
            Files.writeString(tempFile, jsonContent);
            Files.move(tempFile, hologramFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Failed to persist hologram '{}': {}", id, e.getMessage());
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // cleanup failed silently
            }
        }
    }

    private List<String> readIndex() {
        if (!Files.exists(indexFile)) {
            writeIndex();
            return List.of();
        }

        try {
            String content = Files.readString(indexFile);
            JsonArray array = GSON.fromJson(content, JsonArray.class);
            if (array == null) {
                return List.of();
            }

            List<String> ids = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                ids.add(array.get(i).getAsString());
            }
            return ids;
        } catch (Exception e) {
            LOGGER.warn("Failed to read hologram index file, treating as empty: {}", e.getMessage());
            return List.of();
        }
    }

    private void writeIndex() {
        JsonArray array = new JsonArray();
        List<String> sorted = new ArrayList<>(index);
        sorted.sort(null);
        for (String id : sorted) {
            array.add(id);
        }

        Path tempFile = storageDir.resolve(INDEX_FILE_NAME + TMP_EXTENSION);
        try {
            Files.writeString(tempFile, GSON.toJson(array));
            Files.move(tempFile, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Failed to write hologram index file: {}", e.getMessage());
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // cleanup failed silently
            }
        }
    }

    private void quarantineFile(Path file, String reason) {
        try {
            Path target = quarantineDir.resolve(file.getFileName().toString());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Quarantined invalid hologram file '{}': {}", file.getFileName(), reason);
        } catch (IOException e) {
            LOGGER.warn("Failed to quarantine file '{}': {}", file.getFileName(), e.getMessage());
        }
    }

    private void backupFile(Path file) {
        Path backupFile = file.resolveSibling(file.getFileName().toString() + BAK_EXTENSION);
        try {
            Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Failed to backup file '{}': {}", file.getFileName(), e.getMessage());
        }
    }

    private HologramDefinition readDefinition(JsonObject json) {
        ResourceLocation dimensionId = ResourceLocation.parse(json.get("dimension").getAsString());
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

        HologramDefinitionBuilder builder = HologramDefinition.builder(json.get("id").getAsString())
            .ownerId(json.has("ownerId") ? json.get("ownerId").getAsString() : "")
            .location(new HologramLocation(
                dimension,
                json.get("x").getAsDouble(),
                json.get("y").getAsDouble(),
                json.get("z").getAsDouble()
            ))
            .viewDistance(json.get("viewDistance").getAsInt())
            .visibilityPolicy(HologramVisibilityPolicy.valueOf(json.get("visibilityPolicy").getAsString()))
            .updatePolicy(HologramUpdatePolicy.valueOf(json.get("updatePolicy").getAsString()))
            .rendererType(HologramRendererType.valueOf(json.get("rendererType").getAsString()))
            .persistent(true)
            .refreshIntervalTicks(json.get("refreshIntervalTicks").getAsInt())
            .offset(
                json.get("offsetX").getAsDouble(),
                json.get("offsetY").getAsDouble(),
                json.get("offsetZ").getAsDouble()
            )
            .lineWidth(json.get("lineWidth").getAsInt())
            .textOpacity(json.get("textOpacity").getAsByte())
            .backgroundColor(json.get("backgroundColor").getAsInt())
            .shadow(json.get("shadow").getAsBoolean())
            .seeThrough(json.get("seeThrough").getAsBoolean())
            .billboard(Display.BillboardConstraints.valueOf(json.get("billboard").getAsString()))
            .scale(json.get("scale").getAsFloat())
            .hideInSpectator(json.has("hideInSpectator") && json.get("hideInSpectator").getAsBoolean())
            .requiredPermission(json.has("requiredPermission") ? json.get("requiredPermission").getAsString() : "")
            .pageSwitchIntervalTicks(json.has("pageSwitchIntervalTicks") ? json.get("pageSwitchIntervalTicks").getAsInt() : 0)
            .schemaVersion(json.has("schemaVersion") ? json.get("schemaVersion").getAsInt() : 1)
            .createdAt(json.has("createdAt") ? json.get("createdAt").getAsLong() : 0L)
            .updatedAt(json.has("updatedAt") ? json.get("updatedAt").getAsLong() : 0L)
            .enabled(json.has("enabled") ? json.get("enabled").getAsBoolean() : true)
            .displayDistance(json.has("displayDistance") ? json.get("displayDistance").getAsInt() : 0)
            .updateDistance(json.has("updateDistance") ? json.get("updateDistance").getAsInt() : 0)
            .displayName(json.has("displayName") ? json.get("displayName").getAsString() : "")
            .defaultPage(json.has("defaultPage") ? json.get("defaultPage").getAsInt() : 0);

        if (json.has("persistenceMode")) {
            builder.persistenceMode(HologramPersistenceMode.valueOf(json.get("persistenceMode").getAsString()));
        } else {
            builder.persistenceMode(HologramPersistenceMode.PERSISTENT);
        }

        if (json.has("flags") && json.get("flags").isJsonArray()) {
            JsonArray flagsArray = json.getAsJsonArray("flags");
            Set<HologramFlag> flagSet = EnumSet.noneOf(HologramFlag.class);
            for (int i = 0; i < flagsArray.size(); i++) {
                try {
                    flagSet.add(HologramFlag.valueOf(flagsArray.get(i).getAsString()));
                } catch (IllegalArgumentException e) {
                    LOGGER.debug("Unknown hologram flag in serialized data: {}", flagsArray.get(i).getAsString());
                }
            }
            builder.flags(flagSet);
        }

        if (json.has("metadata")) {
            JsonObject metadata = json.getAsJsonObject("metadata");
            for (String key : metadata.keySet()) {
                builder.metadata(key, metadata.get(key).getAsString());
            }
        }

        JsonArray pagesArray = json.getAsJsonArray("pages");
        List<HologramPage> pages = new ArrayList<>();
        for (int i = 0; i < pagesArray.size(); i++) {
            JsonArray linesArray = pagesArray.get(i).getAsJsonArray();
            List<String> lines = new ArrayList<>();
            for (int j = 0; j < linesArray.size(); j++) {
                lines.add(linesArray.get(j).getAsString());
            }
            pages.add(HologramPage.ofLines(lines));
        }
        builder.pages(pages);
        return builder.build();
    }

    private JsonObject writeDefinition(HologramDefinition definition) {
        JsonObject json = new JsonObject();
        json.addProperty("id", definition.id());
        json.addProperty("ownerId", definition.ownerId());
        json.addProperty("dimension", definition.location().dimensionId().toString());
        json.addProperty("x", definition.location().x());
        json.addProperty("y", definition.location().y());
        json.addProperty("z", definition.location().z());
        json.addProperty("viewDistance", definition.viewDistance());
        json.addProperty("visibilityPolicy", definition.visibilityPolicy().name());
        json.addProperty("updatePolicy", definition.updatePolicy().name());
        json.addProperty("rendererType", definition.rendererType().name());
        json.addProperty("refreshIntervalTicks", definition.refreshIntervalTicks());
        json.addProperty("offsetX", definition.offsetX());
        json.addProperty("offsetY", definition.offsetY());
        json.addProperty("offsetZ", definition.offsetZ());
        json.addProperty("lineWidth", definition.lineWidth());
        json.addProperty("textOpacity", definition.textOpacity());
        json.addProperty("backgroundColor", definition.backgroundColor());
        json.addProperty("shadow", definition.shadow());
        json.addProperty("seeThrough", definition.seeThrough());
        json.addProperty("billboard", definition.billboard().name());
        json.addProperty("scale", definition.scale());
        json.addProperty("hideInSpectator", definition.hideInSpectator());
        json.addProperty("requiredPermission", definition.requiredPermission());
        json.addProperty("pageSwitchIntervalTicks", definition.pageSwitchIntervalTicks());
        json.addProperty("schemaVersion", definition.schemaVersion());
        json.addProperty("createdAt", definition.createdAt());
        json.addProperty("updatedAt", definition.updatedAt());
        json.addProperty("enabled", definition.enabled());
        json.addProperty("displayDistance", definition.displayDistance());
        json.addProperty("updateDistance", definition.updateDistance());
        json.addProperty("persistenceMode", definition.persistenceMode().name());
        json.addProperty("displayName", definition.displayName());
        json.addProperty("defaultPage", definition.defaultPage());

        JsonArray flagsArray = new JsonArray();
        for (HologramFlag flag : definition.flags()) {
            flagsArray.add(flag.name());
        }
        json.add("flags", flagsArray);

        JsonObject metadata = new JsonObject();
        definition.metadata().forEach(metadata::addProperty);
        json.add("metadata", metadata);

        JsonArray pagesArray = new JsonArray();
        for (HologramPage page : definition.pages()) {
            JsonArray linesArray = new JsonArray();
            for (var line : page.lines()) {
                linesArray.add(line.persistentValue());
            }
            pagesArray.add(linesArray);
        }
        json.add("pages", pagesArray);

        return json;
    }
}
