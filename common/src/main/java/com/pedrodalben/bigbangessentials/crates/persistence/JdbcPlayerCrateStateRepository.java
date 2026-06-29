package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.crates.domain.PlayerCrateState;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerCrateStateRepository;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcPlayerCrateStateRepository extends JdbcRepository implements PlayerCrateStateRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPlayerCrateStateRepository.class);

    private static final String TABLE = "crate_player_state";
    private static final String SELECT_BY_PLAYER_AND_CRATE = "SELECT * FROM " + TABLE + " WHERE player_uuid = ? AND crate_id = ?";
    private static final String SELECT_BY_PLAYER = "SELECT * FROM " + TABLE + " WHERE player_uuid = ?";
    private static final String SELECT_BY_CRATE = "SELECT * FROM " + TABLE + " WHERE crate_id = ?";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE;
    private static final String INSERT = "INSERT INTO " + TABLE + " (player_uuid, crate_id, cooldown_until, total_opened, milestone_progress, latest_opened_at) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE " + TABLE + " SET cooldown_until = ?, total_opened = ?, milestone_progress = ?, latest_opened_at = ? WHERE player_uuid = ? AND crate_id = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE player_uuid = ? AND crate_id = ?";
    private static final String DELETE_BY_PLAYER = "DELETE FROM " + TABLE + " WHERE player_uuid = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private static final String RECORD_OPENING = "INSERT INTO " + TABLE + " (player_uuid, crate_id, total_opened, latest_opened_at, milestone_progress) "
        + "VALUES (?, ?, 1, ?, 1) "
        + "ON CONFLICT(player_uuid, crate_id) DO UPDATE SET "
        + "total_opened = total_opened + 1, "
        + "latest_opened_at = ?, "
        + "milestone_progress = milestone_progress + 1";

    private static final String START_COOLDOWN = "INSERT INTO " + TABLE + " (player_uuid, crate_id, cooldown_until) "
        + "VALUES (?, ?, ?) "
        + "ON CONFLICT(player_uuid, crate_id) DO UPDATE SET "
        + "cooldown_until = ?";

    private static final String CLEAR_COOLDOWN = "UPDATE " + TABLE + " SET cooldown_until = 0 WHERE player_uuid = ? AND crate_id = ?";

    private boolean tableCreated = false;

    private final RowMapper<PlayerCrateState> MAPPER = (rs) -> {
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        String crateId = rs.getString("crate_id");
        PlayerCrateState state = new PlayerCrateState(playerId, crateId);

        long cooldownUntil = rs.getLong("cooldown_until");
        if (cooldownUntil > 0) {
            state.setCooldownUntil(cooldownUntil);
        }
        state.setTotalOpened(rs.getInt("total_opened"));
        state.setMilestoneProgress(rs.getInt("milestone_progress"));
        long latestOpenedAt = rs.getLong("latest_opened_at");
        if (latestOpenedAt > 0) {
            state.setLatestOpenedAt(java.time.Instant.ofEpochMilli(latestOpenedAt));
        }

        return state;
    };

    public JdbcPlayerCrateStateRepository() {
        ensureTable();
    }

    private synchronized void ensureTable() {
        if (tableCreated) return;
        try {
            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "crate_id VARCHAR(64) NOT NULL, " +
                "cooldown_until BIGINT NOT NULL DEFAULT 0, " +
                "total_opened INT NOT NULL DEFAULT 0, " +
                "milestone_progress INT NOT NULL DEFAULT 0, " +
                "latest_opened_at BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (player_uuid, crate_id)" +
                ")", null).join();
            tableCreated = true;
            LOGGER.debug("Ensured table {} exists", TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create table {}: {}", TABLE, e.getMessage(), e);
        }
    }

    @Override
    public Optional<PlayerCrateState> findByPlayerAndCrate(UUID playerId, String crateId) {
        try {
            return getDatabase().querySingle(SELECT_BY_PLAYER_AND_CRATE,
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, crateId);
                },
                (rs) -> mapState(rs)
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find player crate state: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<PlayerCrateState> findByPlayer(UUID playerId) {
        try {
            return getDatabase().queryList(SELECT_BY_PLAYER,
                stmt -> stmt.setString(1, playerId.toString()),
                (rs) -> mapState(rs)
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find player states: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<PlayerCrateState> findByCrate(String crateId) {
        try {
            return getDatabase().queryList(SELECT_BY_CRATE,
                stmt -> stmt.setString(1, crateId),
                (rs) -> mapState(rs)
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find crate states: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<PlayerCrateState> findAll() {
        try {
            return getDatabase().queryList(SELECT_ALL, null, (rs) -> mapState(rs)).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find all player states: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private PlayerCrateState mapState(ResultSet rs) throws java.sql.SQLException {
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        String crateId = rs.getString("crate_id");
        PlayerCrateState state = new PlayerCrateState(playerId, crateId);

        long cooldownUntil = rs.getLong("cooldown_until");
        if (cooldownUntil > 0) {
            state.setCooldownUntil(cooldownUntil);
        }
        state.setTotalOpened(rs.getInt("total_opened"));
        state.setMilestoneProgress(rs.getInt("milestone_progress"));
        long latestOpenedAt = rs.getLong("latest_opened_at");
        if (latestOpenedAt > 0) {
            state.setLatestOpenedAt(java.time.Instant.ofEpochMilli(latestOpenedAt));
        }

        return state;
    }

    @Override
    public PlayerCrateState save(PlayerCrateState state) {
        try {
            int updated = getDatabase().executeUpdate(UPDATE,
                stmt -> {
                    stmt.setLong(1, state.getCooldownUntil());
                    stmt.setInt(2, state.getTotalOpened());
                    stmt.setInt(3, state.getMilestoneProgress());
                    stmt.setLong(4, state.getLatestOpenedAt() != null ? state.getLatestOpenedAt().toEpochMilli() : 0);
                    stmt.setString(5, state.getPlayerId().toString());
                    stmt.setString(6, state.getCrateId());
                }
            ).join();
            if (updated == 0) {
                getDatabase().executeUpdate(INSERT,
                    stmt -> {
                        stmt.setString(1, state.getPlayerId().toString());
                        stmt.setString(2, state.getCrateId());
                        stmt.setLong(3, state.getCooldownUntil());
                        stmt.setInt(4, state.getTotalOpened());
                        stmt.setInt(5, state.getMilestoneProgress());
                        stmt.setLong(6, state.getLatestOpenedAt() != null ? state.getLatestOpenedAt().toEpochMilli() : 0);
                    }
                ).join();
            }
            return state;
        } catch (Exception e) {
            LOGGER.error("Failed to save player crate state: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save player crate state", e);
        }
    }

    @Override
    public void delete(PlayerCrateState state) {
        try {
            getDatabase().executeUpdate(DELETE,
                stmt -> {
                    stmt.setString(1, state.getPlayerId().toString());
                    stmt.setString(2, state.getCrateId());
                }
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete player crate state: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteByPlayer(UUID playerId) {
        try {
            getDatabase().executeUpdate(DELETE_BY_PLAYER,
                stmt -> stmt.setString(1, playerId.toString())
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete states by player: {}", e.getMessage(), e);
        }
    }

    @Override
    public long count() {
        try {
            return getDatabase().querySingle(COUNT, null, (rs) -> rs.getLong(1)).join().orElse(0L);
        } catch (Exception e) {
            LOGGER.error("Failed to count player crate states: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public void startCooldown(UUID playerId, String crateId, long cooldownUntil) {
        try {
            getDatabase().executeUpdate(START_COOLDOWN,
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, crateId);
                    stmt.setLong(3, cooldownUntil);
                    stmt.setLong(4, cooldownUntil);
                }
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to start cooldown: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start cooldown", e);
        }
    }

    @Override
    public PlayerCrateState recordOpening(UUID playerId, String crateId) {
        try {
            long now = System.currentTimeMillis();
            getDatabase().executeUpdate(RECORD_OPENING,
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, crateId);
                    stmt.setLong(3, now);
                    stmt.setLong(4, now);
                }
            ).join();
            return findByPlayerAndCrate(playerId, crateId)
                .orElseThrow(() -> new IllegalStateException("Failed to read state after recording opening"));
        } catch (Exception e) {
            LOGGER.error("Failed to record opening: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to record opening", e);
        }
    }

    @Override
    public void clearCooldown(UUID playerId, String crateId) {
        try {
            getDatabase().executeUpdate(CLEAR_COOLDOWN,
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, crateId);
                }
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to clear cooldown: {}", e.getMessage(), e);
        }
    }
}
