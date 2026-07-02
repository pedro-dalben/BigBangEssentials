package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.repository.CrateRepository;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-backed CrateRepository. Primary source of truth is the database.
 * Maintains a read cache for fast access; writes go to DB and invalidate cache.
 */
public class JdbcCrateRepository extends JdbcRepository implements CrateRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcCrateRepository.class);

    private static final String TABLE = "crate_definitions";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE;
    private static final String SELECT_BY_ID = "SELECT * FROM " + TABLE + " WHERE id = ?";
    private static final String SELECT_BY_KEY = "SELECT * FROM " + TABLE + " WHERE key_id = ?";
    private static final String SELECT_ENABLED = "SELECT * FROM " + TABLE + " WHERE enabled = 1";
    private static final String INSERT = "INSERT INTO " + TABLE + " (id, key_id, display_name, definition_json, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE " + TABLE + " SET key_id = ?, display_name = ?, definition_json = ?, enabled = ?, updated_at = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE key_id = ?";
    private static final String DELETE_BY_ID = "DELETE FROM " + TABLE + " WHERE id = ?";
    private static final String EXISTS_BY_KEY = "SELECT COUNT(*) FROM " + TABLE + " WHERE key_id = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private final Gson gson = new GsonBuilder().create();
    private final ConcurrentHashMap<String, CrateDefinition> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CrateDefinition> idCache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    private final RowMapper<CrateDefinition> MAPPER = (rs) -> {
        String json = rs.getString("definition_json");
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        return CrateDefinition.fromJson(obj);
    };

    private void ensureCache() {
        if (cacheLoaded) return;
        synchronized (this) {
            if (cacheLoaded) return;
            try {
                List<CrateDefinition> all = getDatabase().queryList(SELECT_ALL, null, MAPPER).join();
                keyCache.clear();
                idCache.clear();
                for (CrateDefinition c : all) {
                    keyCache.put(c.getKey(), c);
                    idCache.put(c.getId(), c);
                }
                cacheLoaded = true;
                LOGGER.debug("Loaded {} crate definitions into cache", all.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load crate cache: {}", e.getMessage(), e);
            }
        }
    }

    private void invalidateCache() {
        cacheLoaded = false;
        keyCache.clear();
        idCache.clear();
    }

    private String toJson(CrateDefinition crate) {
        return crate.toJson().toString();
    }

    @Override
    public Optional<CrateDefinition> findById(UUID id) {
        ensureCache();
        CrateDefinition cached = idCache.get(id);
        if (cached != null) return Optional.of(cached);
        try {
            return getDatabase().querySingle(SELECT_BY_ID,
                stmt -> stmt.setString(1, id.toString()), MAPPER).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find crate by id: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<CrateDefinition> findByKey(String key) {
        ensureCache();
        CrateDefinition cached = keyCache.get(key);
        if (cached != null) return Optional.of(cached);
        try {
            return getDatabase().querySingle(SELECT_BY_KEY,
                stmt -> stmt.setString(1, key), MAPPER).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find crate by key: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<CrateDefinition> findAll() {
        ensureCache();
        return new ArrayList<>(keyCache.values());
    }

    @Override
    public List<CrateDefinition> findByEnabled(boolean enabled) {
        ensureCache();
        return keyCache.values().stream()
            .filter(c -> c.isEnabled() == enabled)
            .toList();
    }

    @Override
    public CrateDefinition save(CrateDefinition crate) {
        String json = toJson(crate);
        long now = System.currentTimeMillis();
        try {
            int updated = getDatabase().executeUpdate(UPDATE,
                stmt -> {
                    stmt.setString(1, crate.getKey());
                    stmt.setString(2, crate.getDisplayName());
                    stmt.setString(3, json);
                    stmt.setBoolean(4, crate.isEnabled());
                    stmt.setLong(5, now);
                    stmt.setString(6, crate.getId().toString());
                }
            ).join();
            if (updated == 0) {
                getDatabase().executeUpdate(INSERT,
                    stmt -> {
                        stmt.setString(1, crate.getId().toString());
                        stmt.setString(2, crate.getKey());
                        stmt.setString(3, crate.getDisplayName());
                        stmt.setString(4, json);
                        stmt.setBoolean(5, crate.isEnabled());
                        stmt.setLong(6, now);
                        stmt.setLong(7, now);
                    }
                ).join();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save crate '{}': {}", crate.getKey(), e.getMessage(), e);
            throw new RuntimeException("Failed to save crate", e);
        }
        invalidateCache();
        return crate;
    }

    @Override
    public void delete(CrateDefinition crate) {
        deleteByKey(crate.getKey());
    }

    @Override
    public void deleteByKey(String key) {
        try {
            getDatabase().executeUpdate(DELETE,
                stmt -> stmt.setString(1, key)).join();
            invalidateCache();
        } catch (Exception e) {
            LOGGER.error("Failed to delete crate '{}': {}", key, e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByKey(String key) {
        ensureCache();
        if (keyCache.containsKey(key)) return true;
        try {
            return getDatabase().querySingle(EXISTS_BY_KEY,
                stmt -> stmt.setString(1, key),
                (rs) -> rs.getLong(1) > 0).join().orElse(false);
        } catch (Exception e) {
            LOGGER.error("Failed to check crate existence: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public long count() {
        ensureCache();
        return keyCache.size();
    }
}
