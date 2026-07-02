package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.crates.domain.RewardRollState;
import com.pedrodalben.bigbangessentials.crates.repository.RewardRollStateRepository;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class JdbcRewardRollStateRepository extends JdbcRepository implements RewardRollStateRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcRewardRollStateRepository.class);

    private static final String TABLE = "crate_reward_roll_state";
    private static final String PLAYER_TABLE = "crate_reward_player_counts";
    private static final String SELECT_BY_REWARD_ID = "SELECT * FROM " + TABLE + " WHERE reward_id = ?";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE;
    private static final String INSERT = "INSERT INTO " + TABLE + " (reward_id, global_count, player_counts) VALUES (?, ?, ?)";
    private static final String UPDATE = "UPDATE " + TABLE + " SET global_count = ?, player_counts = ? WHERE reward_id = ?";
    private static final String DELETE_BY_REWARD_ID = "DELETE FROM " + TABLE + " WHERE reward_id = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE reward_id = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private static final String SELECT_GLOBAL_COUNT = "SELECT global_count FROM " + TABLE + " WHERE reward_id = ?";
    private static final String SELECT_PLAYER_COUNT = "SELECT count FROM " + PLAYER_TABLE + " WHERE reward_id = ? AND player_uuid = ?";
    private static final String RESERVE_GLOBAL = "UPDATE " + TABLE + " SET global_count = global_count + 1 WHERE reward_id = ? AND global_count < ?";
    private static final String RELEASE_GLOBAL = "UPDATE " + TABLE + " SET global_count = CASE WHEN global_count > 0 THEN global_count - 1 ELSE 0 END WHERE reward_id = ?";
    private static final String RESERVE_PLAYER = "UPDATE " + PLAYER_TABLE + " SET count = count + 1 WHERE reward_id = ? AND player_uuid = ? AND count < ?";
    private static final String RELEASE_PLAYER = "UPDATE " + PLAYER_TABLE + " SET count = CASE WHEN count > 0 THEN count - 1 ELSE 0 END WHERE reward_id = ? AND player_uuid = ?";

    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, Integer>>(){}.getType();

    private boolean tableCreated = false;

    private final RowMapper<RewardRollState> MAPPER = (rs) -> {
        String rewardId = rs.getString("reward_id");
        int globalCount = rs.getInt("global_count");
        String playerCountsJson = rs.getString("player_counts");
        return constructState(rewardId, globalCount, playerCountsJson);
    };

    public JdbcRewardRollStateRepository() {
        ensureTable();
    }

    private synchronized void ensureTable() {
        if (tableCreated) return;
        try {
            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "reward_id VARCHAR(64) NOT NULL, " +
                "global_count INT NOT NULL DEFAULT 0, " +
                "player_counts TEXT, " +
                "PRIMARY KEY (reward_id)" +
                ")", null).join();

            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + PLAYER_TABLE + " (" +
                "reward_id VARCHAR(64) NOT NULL, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "count INT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (reward_id, player_uuid)" +
                ")", null).join();

            tableCreated = true;
            LOGGER.debug("Ensured tables {}, {} exist", TABLE, PLAYER_TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create tables {}, {}: {}", TABLE, PLAYER_TABLE, e.getMessage(), e);
        }
    }

    private RewardRollState constructState(String rewardId, int globalCount, String playerCountsJson) {
        Map<UUID, Integer> map = new HashMap<>();
        try {
            if (playerCountsJson != null && !playerCountsJson.isBlank()) {
                Map<String, Integer> raw = gson.fromJson(playerCountsJson, mapType);
                if (raw != null) {
                    raw.forEach((k, v) -> map.put(UUID.fromString(k), v));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse player_counts JSON for reward {}: {}", rewardId, e.getMessage());
        }
        return new RewardRollState(rewardId, globalCount, map);
    }

    @Override
    public Optional<RewardRollState> findByRewardId(String rewardId) {
        try {
            return getDatabase().querySingle(SELECT_BY_REWARD_ID,
                stmt -> stmt.setString(1, rewardId),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find reward roll state by ID {}: {}", rewardId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<RewardRollState> findAll() {
        try {
            return getDatabase().queryList(SELECT_ALL, null, MAPPER).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find all reward roll states: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public RewardRollState save(RewardRollState state) {
        try {
            Map<String, Integer> raw = new HashMap<>();
            state.getPlayerCounts().forEach((k, v) -> raw.put(k.toString(), v));
            String json = gson.toJson(raw);

            int updated = getDatabase().executeUpdate(UPDATE,
                stmt -> {
                    stmt.setInt(1, state.getGlobalCount());
                    stmt.setString(2, json);
                    stmt.setString(3, state.getRewardId());
                }
            ).join();

            if (updated == 0) {
                getDatabase().executeUpdate(INSERT,
                    stmt -> {
                        stmt.setString(1, state.getRewardId());
                        stmt.setInt(2, state.getGlobalCount());
                        stmt.setString(3, json);
                    }
                ).join();
            }
            return state;
        } catch (Exception e) {
            LOGGER.error("Failed to save reward roll state for reward {}: {}", state.getRewardId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save reward roll state", e);
        }
    }

    @Override
    public void delete(RewardRollState state) {
        deleteByRewardId(state.getRewardId());
    }

    @Override
    public void deleteByRewardId(String rewardId) {
        try {
            getDatabase().executeUpdate(DELETE_BY_REWARD_ID,
                stmt -> stmt.setString(1, rewardId)
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete reward roll state by ID: {}", e.getMessage(), e);
        }
    }

    @Override
    public long count() {
        try {
            return getDatabase().querySingle(COUNT, null, (rs) -> rs.getLong(1)).join().orElse(0L);
        } catch (Exception e) {
            LOGGER.error("Failed to count reward roll states: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public int incrementGlobalCount(String rewardId) {
        try {
            getDatabase().executeUpdate(incrementGlobalSql(),
                stmt -> stmt.setString(1, rewardId)
            ).join();
            return getDatabase().querySingle(SELECT_GLOBAL_COUNT,
                stmt -> stmt.setString(1, rewardId),
                (rs) -> rs.getInt("global_count")
            ).join().orElse(1);
        } catch (Exception e) {
            LOGGER.error("Failed to increment global count for reward {}: {}", rewardId, e.getMessage(), e);
            throw new RuntimeException("Failed to increment global count", e);
        }
    }

    @Override
    public int incrementPlayerCount(String rewardId, UUID playerId) {
        try {
            getDatabase().executeUpdate(incrementPlayerSql(),
                stmt -> {
                    stmt.setString(1, rewardId);
                    stmt.setString(2, playerId.toString());
                }
            ).join();
            return getDatabase().querySingle(SELECT_PLAYER_COUNT,
                stmt -> {
                    stmt.setString(1, rewardId);
                    stmt.setString(2, playerId.toString());
                },
                (rs) -> rs.getInt("count")
            ).join().orElse(1);
        } catch (Exception e) {
            LOGGER.error("Failed to increment player count for reward {}: {}", rewardId, e.getMessage(), e);
            throw new RuntimeException("Failed to increment player count", e);
        }
    }

    @Override
    public int getPlayerCount(String rewardId, UUID playerId) {
        try {
            return getDatabase().querySingle(SELECT_PLAYER_COUNT,
                stmt -> {
                    stmt.setString(1, rewardId);
                    stmt.setString(2, playerId.toString());
                },
                (rs) -> rs.getInt("count")
            ).join().orElse(0);
        } catch (Exception e) {
            LOGGER.error("Failed to get player count for reward {}: {}", rewardId, e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public boolean reserveGlobalLimit(String rewardId, int globalLimit) {
        if (globalLimit <= 0) {
            incrementGlobalCount(rewardId);
            return true;
        }
        try {
            getDatabase().executeUpdate(initGlobalSql(), stmt -> stmt.setString(1, rewardId)).join();
            int updated = getDatabase().executeUpdate(RESERVE_GLOBAL, stmt -> {
                stmt.setString(1, rewardId);
                stmt.setInt(2, globalLimit);
            }).join();
            return updated > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to reserve global limit for reward {}: {}", rewardId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean reservePlayerLimit(String rewardId, UUID playerId, int playerLimit) {
        if (playerLimit <= 0) {
            incrementPlayerCount(rewardId, playerId);
            return true;
        }
        try {
            getDatabase().executeUpdate(initPlayerSql(), stmt -> {
                stmt.setString(1, rewardId);
                stmt.setString(2, playerId.toString());
            }).join();
            int updated = getDatabase().executeUpdate(RESERVE_PLAYER, stmt -> {
                stmt.setString(1, rewardId);
                stmt.setString(2, playerId.toString());
                stmt.setInt(3, playerLimit);
            }).join();
            return updated > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to reserve player limit for reward {}: {}", rewardId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void releaseGlobalLimit(String rewardId) {
        try {
            getDatabase().executeUpdate(RELEASE_GLOBAL, stmt -> stmt.setString(1, rewardId)).join();
        } catch (Exception e) {
            LOGGER.error("Failed to release global limit for reward {}: {}", rewardId, e.getMessage(), e);
        }
    }

    @Override
    public void releasePlayerLimit(String rewardId, UUID playerId) {
        try {
            getDatabase().executeUpdate(RELEASE_PLAYER, stmt -> {
                stmt.setString(1, rewardId);
                stmt.setString(2, playerId.toString());
            }).join();
        } catch (Exception e) {
            LOGGER.error("Failed to release player limit for reward {}: {}", rewardId, e.getMessage(), e);
        }
    }

    private String incrementGlobalSql() {
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            return "INSERT INTO " + TABLE + " (reward_id, global_count, player_counts) VALUES (?, 1, '{}') "
                + "ON DUPLICATE KEY UPDATE global_count = global_count + 1";
        }
        return "INSERT INTO " + TABLE + " (reward_id, global_count, player_counts) VALUES (?, 1, '{}') "
            + "ON CONFLICT(reward_id) DO UPDATE SET global_count = global_count + 1";
    }

    private String incrementPlayerSql() {
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            return "INSERT INTO " + PLAYER_TABLE + " (reward_id, player_uuid, count) VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE count = count + 1";
        }
        return "INSERT INTO " + PLAYER_TABLE + " (reward_id, player_uuid, count) VALUES (?, ?, 1) "
            + "ON CONFLICT(reward_id, player_uuid) DO UPDATE SET count = count + 1";
    }

    private String initGlobalSql() {
        return DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
            ? "INSERT IGNORE INTO " + TABLE + " (reward_id, global_count, player_counts) VALUES (?, 0, '{}')"
            : "INSERT OR IGNORE INTO " + TABLE + " (reward_id, global_count, player_counts) VALUES (?, 0, '{}')";
    }

    private String initPlayerSql() {
        return DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
            ? "INSERT IGNORE INTO " + PLAYER_TABLE + " (reward_id, player_uuid, count) VALUES (?, ?, 0)"
            : "INSERT OR IGNORE INTO " + PLAYER_TABLE + " (reward_id, player_uuid, count) VALUES (?, ?, 0)";
    }
}
