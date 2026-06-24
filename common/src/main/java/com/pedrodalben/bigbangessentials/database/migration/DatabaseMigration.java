package com.pedrodalben.bigbangessentials.database.migration;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface representing a database schema migration.
 */
public interface DatabaseMigration {
    
    long version();

    String description();

    /**
     * MD5 or SHA-256 checksum of the migration statements to detect unauthorized changes.
     */
    String checksum();

    void migrate(Connection connection, DatabaseDialect dialect) throws SQLException;
}
