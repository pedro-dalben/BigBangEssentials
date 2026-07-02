package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 4: Creates RankUp task progress, transactions, and rank history tables.
 */
public class V004CreateRankupTables implements DatabaseMigration {

    @Override
    public long version() {
        return 4L;
    }

    @Override
    public String description() {
        return "Create RankUp module tables";
    }

    @Override
    public String checksum() {
        return "a1b2c3d4e5f678901234567890123456";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS rankup_task_progress (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "ladder_id VARCHAR(64) NOT NULL, " +
                    "rank_id VARCHAR(64) NOT NULL, " +
                    "task_id VARCHAR(64) NOT NULL, " +
                    "progress INT NOT NULL DEFAULT 0, " +
                    "completed TINYINT NOT NULL DEFAULT 0, " +
                    "completed_at BIGINT, " +
                    "updated_at BIGINT NOT NULL, " +
                    "PRIMARY KEY (uuid, ladder_id, rank_id, task_id)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS rankup_transactions (" +
                    "transaction_id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "ladder_id VARCHAR(64) NOT NULL, " +
                    "from_rank_id VARCHAR(64) NOT NULL, " +
                    "to_rank_id VARCHAR(64) NOT NULL, " +
                    "money_amount DOUBLE NOT NULL DEFAULT 0.0, " +
                    "gems_amount INT NOT NULL DEFAULT 0, " +
                    "status VARCHAR(32) NOT NULL, " +
                    "idempotency_key VARCHAR(128) NOT NULL, " +
                    "error_message TEXT, " +
                    "created_at BIGINT NOT NULL, " +
                    "completed_at BIGINT" +
                    ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rankup_transactions_uuid ON rankup_transactions(uuid)");

            stmt.execute("CREATE TABLE IF NOT EXISTS rankup_rank_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "ladder_id VARCHAR(64) NOT NULL, " +
                    "from_rank_id VARCHAR(64) NOT NULL, " +
                    "to_rank_id VARCHAR(64) NOT NULL, " +
                    "promoted_by VARCHAR(36), " +
                    "promotion_source VARCHAR(64) NOT NULL, " +
                    "created_at BIGINT NOT NULL" +
                    ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rankup_history_uuid ON rankup_rank_history(uuid)");
        }
    }
}
