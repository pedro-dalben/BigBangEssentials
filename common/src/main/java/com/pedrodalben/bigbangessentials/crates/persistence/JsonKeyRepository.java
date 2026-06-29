package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.repository.KeyRepository;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
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
import java.util.concurrent.ConcurrentHashMap;

public class JsonKeyRepository implements KeyRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonKeyRepository.class);
    private static final String FILE_NAME = "keys.json";

    private final Gson gson;
    private final File file;
    private final Map<String, KeyDefinition> cache = new ConcurrentHashMap<>();
    private boolean loaded = false;

    public JsonKeyRepository() {
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
            LOGGER.info("Keys file not found at {}, starting with empty key list", file.getAbsolutePath());
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<JsonObject>>(){}.getType();
            List<JsonObject> jsonList = new Gson().fromJson(reader, type);
            if (jsonList == null) return;
            for (JsonObject json : jsonList) {
                try {
                    KeyDefinition key = KeyDefinition.fromJson(json);
                    cache.put(key.getId(), key);
                } catch (Exception e) {
                    LOGGER.error("Failed to parse key definition: {}", e.getMessage(), e);
                }
            }
            LOGGER.info("Loaded {} key definitions from {}", cache.size(), file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Failed to load keys file: {}", e.getMessage(), e);
        }
    }

    private void saveToFile() {
        try {
            ResourceUtil.ensureDirectoryExists(ResourceUtil.CONFIG_DIR);
            JsonArray array = new JsonArray();
            for (KeyDefinition key : cache.values()) {
                array.add(key.toJson());
            }
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(array, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save keys file: {}", e.getMessage(), e);
        }
    }

    @Override
    public Optional<KeyDefinition> findById(String id) {
        ensureLoaded();
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public List<KeyDefinition> findAll() {
        ensureLoaded();
        return new ArrayList<>(cache.values());
    }

    @Override
    public List<KeyDefinition> findByActive(boolean active) {
        ensureLoaded();
        return cache.values().stream()
            .filter(k -> k.isActive() == active)
            .toList();
    }

    @Override
    public List<KeyDefinition> findByCompatibleCrate(String crateId) {
        ensureLoaded();
        return cache.values().stream()
            .filter(k -> k.getCompatibleCrateIds().contains(crateId))
            .toList();
    }

    @Override
    public KeyDefinition save(KeyDefinition key) {
        ensureLoaded();
        cache.put(key.getId(), key);
        saveToFile();
        return key;
    }

    @Override
    public void delete(KeyDefinition key) {
        ensureLoaded();
        cache.remove(key.getId());
        saveToFile();
    }

    @Override
    public void deleteById(String id) {
        ensureLoaded();
        cache.remove(id);
        saveToFile();
    }

    @Override
    public boolean existsById(String id) {
        ensureLoaded();
        return cache.containsKey(id);
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
