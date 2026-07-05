package com.pedrodalben.bigbangessentials.jobs.license;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for storing and retrieving permanent job licenses.
 */
public class JobLicenseRepository extends JdbcRepository {

    public JobLicenseRepository() {
        super();
    }

    private boolean isDatabaseAvailable() {
        return DatabaseManager.getInstance().isReady();
    }

    public CompletableFuture<Map<String, PermanentLicense>> loadPlayerLicenses(UUID uuid) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        String sql = "SELECT job_id, licensed_at, source_milestone, license_version, granted_by FROM bbe_job_licenses WHERE uuid = ?";
        return getDatabase().queryList("loadPlayerLicenses", sql,
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> new PermanentLicense(
                        rs.getString("job_id").toLowerCase(),
                        rs.getLong("licensed_at"),
                        rs.getString("source_milestone"),
                        rs.getInt("license_version"),
                        rs.getString("granted_by")
                )
        ).thenApply(list -> {
            Map<String, PermanentLicense> map = new HashMap<>();
            for (PermanentLicense lic : list) {
                map.put(lic.jobId(), lic);
            }
            return map;
        });
    }

    public CompletableFuture<Void> savePlayerLicense(UUID uuid, PermanentLicense license) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_job_licenses (uuid, job_id, licensed_at, source_milestone, license_version, granted_by) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE licensed_at = VALUES(licensed_at), source_milestone = VALUES(source_milestone), license_version = VALUES(license_version), granted_by = VALUES(granted_by)";
        } else {
            sql = "INSERT OR REPLACE INTO bbe_job_licenses (uuid, job_id, licensed_at, source_milestone, license_version, granted_by) VALUES (?, ?, ?, ?, ?, ?)";
        }
        return getDatabase().executeUpdate("savePlayerLicense", sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, license.jobId().toLowerCase());
            stmt.setLong(3, license.licensedAt());
            stmt.setString(4, license.sourceMilestone());
            stmt.setInt(5, license.version());
            stmt.setString(6, license.grantedBy());
        }).thenApply(rows -> null);
    }

    public CompletableFuture<Void> removePlayerLicense(UUID uuid, String jobId) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        String sql = "DELETE FROM bbe_job_licenses WHERE uuid = ? AND job_id = ?";
        return getDatabase().executeUpdate("removePlayerLicense", sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, jobId.toLowerCase());
        }).thenApply(rows -> null);
    }
}
