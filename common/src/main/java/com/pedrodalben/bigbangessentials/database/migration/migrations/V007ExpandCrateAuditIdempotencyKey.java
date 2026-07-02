package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 7: Expands crate audit idempotency key storage for long command-generated keys.
 */
public class V007ExpandCrateAuditIdempotencyKey implements DatabaseMigration {

    @Override
    public long version() {
        return 7L;
    }

    @Override
    public String description() {
        return "Expand crate audit idempotency key column";
    }

    @Override
    public String checksum() {
        return "b7a3b0e5f9c44149a18d4f0c7d2d0d11";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        if (dialect.type() != DatabaseType.MYSQL) {
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE crate_audit_log MODIFY idempotency_key VARCHAR(255)");
        }
    }
}
