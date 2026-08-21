package com.pedrodalben.bigbangessentials.jobs.admin;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for logging administrative actions and significant progression changes to the audit table.
 */
public class JobAuditService extends JdbcRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobAuditService.class);
    private static final JobAuditService INSTANCE = new JobAuditService();

    public static JobAuditService getInstance() {
        return INSTANCE;
    }

    private JobAuditService() {
        super();
    }

    private boolean isDatabaseAvailable() {
        return DatabaseManager.getInstance().isReady();
    }

    public CompletableFuture<Void> logEvent(UUID targetUuid, String eventType, String jobId, String slotType, UUID actorUuid, String reason, String metadata) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO bbe_job_audit_logs (event_id, uuid, event_type, job_id, slot_type, actor_uuid, reason, created_at, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return getDatabase().executeUpdate("logJobAudit", sql, stmt -> {
            stmt.setString(1, eventId);
            stmt.setString(2, targetUuid != null ? targetUuid.toString() : "");
            stmt.setString(3, eventType);
            if (jobId != null) stmt.setString(4, jobId.toLowerCase()); else stmt.setNull(4, java.sql.Types.VARCHAR);
            if (slotType != null) stmt.setString(5, slotType.toUpperCase()); else stmt.setNull(5, java.sql.Types.VARCHAR);
            if (actorUuid != null) stmt.setString(6, actorUuid.toString()); else stmt.setNull(6, java.sql.Types.VARCHAR);
            stmt.setString(7, reason != null ? reason : "");
            stmt.setLong(8, now);
            stmt.setString(9, metadata != null ? metadata : "");
        }).thenApply(rows -> (Void) null).exceptionally(e -> {
            LOGGER.error("Failed to log job audit event {}", eventType, e);
            return (Void) null;
        });
    }
}
