package com.pedrodalben.bigbangessentials.jobs.slot;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for storing and retrieving player job slots.
 */
public class JobSlotRepository extends JdbcRepository {

    public JobSlotRepository() {
        super();
    }

    private boolean isDatabaseAvailable() {
        return DatabaseManager.getInstance().isReady();
    }

    public record JobSlotDb(String slotType, String jobId, long activatedAt, long lastChangedAt, long cooldownUntil, String source) {}

    public CompletableFuture<Map<String, JobSlotDb>> loadPlayerSlots(UUID uuid) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        String sql = "SELECT slot_type, job_id, activated_at, last_changed_at, cooldown_until, source FROM bbe_job_slots WHERE uuid = ?";
        return getDatabase().queryList("loadPlayerSlots", sql,
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> {
                    String slotType = rs.getString("slot_type").toUpperCase();
                    String jobId = rs.getString("job_id");
                    long activatedAt = rs.getLong("activated_at");
                    long lastChangedAt = rs.getLong("last_changed_at");
                    long cooldownUntil = rs.getLong("cooldown_until");
                    String source = rs.getString("source");
                    return new JobSlotDb(slotType, jobId != null ? jobId.toLowerCase() : "", activatedAt, lastChangedAt, cooldownUntil, source);
                }
        ).thenApply(list -> {
            Map<String, JobSlotDb> map = new HashMap<>();
            for (JobSlotDb db : list) {
                map.put(db.slotType(), db);
            }
            return map;
        });
    }

    public CompletableFuture<Void> savePlayerSlot(UUID uuid, String slotType, String jobId, long activatedAt, long lastChangedAt, long cooldownUntil, String source) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_job_slots (uuid, slot_type, job_id, activated_at, last_changed_at, cooldown_until, source) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE job_id = VALUES(job_id), activated_at = VALUES(activated_at), last_changed_at = VALUES(last_changed_at), cooldown_until = VALUES(cooldown_until), source = VALUES(source)";
        } else {
            sql = "INSERT OR REPLACE INTO bbe_job_slots (uuid, slot_type, job_id, activated_at, last_changed_at, cooldown_until, source) VALUES (?, ?, ?, ?, ?, ?, ?)";
        }
        return getDatabase().executeUpdate("savePlayerSlot", sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, slotType.toUpperCase());
            if (jobId != null && !jobId.isBlank()) {
                stmt.setString(3, jobId.toLowerCase());
            } else {
                stmt.setNull(3, java.sql.Types.VARCHAR);
            }
            stmt.setLong(4, activatedAt);
            stmt.setLong(5, lastChangedAt);
            stmt.setLong(6, cooldownUntil);
            stmt.setString(7, source != null ? source : "RANKUP");
        }).thenApply(rows -> null);
    }
}
