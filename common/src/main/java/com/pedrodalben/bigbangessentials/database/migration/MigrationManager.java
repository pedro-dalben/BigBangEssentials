package com.pedrodalben.bigbangessentials.database.migration;

import com.pedrodalben.bigbangessentials.database.config.DatabaseConfig;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.exception.MigrationException;
import com.pedrodalben.bigbangessentials.database.migration.migrations.V001CreateDatabaseInfrastructure;
import com.pedrodalben.bigbangessentials.database.migration.migrations.V002CreateJobsTables;
import com.pedrodalben.bigbangessentials.database.migration.migrations.V003CreatePlayerPreferencesTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Manages database migrations: registration, verification, and execution.
 */
public class MigrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationManager.class);
    
    private final List<DatabaseMigration> registeredMigrations = new ArrayList<>();

    public MigrationManager() {
        // Register migrations in order
        registeredMigrations.add(new V001CreateDatabaseInfrastructure());
        registeredMigrations.add(new V002CreateJobsTables());
        registeredMigrations.add(new V003CreatePlayerPreferencesTables());
    }

    /**
     * Runs all pending migrations. Prevent concurrent executions in the same JVM.
     *
     * @param conn The database connection
     * @param dialect The database dialect
     * @param config The database configuration
     * @return List of execution results
     * @throws MigrationException if validation or execution fails
     */
    public synchronized List<MigrationResult> runMigrations(Connection conn, DatabaseDialect dialect, DatabaseConfig config) throws MigrationException {
        if (!config.getMigrations().isEnabled()) {
            LOGGER.info("Database migrations are disabled in configuration. Skipping execution.");
            return Collections.emptyList();
        }

        try {
            // Bootstrap schema migrations table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(dialect.createSchemaMigrationsTable());
            }

            // Retrieve already applied migrations
            Map<Long, AppliedMigration> applied = new HashMap<>();
            String query = "SELECT version, description, checksum, success FROM bbe_schema_migrations";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    long version = rs.getLong("version");
                    String description = rs.getString("description");
                    String checksum = rs.getString("checksum");
                    boolean success = rs.getBoolean("success");
                    applied.put(version, new AppliedMigration(version, description, checksum, success));
                }
            }

            // Validate checksums of already applied migrations
            if (config.getMigrations().isValidateChecksums()) {
                for (DatabaseMigration migration : registeredMigrations) {
                    AppliedMigration record = applied.get(migration.version());
                    if (record != null && record.success()) {
                        if (!record.checksum().equals(migration.checksum())) {
                            String errorMsg = String.format("Migration checksum mismatch for version %d. Local: %s, Database: %s", 
                                migration.version(), migration.checksum(), record.checksum());
                            if (config.getMigrations().isFailOnChecksumMismatch()) {
                                throw new MigrationException(errorMsg);
                            } else {
                                LOGGER.warn("WARNING: {}", errorMsg);
                            }
                        }
                    }
                }
            }

            // Filter pending migrations
            List<DatabaseMigration> pending = new ArrayList<>();
            for (DatabaseMigration migration : registeredMigrations) {
                AppliedMigration record = applied.get(migration.version());
                if (record == null || !record.success()) {
                    pending.add(migration);
                }
            }

            // Sort pending migrations by version (ascending)
            pending.sort(Comparator.comparingLong(DatabaseMigration::version));

            if (pending.isEmpty()) {
                LOGGER.info("Schema is up-to-date. No migrations pending.");
                return Collections.emptyList();
            }

            LOGGER.info("Found {} pending migrations. Starting execution...", pending.size());
            List<MigrationResult> results = new ArrayList<>();

            for (DatabaseMigration migration : pending) {
                long startTime = System.currentTimeMillis();
                boolean success = false;
                String error = null;

                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    
                    LOGGER.info("Executing migration version {} - {}", migration.version(), migration.description());
                    migration.migrate(conn, dialect);
                    
                    conn.commit();
                    success = true;
                } catch (Throwable e) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        LOGGER.error("Failed to rollback migration version {}", migration.version(), rollbackEx);
                    }
                    error = e.getMessage();
                    throw new MigrationException(String.format("Failed to execute migration version %d (%s): %s", 
                        migration.version(), migration.description(), e.getMessage()), e);
                } finally {
                    try {
                        conn.setAutoCommit(originalAutoCommit);
                    } catch (SQLException ex) {
                        LOGGER.error("Failed to restore autocommit status", ex);
                    }

                    long duration = System.currentTimeMillis() - startTime;
                    
                    // Insert migration entry
                    String insertSql = "INSERT OR REPLACE INTO bbe_schema_migrations (version, description, checksum, applied_at, execution_ms, success) VALUES (?, ?, ?, ?, ?, ?)";
                    if (dialect.type() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL) {
                        // MySQL doesn't support "INSERT OR REPLACE", but version is PRIMARY KEY, so we can delete first or use ON DUPLICATE KEY UPDATE.
                        // Let's use INSERT ON DUPLICATE KEY UPDATE or just delete if exists
                        insertSql = "INSERT INTO bbe_schema_migrations (version, description, checksum, applied_at, execution_ms, success) VALUES (?, ?, ?, ?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE description = VALUES(description), checksum = VALUES(checksum), applied_at = VALUES(applied_at), execution_ms = VALUES(execution_ms), success = VALUES(success)";
                    }
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setLong(1, migration.version());
                        insertStmt.setString(2, migration.description());
                        insertStmt.setString(3, migration.checksum());
                        insertStmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                        insertStmt.setLong(5, duration);
                        insertStmt.setBoolean(6, success);
                        insertStmt.executeUpdate();
                    } catch (SQLException e) {
                        LOGGER.error("Failed to record migration result in schema table for version {}", migration.version(), e);
                    }

                    results.add(new MigrationResult(migration.version(), migration.description(), success, duration, error));
                }
            }

            LOGGER.info("Migrations completed successfully.");
            return results;

        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException("Error during migration orchestration", e);
        }
    }

    /**
     * Gets the current schema version from the migrations table.
     */
    public long getCurrentVersion(Connection conn) {
        String query = "SELECT MAX(version) FROM bbe_schema_migrations WHERE success = 1";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            // Table might not exist yet
            LOGGER.debug("Could not read current schema version (table may not exist yet): {}", e.getMessage());
        }
        return 0L;
    }

    private record AppliedMigration(long version, String description, String checksum, boolean success) {}
}
