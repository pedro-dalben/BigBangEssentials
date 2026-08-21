package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 12: Creates job rank milestones, licenses, license progress, slots, and audit log tables.
 */
public class V012CreateJobProgressionTables implements DatabaseMigration {

    @Override
    public long version() {
        return 12L;
    }

    @Override
    public String description() {
        return "Create job progression, license, slots, and audit tables";
    }

    @Override
    public String checksum() {
        return "f2e3d4c5b6a708192a3b4c5d6e7f8a9b";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Rank milestones achieved by players
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_job_rank_milestones (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "milestone_id VARCHAR(64) NOT NULL, " +
                    "source_rank_id VARCHAR(64) NOT NULL, " +
                    "achieved_at BIGINT NOT NULL, " +
                    "PRIMARY KEY (uuid, milestone_id)" +
                    ")");

            // Permanent job licenses
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_job_licenses (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "job_id VARCHAR(64) NOT NULL, " +
                    "licensed_at BIGINT NOT NULL, " +
                    "source_milestone VARCHAR(64) NOT NULL, " +
                    "license_version INT NOT NULL DEFAULT 1, " +
                    "granted_by VARCHAR(32) NOT NULL, " +
                    "PRIMARY KEY (uuid, job_id)" +
                    ")");

            // In-progress license quests
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_job_license_progress (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "job_id VARCHAR(64) NOT NULL, " +
                    "started_at BIGINT NOT NULL, " +
                    "status VARCHAR(32) NOT NULL, " +
                    "last_progress_at BIGINT NOT NULL, " +
                    "PRIMARY KEY (uuid, job_id)" +
                    ")");

            // License quest objectives
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_job_license_objectives (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "job_id VARCHAR(64) NOT NULL, " +
                    "objective_id VARCHAR(64) NOT NULL, " +
                    "current_amount INT NOT NULL DEFAULT 0, " +
                    "required_amount INT NOT NULL DEFAULT 0, " +
                    "completed_at BIGINT, " +
                    "PRIMARY KEY (uuid, job_id, objective_id)" +
                    ")");

            // Player job slots
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_job_slots (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "slot_type VARCHAR(64) NOT NULL, " +
                    "job_id VARCHAR(64), " +
                    "activated_at BIGINT, " +
                    "last_changed_at BIGINT, " +
                    "cooldown_until BIGINT NOT NULL DEFAULT 0, " +
                    "source VARCHAR(32), " +
                    "PRIMARY KEY (uuid, slot_type)" +
                    ")");

            // Job audit logs for progression, admin actions, and slot assignments
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_job_audit_logs (" +
                    "event_id VARCHAR(64) NOT NULL, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "event_type VARCHAR(64) NOT NULL, " +
                    "job_id VARCHAR(64), " +
                    "slot_type VARCHAR(64), " +
                    "actor_uuid VARCHAR(36), " +
                    "reason VARCHAR(255), " +
                    "created_at BIGINT NOT NULL, " +
                    "metadata VARCHAR(1024), " +
                    "PRIMARY KEY (event_id)" +
                    ")");

            createIndexIfMissing(connection, stmt, "bbe_job_audit_logs", "idx_job_audit_uuid", "uuid", false);
            createIndexIfMissing(connection, stmt, "bbe_job_audit_logs", "idx_job_audit_created", "created_at", false);
        }
    }

    private void createIndexIfMissing(Connection connection, Statement stmt, String tableName, String indexName, String columnName, boolean unique)
            throws SQLException {
        if (hasIndex(connection, tableName, indexName)) {
            return;
        }
        stmt.execute((unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ") + indexName + " ON " + tableName + " (" + columnName + ")");
    }

    private boolean hasIndex(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        if (hasIndexForTable(metaData, tableName, indexName)) {
            return true;
        }
        return hasIndexForTable(metaData, tableName.toUpperCase(), indexName);
    }

    private boolean hasIndexForTable(DatabaseMetaData metaData, String tableName, String indexName) throws SQLException {
        try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
