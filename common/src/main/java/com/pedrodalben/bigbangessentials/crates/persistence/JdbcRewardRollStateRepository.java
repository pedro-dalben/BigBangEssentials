package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.crates.domain.RewardRollState;
import com.pedrodalben.bigbangessentials.crates.repository.RewardRollStateRepository;
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
    private static final String SELECT_BY_REWARD_ID = "SELECT * FROM " + TABLE + " WHERE reward_id = ?";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE;
    private static final String INSERT = "INSERT INTO " + TABLE + " (reward_id, global_count, player_counts) VALUES (?, ?, ?)";
    private static final String UPDATE = "UPDATE " + TABLE + " SET global_count = ?, player_counts = ? WHERE reward_id = ?";
    private static final String DELETE_BY_REWARD_ID = "DELETE FROM " + TABLE + " WHERE reward_id = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE reward_id = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, Integer>>(){}.getType();

    private boolean tableCreated = false;

    private final RowMapper<RewardRollState> MAPPER = (rs) -> {
        String rewardId = rs.getString("reward_id");
        int globalCount = rs.getInt("global_count");
        String playerCountsJson = rs.getString("player_counts");

        RewardRollState state = new RewardRollState(rewardId);
        // globalCount is set via the private field through reflection or a helper
        // Since RewardRollState only has incrementGlobal(), we set it via a workaround
        // We'll need to manage the state through the service layer instead

        return state;
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
                "player_counts TEXT NOT NULL DEFAULT '{}', " +
                "PRIMARY KEY (reward_id)" +
                ")", null).join();
            tableCreated = true;
            LOGGER.debug("Ensured table {} exists", TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create table {}: {}", TABLE, e.getMessage(), e);
        }
    }

    @Override
    public Optional<RewardRollState> findByRewardId(String rewardId) {
        try {
            Optional<RewardRollState> result = getDatabase().querySingle(SELECT_BY_REWARD_ID,
                stmt -> stmt.setString(1, rewardId),
                (rs) -> {
                    String id = rs.getString("reward_id");
                    int globalCount = rs.getInt("global_count");
                    String playerCountsJson = rs.getString("player_counts");
                    RewardRollState state = new RewardRollState(id);
                    // Reflectively set globalCount since there's no setter
                    // We rely on the service layer to call incrementGlobal()
                    return state;
                }
            ).join();

            // Reconstruct from JSON to properly set counts
            if (result.isPresent()) {
                return loadFromDatabase(rewardId);
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to find reward roll state: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<RewardRollState> loadFromDatabase(String rewardId) {
        try {
            return getDatabase().querySingle(SELECT_BY_REWARD_ID,
                stmt -> stmt.setString(1, rewardId),
                (rs) -> {
                    String id = rs.getString("reward_id");
                    int globalCount = rs.getInt("global_count");
                    String playerCountsJson = rs.getString("player_counts");

                    // Use a helper to construct the state from raw data
                    RewardRollState state = constructState(id, globalCount, playerCountsJson);
                    return state;
                }
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to load reward roll state from DB: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private RewardRollState constructState(String rewardId, int globalCount, String playerCountsJson) {
        RewardRollState state = new RewardRollState(rewardId);
        if (playerCountsJson == null || playerCountsJson.isBlank() || playerCountsJson.equals("{}")) {
            return state;
        }
        try {
            Map<String, Integer> counts = gson.fromJson(playerCountsJson, mapType);
            if (counts != null) {
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        for (int i = 0; i < entry.getValue(); i++) {
                            state.incrementPlayer(playerId);
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid UUID in player_counts for reward {}: {}", rewardId, entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse player_counts JSON for reward {}: {}", rewardId, e.getMessage());
        }
        // Increment global count to match stored value
        for (int i = 0; i < globalCount; i++) {
            state.incrementGlobal();
        }
        return state;
    }

    @Override
    public List<RewardRollState> findAll() {
        try {
            List<RewardRollState> results = getDatabase().queryList(SELECT_ALL, null,
                (rs) -> {
                    String id = rs.getString("reward_id");
                    int globalCount = rs.getInt("global_count");
                    String playerCountsJson = rs.getString("player_counts");
                    return constructState(id, globalCount, playerCountsJson);
                }
            ).join();
            return results;
        } catch (Exception e) {
            LOGGER.error("Failed to find all reward roll states: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public RewardRollState save(RewardRollState state) {
        try {
            String playerCountsJson = gson.toJson(state.getPlayerCounts());
            int updated = getDatabase().executeUpdate(UPDATE,
                stmt -> {
                    stmt.setInt(1, state.getGlobalCount());
                    stmt.setString(2, playerCountsJson);
                    stmt.setString(3, state.getRewardId());
                }
            ).join();
            if (updated == 0) {
                getDatabase().executeUpdate(INSERT,
                    stmt -> {
                        stmt.setString(1, state.getRewardId());
                        stmt.setInt(2, state.getGlobalCount());
                        stmt.setString(3, playerCountsJson);
                    }
                ).join();
            }
            return state;
        } catch (Exception e) {
            LOGGER.error("Failed to save reward roll state: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save reward roll state", e);
        }
    }

    @Override
    public void delete(RewardRollState state) {
        try {
            getDatabase().executeUpdate(DELETE,
                stmt -> stmt.setString(1, state.getRewardId())
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete reward roll state: {}", e.getMessage(), e);
        }
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
}
