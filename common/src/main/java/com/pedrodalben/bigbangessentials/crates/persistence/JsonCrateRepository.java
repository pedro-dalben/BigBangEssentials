package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.repository.CrateRepository;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JsonCrateRepository implements CrateRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonCrateRepository.class);
    private static final String FILE_NAME = "crates.json";

    private final Gson gson;
    private final File file;
    private final Map<String, CrateDefinition> cache = new ConcurrentHashMap<>();
    private boolean loaded = false;

    public JsonCrateRepository() {
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
            LOGGER.info("Crate file not found at {}, starting with empty crate list", file.getAbsolutePath());
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<JsonObject>>(){}.getType();
            List<JsonObject> jsonList = new Gson().fromJson(reader, type);
            if (jsonList == null) return;
            for (JsonObject json : jsonList) {
                try {
                    CrateDefinition crate = CrateDefinition.fromJson(json);
                    cache.put(crate.getKey(), crate);
                } catch (Exception e) {
                    LOGGER.error("Failed to parse crate definition: {}", e.getMessage(), e);
                }
            }
            LOGGER.info("Loaded {} crate definitions from {}", cache.size(), file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Failed to load crate file: {}", e.getMessage(), e);
        }
    }

    private void saveToFile() {
        try {
            ResourceUtil.ensureDirectoryExists(ResourceUtil.CONFIG_DIR);
            JsonArray array = new JsonArray();
            for (CrateDefinition crate : cache.values()) {
                array.add(crate.toJson());
            }
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(array, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save crate file: {}", e.getMessage(), e);
        }
    }

    @Override
    public Optional<CrateDefinition> findById(UUID id) {
        ensureLoaded();
        return cache.values().stream()
            .filter(c -> c.getId().equals(id))
            .findFirst();
    }

    @Override
    public Optional<CrateDefinition> findByKey(String key) {
        ensureLoaded();
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public List<CrateDefinition> findAll() {
        ensureLoaded();
        return new ArrayList<>(cache.values());
    }

    @Override
    public List<CrateDefinition> findByEnabled(boolean enabled) {
        ensureLoaded();
        return cache.values().stream()
            .filter(c -> c.isEnabled() == enabled)
            .toList();
    }

    @Override
    public CrateDefinition save(CrateDefinition crate) {
        ensureLoaded();
        cache.put(crate.getKey(), crate);
        saveToFile();
        return crate;
    }

    @Override
    public void delete(CrateDefinition crate) {
        ensureLoaded();
        cache.remove(crate.getKey());
        saveToFile();
    }

    @Override
    public void deleteByKey(String key) {
        ensureLoaded();
        cache.remove(key);
        saveToFile();
    }

    @Override
    public boolean existsByKey(String key) {
        ensureLoaded();
        return cache.containsKey(key);
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
