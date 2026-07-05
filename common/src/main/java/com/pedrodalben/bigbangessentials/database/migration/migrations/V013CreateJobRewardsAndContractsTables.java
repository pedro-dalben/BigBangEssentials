package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 13: Creates job reward balances, ledger, key rolls, contracts, and rankup reward claim tables.
 */
public class V013CreateJobRewardsAndContractsTables implements DatabaseMigration {

    @Override
    public long version() {
        return 13L;
    }

    @Override
    public String description() {
        return "Create job rewards, key rolls, contracts, and rankup reward claim tables";
    }

    @Override
    public String checksum() {
        return "a1b2c3d4e5f60718293a4b5c6d7e8f9a";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Virtual reward balances (e.g., JOURNEY_FRAGMENT)
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_jobs_reward_balances (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "reward_type VARCHAR(64) NOT NULL, " +
                    "balance BIGINT NOT NULL DEFAULT 0, " +
                    "updated_at BIGINT NOT NULL, " +
                    "version BIGINT NOT NULL DEFAULT 1, " +
                    "PRIMARY KEY (uuid, reward_type)" +
                    ")");

            // Immutable audit ledger for all reward balance changes
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_jobs_reward_ledger (" +
                    "entry_id VARCHAR(64) NOT NULL, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "reward_type VARCHAR(64) NOT NULL, " +
                    "delta BIGINT NOT NULL, " +
                    "balance_after BIGINT NOT NULL, " +
                    "source_type VARCHAR(64) NOT NULL, " +
                    "source_ref_id VARCHAR(255) NOT NULL, " +
                    "action_id VARCHAR(255), " +
                    "contract_id VARCHAR(255), " +
                    "rank_milestone_id VARCHAR(255), " +
                    "created_at BIGINT NOT NULL, " +
                    "metadata VARCHAR(1024), " +
                    "PRIMARY KEY (entry_id)" +
                    ")");

            // Key rolls audit log
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_jobs_key_rolls (" +
                    "roll_id VARCHAR(64) NOT NULL, " +
                    "action_id VARCHAR(255) NOT NULL, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "job_id VARCHAR(64) NOT NULL, " +
                    "job_level INT NOT NULL, " +
                    "base_chance DOUBLE NOT NULL, " +
                    "action_weight DOUBLE NOT NULL, " +
                    "final_chance DOUBLE NOT NULL, " +
                    "random_value DOUBLE NOT NULL, " +
                    "success BOOLEAN NOT NULL, " +
                    "reason VARCHAR(255), " +
                    "created_at BIGINT NOT NULL, " +
                    "PRIMARY KEY (roll_id)" +
                    ")");

            // Daily and weekly contracts assigned to players
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_jobs_contracts (" +
                    "contract_id VARCHAR(64) NOT NULL, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "template_id VARCHAR(64) NOT NULL, " +
                    "period_type VARCHAR(32) NOT NULL, " +
                    "generated_at BIGINT NOT NULL, " +
                    "expires_at BIGINT NOT NULL, " +
                    "status VARCHAR(32) NOT NULL, " +
                    "objective_snapshot VARCHAR(2048) NOT NULL, " +
                    "reward_snapshot VARCHAR(1024) NOT NULL, " +
                    "seed_reference VARCHAR(128), " +
                    "progress_amount INT NOT NULL DEFAULT 0, " +
                    "claimed_at BIGINT, " +
                    "reroll_count INT NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (contract_id)" +
                    ")");

            // Reclaim prevention for one-time rankup rewards (e.g., ASCENSION_KEY)
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_jobs_rankup_rewards_claimed (" +
                    "claim_id VARCHAR(128) NOT NULL, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "rank_milestone_id VARCHAR(64) NOT NULL, " +
                    "reward_type VARCHAR(64) NOT NULL, " +
                    "claimed_at BIGINT NOT NULL, " +
                    "PRIMARY KEY (claim_id)" +
                    ")");

            createIndexIfMissing(connection, stmt, "bbe_jobs_reward_ledger", "idx_reward_ledger_uuid", "uuid", false);
            createIndexIfMissing(connection, stmt, "bbe_jobs_reward_ledger", "idx_reward_ledger_ref", "source_ref_id", false);
            createIndexIfMissing(connection, stmt, "bbe_jobs_key_rolls", "idx_key_rolls_uuid_job", "uuid, job_id", false);
            createIndexIfMissing(connection, stmt, "bbe_jobs_contracts", "idx_jobs_contracts_uuid_period", "uuid, period_type", false);
        }
    }

    private void createIndexIfMissing(Connection connection, Statement stmt, String tableName, String indexName, String columns, boolean unique)
            throws SQLException {
        if (hasIndex(connection, tableName, indexName)) {
            return;
        }
        stmt.execute((unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ") + indexName + " ON " + tableName + " (" + columns + ")");
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
