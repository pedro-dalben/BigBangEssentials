package com.pedrodalben.bigbangessentials.database.dialect;

import com.pedrodalben.bigbangessentials.database.DatabaseType;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface representing SQL differences (dialects) between SQLite and MySQL.
 */
public interface DatabaseDialect {
    
    DatabaseType type();

    /**
     * Gets the SQL statement to create the schema migrations table if it does not exist.
     */
    String createSchemaMigrationsTable();

    /**
     * Gets the SQL statement to create the metadata table if it does not exist.
     */
    String createMetadataTable();

    /**
     * Gets the expression for current timestamp (e.g. CURRENT_TIMESTAMP).
     */
    String currentTimestampExpression();

    /**
     * Gets the upsert SQL query format for the metadata table.
     * Expects parameters: meta_key, meta_value, updated_at
     */
    String upsertMetadataSql();

    /**
     * Optional connection-level configuration.
     */
    void configureConnection(Connection connection) throws SQLException;
}
