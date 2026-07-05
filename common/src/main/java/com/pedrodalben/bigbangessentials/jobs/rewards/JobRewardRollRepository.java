package com.pedrodalben.bigbangessentials.jobs.rewards;

import com.pedrodalben.bigbangessentials.database.api.DatabaseAPI;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JobRewardRollRepository extends JdbcRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobRewardRollRepository.class);
    private static final JobRewardRollRepository INSTANCE = new JobRewardRollRepository();

    public static JobRewardRollRepository getInstance() {
        return INSTANCE;
    }

    private JobRewardRollRepository() {}

    public void saveRoll(JobKeyRollResult roll) {
        if (!DatabaseAPI.isAvailable() || roll == null) return;
        String sql = "INSERT INTO bbe_jobs_key_rolls (roll_id, action_id, uuid, job_id, job_level, base_chance, action_weight, final_chance, random_value, success, reason, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        getDatabase().executeUpdate("saveKeyRoll", sql, stmt -> {
            stmt.setString(1, roll.rollId());
            stmt.setString(2, roll.actionId());
            stmt.setString(3, roll.playerUuid().toString());
            stmt.setString(4, roll.jobId());
            stmt.setInt(5, roll.jobLevel());
            stmt.setDouble(6, roll.baseChance());
            stmt.setDouble(7, roll.actionWeight());
            stmt.setDouble(8, roll.finalChance());
            stmt.setDouble(9, roll.randomValue());
            stmt.setBoolean(10, roll.success());
            stmt.setString(11, roll.reason() != null ? roll.reason() : "");
            stmt.setLong(12, roll.createdAt());
        });
    }

    public int countSuccessfulRollsForJobSince(UUID playerUuid, String jobId, long sinceTimestamp) {
        if (!DatabaseAPI.isAvailable() || playerUuid == null || jobId == null) return 0;
        String sql = "SELECT COUNT(*) AS total FROM bbe_jobs_key_rolls WHERE uuid = ? AND job_id = ? AND success = 1 AND created_at >= ?";
        return getDatabase().querySingle("countJobRolls", sql, stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, jobId);
            stmt.setLong(3, sinceTimestamp);
        }, rs -> rs.getInt("total")).join().orElse(0);
    }

    public int countTotalSuccessfulRollsSince(UUID playerUuid, long sinceTimestamp) {
        if (!DatabaseAPI.isAvailable() || playerUuid == null) return 0;
        String sql = "SELECT COUNT(*) AS total FROM bbe_jobs_key_rolls WHERE uuid = ? AND success = 1 AND created_at >= ?";
        return getDatabase().querySingle("countTotalRolls", sql, stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setLong(2, sinceTimestamp);
        }, rs -> rs.getInt("total")).join().orElse(0);
    }

    public long getLatestSuccessfulRollTimestamp(UUID playerUuid, String jobId) {
        if (!DatabaseAPI.isAvailable() || playerUuid == null || jobId == null) return 0L;
        String sql = "SELECT MAX(created_at) AS latest FROM bbe_jobs_key_rolls WHERE uuid = ? AND job_id = ? AND success = 1";
        return getDatabase().querySingle("getLatestRoll", sql, stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, jobId);
        }, rs -> rs.getLong("latest")).join().orElse(0L);
    }
}
