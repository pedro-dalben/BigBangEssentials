package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.repository.CrateLocationRepository;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JsonCrateLocationRepository implements CrateLocationRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonCrateLocationRepository.class);
    private static final String FILE_NAME = "crate_locations.json";

    private final Gson gson;
    private final File file;
    private final Map<UUID, CrateLocation> cache = new ConcurrentHashMap<>();
    private boolean loaded = false;

    public JsonCrateLocationRepository() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.file = ResourceUtil.getConfigFile(FILE_NAME);
    }

    private void ensureLoaded() {
        if (loaded) return;
        loadFromFile();
        loaded = true;
    }

    private void loadFromFile() {
        cache.clear();
        if (!file.exists()) {
            LOGGER.info("Crate locations file not found at {}, starting empty", file.getAbsolutePath());
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<JsonObject>>(){}.getType();
            List<JsonObject> jsonList = new Gson().fromJson(reader, type);
            if (jsonList == null) return;
            for (JsonObject json : jsonList) {
                try {
                    CrateLocation loc = CrateLocation.fromJson(json);
                    cache.put(loc.getId(), loc);
                } catch (Exception e) {
                    LOGGER.error("Failed to parse crate location: {}", e.getMessage(), e);
                }
            }
            LOGGER.info("Loaded {} crate locations from {}", cache.size(), file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Failed to load crate locations file: {}", e.getMessage(), e);
        }
    }

    private void saveToFile() {
        try {
            ResourceUtil.ensureDirectoryExists(ResourceUtil.CONFIG_DIR);
            JsonArray array = new JsonArray();
            for (CrateLocation loc : cache.values()) {
                array.add(loc.toJson());
            }
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(array, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save crate locations file: {}", e.getMessage(), e);
        }
    }

    @Override
    public Optional<CrateLocation> findById(UUID id) {
        ensureLoaded();
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public Optional<CrateLocation> findByPosition(ResourceKey<Level> dimension, BlockPos position) {
        ensureLoaded();
        return cache.values().stream()
            .filter(loc -> loc.getDimension().equals(dimension) && loc.getPosition().equals(position))
            .findFirst();
    }

    @Override
    public List<CrateLocation> findByCrateId(String crateId) {
        ensureLoaded();
        return cache.values().stream()
            .filter(loc -> loc.getCrateId().equals(crateId))
            .toList();
    }

    @Override
    public List<CrateLocation> findByDimension(ResourceKey<Level> dimension) {
        ensureLoaded();
        return cache.values().stream()
            .filter(loc -> loc.getDimension().equals(dimension))
            .toList();
    }

    @Override
    public List<CrateLocation> findAll() {
        ensureLoaded();
        return new ArrayList<>(cache.values());
    }

    @Override
    public CrateLocation save(CrateLocation location) {
        ensureLoaded();
        cache.put(location.getId(), location);
        saveToFile();
        return location;
    }

    @Override
    public void delete(CrateLocation location) {
        ensureLoaded();
        cache.remove(location.getId());
        saveToFile();
    }

    @Override
    public void deleteById(UUID id) {
        ensureLoaded();
        cache.remove(id);
        saveToFile();
    }

    @Override
    public long count() {
        ensureLoaded();
        return cache.size();
    }

    public void reload() {
        loaded = false;
        ensureLoaded();
    }
}
