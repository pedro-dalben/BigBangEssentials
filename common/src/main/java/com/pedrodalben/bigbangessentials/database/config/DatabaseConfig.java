package com.pedrodalben.bigbangessentials.database.config;

import com.pedrodalben.bigbangessentials.database.DatabaseType;

public class DatabaseConfig {
    public boolean enabled = true;
    public boolean required = false;
    public boolean isEnabled() { return enabled; }
    public boolean isRequired() { return required; }
    public DatabaseType type = DatabaseType.SQLITE;
    public DatabaseType getType() { return type; }
    public void setType(DatabaseType type) { this.type = type; }
    
    public static class SqliteConfig {
        public String file = "database.db";
        public String getFile() { return file; }
        public boolean foreignKeys = true;
        public boolean isForeignKeys() { return foreignKeys; }
        public boolean wal = true;
        public boolean isWal() { return wal; }
        public long busyTimeoutMs = 3000;
        public long getBusyTimeoutMs() { return busyTimeoutMs; }
    }
    public static class MySqlConfig {
        public String host = "localhost";
        public void setHost(String host) { this.host = host; }
        public String getHost() { return host; }
        public int port = 3306;
        public void setPort(int port) { this.port = port; }
        public int getPort() { return port; }
        public String database = "db";
        public String getDatabase() { return database; }
        public String username = "root";
        public String getUsername() { return username; }
        public String password = "";
        public String getPassword() { return password; }
        public String sslMode = "DISABLED";
        public String getSslMode() { return sslMode; }
        public String serverTimezone = "UTC";
        public String getServerTimezone() { return serverTimezone; }
    }
    public static class PoolConfig {
        public int maximumPoolSize = 10;
        public void setMaximumPoolSize(int size) { this.maximumPoolSize = size; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public int minimumIdle = 2;
        public void setMinimumIdle(int idle) { this.minimumIdle = idle; }
        public int getMinimumIdle() { return minimumIdle; }
        public long idleTimeoutMs = 30000;
        public long getIdleTimeoutMs() { return idleTimeoutMs; }
        public long maxLifetimeMs = 1800000;
        public long getMaxLifetimeMs() { return maxLifetimeMs; }
        public long connectionTimeoutMs = 5000;
        public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
        public void setConnectionTimeoutMs(long ms) { this.connectionTimeoutMs = ms; }
        public void setConnectionTimeoutMs(int ms) { this.connectionTimeoutMs = ms; }
        public long validationTimeoutMs = 5000;
        public long getValidationTimeoutMs() { return validationTimeoutMs; }
        public long keepaliveTimeMs = 30000;
        public long getKeepaliveTimeMs() { return keepaliveTimeMs; }
    }
    public static class ExecutorConfig {
        public int poolSize = 8;
        public void setThreads(int threads) { this.poolSize = threads; }
        public int getThreads() { return poolSize; }
        public int queueCapacity = 2000;
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int cap) { this.queueCapacity = cap; }
        public long shutdownTimeoutSeconds = 10;
        public long getShutdownTimeoutSeconds() { return shutdownTimeoutSeconds; }
    }
    public static class DebugConfig {
        public long slowQueryThresholdMs = 500;
        public long getSlowQueryThresholdMs() { return slowQueryThresholdMs; }
        public boolean logSlowQueries = false;
        public boolean isLogSlowQueries() { return logSlowQueries; }
        public boolean logQueries = false;
        public boolean isLogQueries() { return logQueries; }
    }
    public static class MigrationConfig {
        public boolean failOnChecksumMismatch = false;
        public boolean isFailOnChecksumMismatch() { return failOnChecksumMismatch; }
        public boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public boolean validateChecksums = true;
        public boolean isValidateChecksums() { return validateChecksums; }
    }
    
    public SqliteConfig sqlite = new SqliteConfig();
    public SqliteConfig getSqlite() { return sqlite; }
    public MySqlConfig mysql = new MySqlConfig();
    public MySqlConfig getMysql() { return mysql; }
    public PoolConfig pool = new PoolConfig();
    public PoolConfig getPool() { return pool; }
    public ExecutorConfig executor = new ExecutorConfig();
    public ExecutorConfig getExecutor() { return executor; }
    public DebugConfig debug = new DebugConfig();
    public DebugConfig getDebug() { return debug; }
    public MigrationConfig migrations = new MigrationConfig();
    public MigrationConfig getMigrations() { return migrations; }
    
    public static DatabaseConfig load() { return new DatabaseConfig(); }
}
