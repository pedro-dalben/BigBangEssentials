package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 2: Creates jobs, job skills, and daily earnings tables.
 */
public class V002CreateJobsTables implements DatabaseMigration {

    @Override
    public long version() {
        return 2L;
    }

    @Override
    public String description() {
        return "Create jobs module tables";
    }

    @Override
    public String checksum() {
        return "f1274d825c56df3d1c15f9b4c3e8006b";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Player jobs progress and active state
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_player_jobs (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "job_id VARCHAR(64) NOT NULL, " +
                    "level INT NOT NULL DEFAULT 1, " +
                    "xp DOUBLE NOT NULL DEFAULT 0.0, " +
                    "skill_points INT NOT NULL DEFAULT 0, " +
                    "active TINYINT NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (uuid, job_id)" +
                    ")");

            // Player job skills ranks
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_player_job_skills (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "job_id VARCHAR(64) NOT NULL, " +
                    "skill_id VARCHAR(64) NOT NULL, " +
                    "skill_rank INT NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (uuid, job_id, skill_id)" +
                    ")");

            // Player daily earnings tracker (cycle_start is epoch ms identifying the day cycle)
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_player_job_earnings (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "job_id VARCHAR(64) NOT NULL, " +
                    "cycle_start BIGINT NOT NULL, " +
                    "amount DOUBLE NOT NULL DEFAULT 0.0, " +
                    "PRIMARY KEY (uuid, job_id, cycle_start)" +
                    ")");
        }
    }
}
