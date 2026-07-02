package com.pedrodalben.bigbangessentials.crates.persistence;

import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.repository.CrateLocationRepository;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-backed CrateLocationRepository. Primary source of truth is the database.
 */
public class JdbcCrateLocationRepository extends JdbcRepository implements CrateLocationRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcCrateLocationRepository.class);

    private static final String TABLE = "crate_locations";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE;
    private static final String SELECT_BY_ID = "SELECT * FROM " + TABLE + " WHERE id = ?";
    private static final String SELECT_BY_CRATE = "SELECT * FROM " + TABLE + " WHERE crate_id = ?";
    private static final String SELECT_BY_POS = "SELECT * FROM " + TABLE + " WHERE world = ? AND x = ? AND y = ? AND z = ?";
    private static final String INSERT = "INSERT INTO " + TABLE + " (id, crate_id, world, x, y, z, hologram_enabled, particles_enabled, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE " + TABLE + " SET crate_id = ?, world = ?, x = ?, y = ?, z = ?, hologram_enabled = ?, particles_enabled = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE id = ?";
    private static final String DELETE_BY_CRATE = "DELETE FROM " + TABLE + " WHERE crate_id = ?";

    private final ConcurrentHashMap<UUID, CrateLocation> idCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<CrateLocation>> crateCache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    private final RowMapper<CrateLocation> MAPPER = (rs) -> {
        UUID id = UUID.fromString(rs.getString("id"));
        String crateId = rs.getString("crate_id");
        String worldStr = rs.getString("world");
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");

        ResourceKey<Level> dimension = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.parse(worldStr)
        );

        CrateLocation loc = new CrateLocation(id, crateId, dimension, new BlockPos(x, y, z));
        return loc;
    };

    private void ensureCache() {
        if (cacheLoaded) return;
        synchronized (this) {
            if (cacheLoaded) return;
            try {
                List<CrateLocation> all = getDatabase().queryList(SELECT_ALL, null, MAPPER).join();
                idCache.clear();
                crateCache.clear();
                for (CrateLocation loc : all) {
                    idCache.put(loc.getId(), loc);
                    crateCache.computeIfAbsent(loc.getCrateId(), k -> new ArrayList<>()).add(loc);
                }
                cacheLoaded = true;
                LOGGER.debug("Loaded {} crate locations into cache", all.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load location cache: {}", e.getMessage(), e);
            }
        }
    }

    private void invalidateCache() {
        cacheLoaded = false;
        idCache.clear();
        crateCache.clear();
    }

    @Override
    public Optional<CrateLocation> findById(UUID id) {
        ensureCache();
        return Optional.ofNullable(idCache.get(id));
    }

    @Override
    public List<CrateLocation> findAll() {
        ensureCache();
        return new ArrayList<>(idCache.values());
    }

    @Override
    public List<CrateLocation> findByCrateId(String crateId) {
        ensureCache();
        return crateCache.getOrDefault(crateId, List.of());
    }

    @Override
    public Optional<CrateLocation> findByPosition(ResourceKey<Level> dimension, BlockPos position) {
        ensureCache();
        String worldStr = dimension.location().toString();
        return idCache.values().stream()
            .filter(loc -> loc.getDimension().location().toString().equals(worldStr)
                && loc.getPosition().equals(position))
            .findFirst();
    }

    @Override
    public CrateLocation save(CrateLocation location) {
        long now = System.currentTimeMillis();
        try {
            int updated = getDatabase().executeUpdate(UPDATE,
                stmt -> {
                    stmt.setString(1, location.getCrateId());
                    stmt.setString(2, location.getDimension().location().toString());
                    stmt.setInt(3, location.getPosition().getX());
                    stmt.setInt(4, location.getPosition().getY());
                    stmt.setInt(5, location.getPosition().getZ());
                    stmt.setBoolean(6, true);
                    stmt.setBoolean(7, true);
                    stmt.setString(8, location.getId().toString());
                }
            ).join();

            if (updated == 0) {
                getDatabase().executeUpdate(INSERT,
                    stmt -> {
                        stmt.setString(1, location.getId().toString());
                        stmt.setString(2, location.getCrateId());
                        stmt.setString(3, location.getDimension().location().toString());
                        stmt.setInt(4, location.getPosition().getX());
                        stmt.setInt(5, location.getPosition().getY());
                        stmt.setInt(6, location.getPosition().getZ());
                        stmt.setBoolean(7, true);
                        stmt.setBoolean(8, true);
                        stmt.setLong(9, now);
                    }
                ).join();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save location: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save location", e);
        }
        invalidateCache();
        return location;
    }

    @Override
    public void delete(CrateLocation location) {
        deleteById(location.getId());
    }

    @Override
    public void deleteById(UUID id) {
        try {
            getDatabase().executeUpdate(DELETE,
                stmt -> stmt.setString(1, id.toString())).join();
            invalidateCache();
        } catch (Exception e) {
            LOGGER.error("Failed to delete location: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<CrateLocation> findByDimension(ResourceKey<Level> dimension) {
        ensureCache();
        String worldStr = dimension.location().toString();
        return idCache.values().stream()
            .filter(loc -> loc.getDimension().location().toString().equals(worldStr))
            .toList();
    }

    @Override
    public long count() {
        ensureCache();
        return idCache.size();
    }

    @Override
    public void deleteByCrateId(String crateId) {
        try {
            getDatabase().executeUpdate(DELETE_BY_CRATE,
                stmt -> stmt.setString(1, crateId)).join();
            invalidateCache();
        } catch (Exception e) {
            LOGGER.error("Failed to delete locations by crate: {}", e.getMessage(), e);
        }
    }
}
