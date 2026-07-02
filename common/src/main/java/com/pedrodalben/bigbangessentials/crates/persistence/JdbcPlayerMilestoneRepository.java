package com.pedrodalben.bigbangessentials.crates.persistence;

import com.pedrodalben.bigbangessentials.crates.domain.PlayerMilestoneRecord;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerMilestoneRepository;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public class JdbcPlayerMilestoneRepository extends JdbcRepository implements PlayerMilestoneRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPlayerMilestoneRepository.class);

    private static final String TABLE = "crate_player_milestones";
    private static final String SELECT = "SELECT * FROM " + TABLE + " WHERE player_uuid = ? AND crate_id = ? AND milestone_id = ? AND threshold_mult = ?";

    private boolean tableCreated = false;

    private final RowMapper<PlayerMilestoneRecord> MAPPER = rs -> new PlayerMilestoneRecord(
        UUID.fromString(rs.getString("player_uuid")),
        rs.getString("crate_id"),
        rs.getString("milestone_id"),
        rs.getInt("threshold_mult"),
        rs.getLong("reached_at"),
        rs.getLong("delivered_at"),
        rs.getString("status"),
        rs.getString("opening_id"),
        rs.getBoolean("repeatable")
    );

    public JdbcPlayerMilestoneRepository() {
        ensureTable();
    }

    private synchronized void ensureTable() {
        if (tableCreated) return;
        try {
            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "crate_id VARCHAR(64) NOT NULL, " +
                "milestone_id VARCHAR(64) NOT NULL, " +
                "threshold_mult INT NOT NULL DEFAULT 1, " +
                "reached_at BIGINT NOT NULL, " +
                "delivered_at BIGINT NOT NULL, " +
                "status VARCHAR(32) NOT NULL, " +
                "opening_id VARCHAR(36), " +
                "repeatable BOOLEAN NOT NULL, " +
                "PRIMARY KEY (player_uuid, crate_id, milestone_id, threshold_mult)" +
                ")", null).join();
            tableCreated = true;
            LOGGER.debug("Ensured table {} exists", TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create table {}: {}", TABLE, e.getMessage(), e);
        }
    }

    @Override
    public Optional<PlayerMilestoneRecord> find(UUID playerUuid, String crateId, String milestoneId, int thresholdMult) {
        try {
            return getDatabase().querySingle(SELECT,
                stmt -> {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, crateId);
                    stmt.setString(3, milestoneId);
                    stmt.setInt(4, thresholdMult);
                },
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find milestone record: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean recordDelivery(UUID playerUuid, String crateId, String milestoneId, int thresholdMult, long reachedAt, long deliveredAt, String openingId, boolean repeatable) {
        try {
            int inserted = getDatabase().executeUpdate(insertSql(),
                stmt -> {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, crateId);
                    stmt.setString(3, milestoneId);
                    stmt.setInt(4, thresholdMult);
                    stmt.setLong(5, reachedAt);
                    stmt.setLong(6, deliveredAt);
                    stmt.setString(7, openingId);
                    stmt.setBoolean(8, repeatable);
                }
            ).join();
            return inserted > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to record milestone delivery: {}", e.getMessage(), e);
            return false;
        }
    }

    private String insertSql() {
        return DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
            ? "INSERT IGNORE INTO " + TABLE + " (player_uuid, crate_id, milestone_id, threshold_mult, reached_at, delivered_at, status, opening_id, repeatable) VALUES (?, ?, ?, ?, ?, ?, 'DELIVERED', ?, ?)"
            : "INSERT OR IGNORE INTO " + TABLE + " (player_uuid, crate_id, milestone_id, threshold_mult, reached_at, delivered_at, status, opening_id, repeatable) VALUES (?, ?, ?, ?, ?, ?, 'DELIVERED', ?, ?)";
    }
}
