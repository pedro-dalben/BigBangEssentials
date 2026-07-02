package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.crates.domain.CrateKeyType;
import com.pedrodalben.bigbangessentials.crates.domain.ItemSerializer;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.repository.KeyRepository;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-backed KeyRepository. Primary source of truth is the database.
 * Reads from crate_keys table; maintains read cache for fast access.
 */
public class JdbcKeyRepository extends JdbcRepository implements KeyRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcKeyRepository.class);

    private static final String TABLE = "crate_keys";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE;
    private static final String SELECT_BY_ID = "SELECT * FROM " + TABLE + " WHERE id = ?";
    private static final String SELECT_BY_ACTIVE = "SELECT * FROM " + TABLE + " WHERE active = ?";
    private static final String INSERT = "INSERT INTO " + TABLE + " (id, name, key_type, active, item_template_json, lore_json, required_permission, give_sound, take_sound, give_commands_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE " + TABLE + " SET name = ?, key_type = ?, active = ?, item_template_json = ?, lore_json = ?, required_permission = ?, give_sound = ?, take_sound = ?, give_commands_json = ?, updated_at = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE id = ?";
    private static final String EXISTS = "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private final Gson gson = new GsonBuilder().create();
    private static final ConcurrentHashMap<String, KeyDefinition> cache = new ConcurrentHashMap<>();
    private static volatile boolean cacheLoaded = false;

    private final RowMapper<KeyDefinition> MAPPER = (rs) -> {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String keyTypeStr = rs.getString("key_type");
        boolean active = rs.getBoolean("active");

        KeyDefinition key = new KeyDefinition(id, name);
        key.setActive(active);

        if ("PHYSICAL".equals(keyTypeStr)) {
            key.setKeyType(CrateKeyType.PHYSICAL);
        } else {
            key.setKeyType(CrateKeyType.VIRTUAL);
        }

        String itemJson = rs.getString("item_template_json");
        if (itemJson != null && !itemJson.isEmpty()) {
            try {
                com.google.gson.JsonObject obj = gson.fromJson(itemJson, com.google.gson.JsonObject.class);
                key.setPhysicalItem(ItemSerializer.deserialize(obj));
            } catch (Exception e) {
                LOGGER.warn("Failed to deserialize item template for key '{}': {}", id, e.getMessage());
            }
        }

        String loreJson = rs.getString("lore_json");
        if (loreJson != null && !loreJson.isEmpty()) {
            try {
                Type listType = new TypeToken<List<String>>(){}.getType();
                List<String> lore = gson.fromJson(loreJson, listType);
                key.setLore(lore);
            } catch (Exception e) {
                LOGGER.warn("Failed to deserialize lore for key '{}': {}", id, e.getMessage());
            }
        }

        String perm = rs.getString("required_permission");
        if (perm != null) key.setRequiredPermission(perm);

        String giveSound = rs.getString("give_sound");
        if (giveSound != null) key.setGiveSound(giveSound);

        String takeSound = rs.getString("take_sound");
        if (takeSound != null) key.setTakeSound(takeSound);

        String commandsJson = rs.getString("give_commands_json");
        if (commandsJson != null && !commandsJson.isEmpty()) {
            try {
                Type listType = new TypeToken<List<String>>(){}.getType();
                List<String> commands = gson.fromJson(commandsJson, listType);
                key.setGiveCommands(commands);
            } catch (Exception e) {
                LOGGER.warn("Failed to deserialize give commands for key '{}': {}", id, e.getMessage());
            }
        }

        return key;
    };

    private void ensureCache() {
        if (cacheLoaded) return;
        synchronized (JdbcKeyRepository.class) {
            if (cacheLoaded) return;
            try {
                List<KeyDefinition> all = getDatabase().queryList(SELECT_ALL, null, MAPPER).join();
                cache.clear();
                for (KeyDefinition k : all) {
                    cache.put(k.getId(), k);
                }
                cacheLoaded = true;
                LOGGER.debug("Loaded {} key definitions into cache", all.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load key cache: {}", e.getMessage(), e);
            }
        }
    }

    private void invalidateCache() {
        cacheLoaded = false;
        cache.clear();
    }

    @Override
    public Optional<KeyDefinition> findById(String id) {
        ensureCache();
        KeyDefinition cached = cache.get(id);
        if (cached != null) return Optional.of(cached);
        try {
            return getDatabase().querySingle(SELECT_BY_ID,
                stmt -> stmt.setString(1, id), MAPPER).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find key '{}': {}", id, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<KeyDefinition> findAll() {
        ensureCache();
        return new ArrayList<>(cache.values());
    }

    @Override
    public List<KeyDefinition> findByActive(boolean active) {
        ensureCache();
        return cache.values().stream()
            .filter(k -> k.isActive() == active)
            .toList();
    }

    @Override
    public List<KeyDefinition> findByCompatibleCrate(String crateId) {
        ensureCache();
        return cache.values().stream()
            .filter(k -> k.getCompatibleCrateIds().contains(crateId))
            .toList();
    }

    @Override
    public KeyDefinition save(KeyDefinition key) {
        final String itemJsonF = (key.getPhysicalItem() != null && !key.getPhysicalItem().isEmpty())
            ? ItemSerializer.serialize(key.getPhysicalItem()).toString() : null;
        final String loreJsonF = key.getLore().isEmpty() ? null : gson.toJson(key.getLore());
        final String commandsJsonF = key.getGiveCommands().isEmpty() ? null : gson.toJson(key.getGiveCommands());
        long now = System.currentTimeMillis();

        try {
            int updated = getDatabase().executeUpdate(UPDATE,
                stmt -> {
                    stmt.setString(1, key.getName());
                    stmt.setString(2, key.getKeyType().name());
                    stmt.setBoolean(3, key.isActive());
                    if (itemJsonF != null) stmt.setString(4, itemJsonF);
                    else stmt.setNull(4, java.sql.Types.VARCHAR);
                    if (loreJsonF != null) stmt.setString(5, loreJsonF);
                    else stmt.setNull(5, java.sql.Types.VARCHAR);
                    stmt.setString(6, key.getRequiredPermission());
                    stmt.setString(7, key.getGiveSound());
                    stmt.setString(8, key.getTakeSound());
                    if (commandsJsonF != null) stmt.setString(9, commandsJsonF);
                    else stmt.setNull(9, java.sql.Types.VARCHAR);
                    stmt.setLong(10, now);
                    stmt.setString(11, key.getId());
                }
            ).join();

            if (updated == 0) {
                getDatabase().executeUpdate(INSERT,
                    stmt -> {
                        stmt.setString(1, key.getId());
                        stmt.setString(2, key.getName());
                        stmt.setString(3, key.getKeyType().name());
                        stmt.setBoolean(4, key.isActive());
                        if (itemJsonF != null) stmt.setString(5, itemJsonF);
                        else stmt.setNull(5, java.sql.Types.VARCHAR);
                        if (loreJsonF != null) stmt.setString(6, loreJsonF);
                        else stmt.setNull(6, java.sql.Types.VARCHAR);
                        stmt.setString(7, key.getRequiredPermission());
                        stmt.setString(8, key.getGiveSound());
                        stmt.setString(9, key.getTakeSound());
                        if (commandsJsonF != null) stmt.setString(10, commandsJsonF);
                        else stmt.setNull(10, java.sql.Types.VARCHAR);
                        stmt.setLong(11, now);
                        stmt.setLong(12, now);
                    }
                ).join();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save key '{}': {}", key.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save key", e);
        }
        invalidateCache();
        return key;
    }

    @Override
    public void delete(KeyDefinition key) {
        deleteById(key.getId());
    }

    @Override
    public void deleteById(String id) {
        try {
            getDatabase().executeUpdate(DELETE,
                stmt -> stmt.setString(1, id)).join();
            invalidateCache();
        } catch (Exception e) {
            LOGGER.error("Failed to delete key '{}': {}", id, e.getMessage(), e);
        }
    }

    @Override
    public boolean existsById(String id) {
        ensureCache();
        if (cache.containsKey(id)) return true;
        try {
            return getDatabase().querySingle(EXISTS,
                stmt -> stmt.setString(1, id),
                (rs) -> rs.getLong(1) > 0).join().orElse(false);
        } catch (Exception e) {
            LOGGER.error("Failed to check key existence: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public long count() {
        ensureCache();
        return cache.size();
    }
}
