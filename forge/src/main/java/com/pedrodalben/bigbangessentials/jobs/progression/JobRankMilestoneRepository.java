package com.pedrodalben.bigbangessentials.jobs.progression;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for persisting and retrieving player unlocked rank milestones.
 */
public class JobRankMilestoneRepository extends JdbcRepository {

    public JobRankMilestoneRepository() {
        super();
    }

    private boolean isDatabaseAvailable() {
        return DatabaseManager.getInstance().isReady();
    }

    public CompletableFuture<Set<String>> loadPlayerMilestones(UUID uuid) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(new HashSet<>());
        }
        String sql = "SELECT milestone_id FROM bbe_job_rank_milestones WHERE uuid = ?";
        return getDatabase().queryList("loadPlayerMilestones", sql,
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> rs.getString("milestone_id").toLowerCase()
        ).thenApply(HashSet::new);
    }

    public CompletableFuture<Void> savePlayerMilestone(UUID uuid, String milestoneId, String sourceRankId, long achievedAt) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_job_rank_milestones (uuid, milestone_id, source_rank_id, achieved_at) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE achieved_at = VALUES(achieved_at)";
        } else {
            sql = "INSERT OR IGNORE INTO bbe_job_rank_milestones (uuid, milestone_id, source_rank_id, achieved_at) VALUES (?, ?, ?, ?)";
        }
        return getDatabase().executeUpdate("savePlayerMilestone", sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, milestoneId.toLowerCase());
            stmt.setString(3, sourceRankId);
            stmt.setLong(4, achievedAt);
        }).thenApply(rows -> null);
    }
}
