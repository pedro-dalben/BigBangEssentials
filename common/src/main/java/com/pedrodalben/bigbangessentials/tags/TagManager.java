package com.pedrodalben.bigbangessentials.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
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

/**
 * TagManager - Stores chat tag definitions and each player's selected tag.
 *
 * <p>Tag definitions are global and persisted in {@code world/serverconfig/bigbangessentials/tags.json}.</p>
 * <p>Each player's selected tag is persisted separately in {@code bigbangessentials/playerdata/tags/}.</p>
 */
public class TagManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TAGS_FILE_NAME = "tags.json";
    private static final String TAGS_KEY = "tags";
    private static final String SELECTED_TAG_KEY = "selectedTag";

    private static volatile TagManager instance;

    private final Map<String, String> tags = new ConcurrentHashMap<>();
    private final PlayerDataStore selectedTagStore = new PlayerDataStore("tags");
    private final File tagsFile = ResourceUtil.getConfigFile(TAGS_FILE_NAME);
    private final Object fileLock = new Object();

    private TagManager() {
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

    /**
     * Reload tag definitions from disk.
     */
    public void reload() {
        synchronized (fileLock) {
            tags.clear();
            loadTagsFromDisk();
        }
    }

    /**
     * Returns true if a tag exists.
     */
    public boolean hasTag(String tagName) {
        String normalized = normalizeTagName(tagName);
        return normalized != null && tags.containsKey(normalized);
    }

    /**
     * Returns the raw format for a tag or null if not found.
     */
    public String getTagFormat(String tagName) {
        String normalized = normalizeTagName(tagName);
        if (normalized == null) {
            return null;
        }
        return tags.get(normalized);
    }

    /**
     * Returns the permission node for a tag.
     */
    public static String getPermissionNode(String tagName) {
        String normalized = normalizeTagName(tagName);
        if (normalized == null || normalized.isEmpty()) {
            return "bigbangessentials.tag";
        }
        return "bigbangessentials.tag." + normalized;
    }

    /**
     * Create or update a tag definition.
     */
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

    /**
     * Deletes a tag definition.
     */
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

    /**
     * Returns all tag names sorted alphabetically.
     */
    public List<String> getAllTagNames() {
        List<String> result = new ArrayList<>(tags.keySet());
        Collections.sort(result);
        return result;
    }

    /**
     * Returns the tags the player can currently use.
     */
    public List<String> getAccessibleTagNames(UUID playerId) {
        List<String> result = new ArrayList<>();
        for (String tagName : getAllTagNames()) {
            if (canUseTag(playerId, tagName)) {
                result.add(tagName);
            }
        }
        return result;
    }

    /**
     * Returns the currently selected tag name for a player, or null if none.
     */
    public String getSelectedTagName(UUID playerId) {
        JsonObject data = selectedTagStore.load(playerId);
        if (data == null || !data.has(SELECTED_TAG_KEY)) {
            return null;
        }

        String selected = data.get(SELECTED_TAG_KEY).getAsString();
        String normalized = normalizeTagName(selected);
        if (normalized == null || normalized.isEmpty()) {
            clearSelectedTag(playerId);
            return null;
        }

        if (!tags.containsKey(normalized)) {
            clearSelectedTag(playerId);
            return null;
        }

        return normalized;
    }

    /**
     * Returns the selected tag format without any trailing separator.
     */
    public String getSelectedTagFormat(UUID playerId) {
        String tagName = getSelectedTagName(playerId);
        if (tagName == null) {
            return "";
        }

        String format = getTagFormat(tagName);
        return format != null ? format : "";
    }

    /**
     * Returns the selected tag text for chat.
     * Keeps the tag color local by appending a reset after the tag content.
     */
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

        return ensureTrailingSpace(format) + "§r";
    }

    /**
     * Returns the selected tag name if it is still accessible to the player.
     */
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

    /**
     * Returns true if the player has permission for the tag.
     */
    public boolean canUseTag(UUID playerId, String tagName) {
        String normalized = normalizeTagName(tagName);
        if (normalized == null || !tags.containsKey(normalized)) {
            return false;
        }
        return PermissionAPI.hasPermission(playerId, getPermissionNode(normalized));
    }

    /**
     * Select a tag for a player.
     */
    public boolean selectTag(UUID playerId, String tagName) {
        String normalized = normalizeTagName(tagName);
        if (normalized == null || !tags.containsKey(normalized) || !canUseTag(playerId, normalized)) {
            return false;
        }

        JsonObject data = selectedTagStore.load(playerId);
        data.addProperty(SELECTED_TAG_KEY, normalized);
        selectedTagStore.save(playerId, data);
        return true;
    }

    /**
     * Clears a player's selected tag.
     */
    public void clearSelectedTag(UUID playerId) {
        selectedTagStore.delete(playerId);
    }

    /**
     * Apply a selected chat tag to a template if the template does not already contain an explicit tag placeholder.
     */
    public String applyChatTags(ServerPlayer player, String template) {
        if (player == null || template == null || template.isEmpty()) {
            return template;
        }

        String selectedTag = getSelectedChatTag(player);
        if (selectedTag.isEmpty()) {
            return template;
        }

        // If the user placed a tag placeholder manually, do not inject a second copy.
        if (template.contains("{bigbangessentials_tag")) {
            return template;
        }

        String result = template;
        result = result.replace("{bigbangessentials_username}", selectedTag + "{bigbangessentials_username}");
        result = result.replace("{bigbangessentials_name}", selectedTag + "{bigbangessentials_name}");
        result = result.replace("{bigbangessentials_displayname}", selectedTag + "{bigbangessentials_displayname}");
        return result;
    }

    /**
     * Validate a tag name for safe storage and permission generation.
     */
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

    /**
     * Validate a tag format string.
     */
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
