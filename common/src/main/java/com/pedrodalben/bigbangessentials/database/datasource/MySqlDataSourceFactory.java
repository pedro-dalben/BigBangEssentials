package com.pedrodalben.bigbangessentials.database.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfig;

/**
 * Factory for creating MySQL connection pools using HikariCP.
 */
public class MySqlDataSourceFactory implements DataSourceFactory {

    @Override
    public HikariDataSource create(DatabaseConfig config) {
        DatabaseConfig.MySqlConfig mysql = config.getMysql();
        DatabaseConfig.PoolConfig pool = config.getPool();

        HikariConfig hikariConfig = new HikariConfig();

        // Driver and URL
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", 
            mysql.getHost(), mysql.getPort(), mysql.getDatabase()));
        
        // Credentials
        hikariConfig.setUsername(mysql.getUsername());
        hikariConfig.setPassword(mysql.getPassword());

        // Pool sizes
        hikariConfig.setMaximumPoolSize(pool.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(pool.getMinimumIdle());

        // Timeouts
        hikariConfig.setConnectionTimeout(pool.getConnectionTimeoutMs());
        hikariConfig.setValidationTimeout(pool.getValidationTimeoutMs());
        hikariConfig.setIdleTimeout(pool.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(pool.getMaxLifetimeMs());
        hikariConfig.setKeepaliveTime(pool.getKeepaliveTimeMs());

        // Thread pool name
        hikariConfig.setPoolName("BigBangEssentials-MySql-Pool");

        // Custom MySQL Properties
        hikariConfig.addDataSourceProperty("sslMode", mysql.getSslMode());
        hikariConfig.addDataSourceProperty("serverTimezone", mysql.getServerTimezone());
        
        // Performance optimizations for MySQL
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        return new HikariDataSource(hikariConfig);
    }
}
