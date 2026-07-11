package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class V014AlterRankupTransactionsIdempotencyAndMoney implements DatabaseMigration {

    @Override
    public long version() {
        return 14L;
    }

    @Override
    public String description() {
        return "Alter Rankup transactions and add columns for explicit tracking and unique idempotency index";
    }

    @Override
    public String checksum() {
        return "b2c3d4e5f6a178901234567890123458";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            if (dialect.type() == DatabaseType.MYSQL) {
                // Modify money column to DECIMAL(19,4) for precision in MySQL
                stmt.execute("ALTER TABLE rankup_transactions MODIFY COLUMN money_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000");
                // Unique index for MySQL
                stmt.execute("ALTER TABLE rankup_transactions ADD CONSTRAINT uc_rankup_tx_idempotency UNIQUE (idempotency_key)");
                // Add columns for explicit steps
                stmt.execute("ALTER TABLE rankup_transactions ADD COLUMN money_debited TINYINT NOT NULL DEFAULT 0, " +
                        "ADD COLUMN gems_debited TINYINT NOT NULL DEFAULT 0, " +
                        "ADD COLUMN luckperms_updated TINYINT NOT NULL DEFAULT 0, " +
                        "ADD COLUMN history_written TINYINT NOT NULL DEFAULT 0, " +
                        "ADD COLUMN progress_cleared TINYINT NOT NULL DEFAULT 0, " +
                        "ADD COLUMN actions_executed TINYINT NOT NULL DEFAULT 0, " +
                        "ADD COLUMN compensated TINYINT NOT NULL DEFAULT 0");
            } else {
                // SQLite: Create unique index
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_rankup_tx_idempotency ON rankup_transactions(idempotency_key)");
                // SQLite: Add columns one by one
                stmt.execute("ALTER TABLE rankup_transactions ADD COLUMN money_debited TINYINT NOT NULL DEFAULT 0");
                stmt.execute("ALTER TABLE rankup_transactions ADD COLUMN gems_debited TINYINT NOT NULL DEFAULT 0");
                stmt.execute("ALTER TABLE rankup_transactions ADD COLUMN luckperms_updated TINYINT NOT NULL DEFAULT 0");
                stmt.execute("ALTER TABLE rankup_transactions ADD COLUMN history_written TINYINT NOT NULL DEFAULT 0");
                stmt.execute("ALTER TABLE rankup_transactions ADD COLUMN progress_cleared TINYINT NOT NULL DEFAULT 0");
                stmt.execute("ALTER TABLE rankup_transactions ADD COLUMN actions_executed TINYINT NOT NULL DEFAULT 0");
                stmt.execute("ALTER TABLE rankup_transactions ADD COLUMN compensated TINYINT NOT NULL DEFAULT 0");
            }
        }
    }
}
