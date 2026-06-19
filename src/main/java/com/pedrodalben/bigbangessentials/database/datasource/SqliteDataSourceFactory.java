package com.pedrodalben.bigbangessentials.database.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Factory for creating SQLite connection pools using HikariCP.
 */
public class SqliteDataSourceFactory implements DataSourceFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteDataSourceFactory.class);

    @Override
    public HikariDataSource create(DatabaseConfig config) {
        DatabaseConfig.SqliteConfig sqlite = config.getSqlite();
        DatabaseConfig.PoolConfig pool = config.getPool();

        // Ensure parent directory exists
        File dbFile = new File(sqlite.getFile());
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                LOGGER.info("Created parent directory for SQLite database: {}", parentDir.getPath());
            }
        }

        HikariConfig hikariConfig = new HikariConfig();
        
        // Driver and URL
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + sqlite.getFile());

        // Pool constraints (Enforced internally for SQLite)
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setMinimumIdle(1);
        
        // Timeouts
        hikariConfig.setConnectionTimeout(pool.getConnectionTimeoutMs());
        hikariConfig.setValidationTimeout(pool.getValidationTimeoutMs());
        hikariConfig.setIdleTimeout(pool.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(pool.getMaxLifetimeMs());
        hikariConfig.setKeepaliveTime(pool.getKeepaliveTimeMs());

        // Thread pool name
        hikariConfig.setPoolName("BigBangEssentials-Sqlite-Pool");

        // SQLite PRAGMAs configuration executed on connection initialization
        String pragmaSql = String.format(
            "PRAGMA foreign_keys = %s; PRAGMA journal_mode = %s; PRAGMA busy_timeout = %d;",
            sqlite.isForeignKeys() ? "ON" : "OFF",
            sqlite.isWal() ? "WAL" : "DELETE",
            sqlite.getBusyTimeoutMs()
        );
        hikariConfig.setConnectionInitSql(pragmaSql);

        return new HikariDataSource(hikariConfig);
    }
}
