package com.pedrodalben.bigbangessentials.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.util.PlayerDataStore;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TagManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TAGS_FILE_NAME = "tags.json";
    private static final String TAGS_KEY = "tags";
    private static final String SELECTED_TAG_KEY = "selectedTag";

    private static volatile TagManager instance;

    private final Map<String, String> tags = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedTagCache = new ConcurrentHashMap<>();
    private final PlayerDataStore selectedTagStore = new PlayerDataStore("tags");
    private final File tagsFile = ResourceUtil.getConfigFile(TAGS_FILE_NAME);
    private final Object fileLock = new Object();
    private volatile PlayerPreferencesStorage dbStorage;

    private TagManager() {
        this.dbStorage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
        reload();
    }

    public static TagManager getInstance() {
        if (instance == null) {
            synchronized (TagManager.class) {
                if (instance == null) {
                    instance = new TagManager();
                }
            }
        }
        return instance;
    }

    public void reload() {
        synchronized (fileLock) {
            tags.clear();
            loadTagsFromDisk();
        }
    }

    public boolean hasTag(String tagName) {
        String normalized = normalizeTagName(tagName);
        return normalized != null && tags.containsKey(normalized);
    }

    public String getTagFormat(String tagName) {
        String normalized = normalizeTagName(tagName);
        if (normalized == null) {
            return null;
        }
        return tags.get(normalized);
    }

    public static String getPermissionNode(String tagName) {
        String normalized = normalizeTagName(tagName);
        if (normalized == null || normalized.isEmpty()) {
            return "bigbangessentials.tag";
        }
        return "bigbangessentials.tag." + normalized;
    }

    public boolean upsertTag(String tagName, String format) {
        String normalized = normalizeTagName(tagName);
        if (!isValidTagName(normalized) || !isValidTagFormat(format)) {
            return false;
        }

        synchronized (fileLock) {
            tags.put(normalized, format.strip());
            saveTagsToDisk();
        }

        return true;
    }

    public boolean deleteTag(String tagName) {
        String normalized = normalizeTagName(tagName);
        if (!isValidTagName(normalized)) {
            return false;
        }

        synchronized (fileLock) {
            if (tags.remove(normalized) == null) {
                return false;
            }
            saveTagsToDisk();
        }

        return true;
    }

    public List<String> getAllTagNames() {
        List<String> result = new ArrayList<>(tags.keySet());
        Collections.sort(result);
        return result;
    }

    public List<String> getAccessibleTagNames(UUID playerId) {
        List<String> result = new ArrayList<>();
        for (String tagName : getAllTagNames()) {
            if (canUseTag(playerId, tagName)) {
                result.add(tagName);
            }
        }
        return result;
    }

    public String getSelectedTagName(UUID playerId) {
        // Fast path: cache hit
        String cached = selectedTagCache.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Synchronous fallback: load from local store
        String local = loadFromLocalStore(playerId);
        if (local != null) {
            selectedTagCache.put(playerId, local);
            return local;
        }

        // No local data — try DB asynchronously
        loadSelectedTagFromDBAsync(playerId);
        return null;
    }

    /**
     * Kicks off an async DB load on login. The result is cached and
     * notified to the tablist when it arrives. Public so login handler
     * can call it without modifying the synchronous contract of
     * {@link #getSelectedTagName(UUID)}.
     */
    public void loadSelectedTagNameAsync(UUID playerId) {
        loadSelectedTagFromDBAsync(playerId);
    }

    private void loadSelectedTagFromDBAsync(UUID playerId) {
        // Already cached — nothing to do
        if (selectedTagCache.containsKey(playerId)) {
            return;
        }

        PlayerPreferencesStorage storage = resolveDbStorage();
        if (storage == null) return;

        storage.loadTag(playerId).thenAccept(dbTag -> {
            if (dbTag != null) {
                String normalized = normalizeTagName(dbTag);
                if (normalized != null && tags.containsKey(normalized)) {
                    selectedTagCache.put(playerId, normalized);
                    String tagFormat = getTagFormat(normalized);
                    // Defer tablist notification to main server thread.
                    // CompletableFuture callbacks run on ForkJoinPool; direct
                    // access to TabPlayerState + invalidatePlayer is not atomic.
                    net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
                    if (server != null) {
                        server.tell(new net.minecraft.server.TickTask(server.getTickCount(),
                            () -> notifyTablistOfTag(playerId, tagFormat)));
                    }
                }
            }
        }).exceptionally(err -> {
            LOGGER.debug("Failed to async load tag for {}: {}", playerId, err.getMessage());
            return null;
        });
    }

    private void notifyTablistOfTag(UUID playerId, String tagFormat) {
        try {
            com.pedrodalben.bigbangessentials.tablist.integration.TagTabIntegration.onTagChange(playerId, tagFormat != null ? tagFormat : "");
        } catch (Exception e) {
            LOGGER.debug("Failed to notify tablist of tag: {}", e.getMessage());
        }
    }

    private String loadFromLocalStore(UUID playerId) {
        JsonObject data = selectedTagStore.load(playerId);
        if (data == null || !data.has(SELECTED_TAG_KEY)) {
            return null;
        }
        String selected = data.get(SELECTED_TAG_KEY).getAsString();
        String normalized = normalizeTagName(selected);
        if (normalized == null || normalized.isEmpty() || !tags.containsKey(normalized)) {
            return null;
        }
        return normalized;
    }

    public String getSelectedTagFormat(UUID playerId) {
        String tagName = getSelectedTagName(playerId);
        if (tagName == null) {
            return "";
        }

        String format = getTagFormat(tagName);
        return format != null ? format : "";
    }

    public String getSelectedChatTag(ServerPlayer player) {
        if (player == null) {
            return "";
        }

        String tagName = getSelectedTagName(player.getUUID());
        if (tagName == null || !canUseTag(player.getUUID(), tagName)) {
            return "";
        }

        String format = getTagFormat(tagName);
        if (format == null || format.isBlank()) {
            return "";
        }

        return ensureTrailingSpace(format) + "\u00a7r";
    }

    public String getSelectedAccessibleTagName(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        String tagName = getSelectedTagName(player.getUUID());
        if (tagName == null) {
            return null;
        }

        return canUseTag(player.getUUID(), tagName) ? tagName : null;
    }

    public boolean canUseTag(UUID playerId, String tagName) {
        String normalized = normalizeTagName(tagName);
        if (normalized == null || !tags.containsKey(normalized)) {
            return false;
        }
        return PermissionAPI.hasPermission(playerId, getPermissionNode(normalized));
    }

    public boolean selectTag(UUID playerId, String tagName) {
        String normalized = normalizeTagName(tagName);
        if (normalized == null || !tags.containsKey(normalized) || !canUseTag(playerId, normalized)) {
            return false;
        }

        selectedTagCache.put(playerId, normalized);

        JsonObject data = selectedTagStore.load(playerId);
        data.addProperty(SELECTED_TAG_KEY, normalized);
        selectedTagStore.save(playerId, data);

        saveSelectedTagToDatabase(playerId, normalized);
        return true;
    }

    public void clearSelectedTag(UUID playerId) {
        selectedTagCache.remove(playerId);
        selectedTagStore.delete(playerId);
        deleteSelectedTagFromDatabase(playerId);
    }

    private void saveSelectedTagToDatabase(UUID playerId, String tagName) {
        PlayerPreferencesStorage storage = resolveDbStorage();
        if (storage == null) return;
        storage.saveTag(playerId, tagName).exceptionally(err -> {
            LOGGER.warn("Failed to save selected tag '{}' for {}", tagName, playerId, err);
            return null;
        });
    }

    private void deleteSelectedTagFromDatabase(UUID playerId) {
        PlayerPreferencesStorage storage = resolveDbStorage();
        if (storage == null) return;
        storage.deleteTag(playerId).exceptionally(err -> {
            LOGGER.warn("Failed to delete selected tag for {}", playerId, err);
            return null;
        });
    }

    private PlayerPreferencesStorage resolveDbStorage() {
        PlayerPreferencesStorage storage = dbStorage;
        if (storage == null) {
            storage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
            dbStorage = storage;
        }
        return storage;
    }

    public String applyChatTags(ServerPlayer player, String template) {
        if (player == null || template == null || template.isEmpty()) {
            return template;
        }

        String selectedTag = getSelectedChatTag(player);
        if (selectedTag.isEmpty()) {
            return template;
        }

        if (template.contains("{bigbangessentials_tag")) {
            return template;
        }

        String result = template;
        result = result.replace("{bigbangessentials_username}", selectedTag + "{bigbangessentials_username}");
        result = result.replace("{bigbangessentials_name}", selectedTag + "{bigbangessentials_name}");
        result = result.replace("{bigbangessentials_displayname}", selectedTag + "{bigbangessentials_displayname}");
        return result;
    }

    public static boolean isValidTagName(String tagName) {
        if (tagName == null) {
            return false;
        }

        String normalized = tagName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 32) {
            return false;
        }

        if (!normalized.matches("^[a-z0-9._-]+$")) {
            return false;
        }

        if (normalized.startsWith(".") || normalized.endsWith(".") || normalized.contains("..")) {
            return false;
        }

        return true;
    }

    public static boolean isValidTagFormat(String format) {
        if (format == null) {
            return false;
        }

        String normalized = format.strip();
        if (normalized.isEmpty() || normalized.length() > 64) {
            return false;
        }

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isISOControl(c)) {
                return false;
            }
        }

        return true;
    }

    private void loadTagsFromDisk() {
        try {
            File parent = tagsFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                LOGGER.warn("Failed to create tags config directory: {}", parent.getAbsolutePath());
            }

            if (!tagsFile.exists()) {
                saveTagsToDisk();
                return;
            }

            try (FileReader reader = new FileReader(tagsFile)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    LOGGER.warn("tags.json is not a JSON object, starting with an empty tag set");
                    saveTagsToDisk();
                    return;
                }

                JsonObject root = element.getAsJsonObject();
                JsonObject tagsObject = root.has(TAGS_KEY) && root.get(TAGS_KEY).isJsonObject()
                    ? root.getAsJsonObject(TAGS_KEY)
                    : new JsonObject();

                for (Map.Entry<String, JsonElement> entry : tagsObject.entrySet()) {
                    String normalized = normalizeTagName(entry.getKey());
                    if (normalized == null || !isValidTagName(normalized)) {
                        LOGGER.warn("Skipping invalid tag name in tags.json: {}", entry.getKey());
                        continue;
                    }

                    if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                        LOGGER.warn("Skipping invalid tag format for '{}': expected string", entry.getKey());
                        continue;
                    }

                    String format = entry.getValue().getAsString().strip();
                    if (!isValidTagFormat(format)) {
                        LOGGER.warn("Skipping invalid tag format for '{}'", entry.getKey());
                        continue;
                    }

                    tags.put(normalized, format);
                }

                LOGGER.info("Loaded {} chat tag(s)", tags.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load chat tags: {}", e.getMessage(), e);
        }
    }

    private void saveTagsToDisk() {
        try {
            File parent = tagsFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                LOGGER.warn("Failed to create tags config directory: {}", parent.getAbsolutePath());
            }

            File tempFile = new File(tagsFile.getAbsolutePath() + ".tmp");
            JsonObject root = new JsonObject();
            root.addProperty("_version", 1);
            JsonObject tagsObject = new JsonObject();
            tags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> tagsObject.addProperty(entry.getKey(), entry.getValue()));
            root.add(TAGS_KEY, tagsObject);

            try (FileWriter writer = new FileWriter(tempFile)) {
                GSON.toJson(root, writer);
            }

            Files.move(tempFile.toPath(), tagsFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

            LOGGER.debug("Saved {} chat tag(s)", tags.size());
        } catch (Exception e) {
            LOGGER.error("Failed to save chat tags: {}", e.getMessage(), e);
        }
    }

    private static String normalizeTagName(String tagName) {
        if (tagName == null) {
            return null;
        }
        String normalized = tagName.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String ensureTrailingSpace(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        char lastChar = value.charAt(value.length() - 1);
        return Character.isWhitespace(lastChar) ? value : value + " ";
    }
}
