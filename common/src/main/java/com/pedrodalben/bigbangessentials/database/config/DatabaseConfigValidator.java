package com.pedrodalben.bigbangessentials.database.config;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseException;

public class DatabaseConfigValidator {
    public static void validateAndSanitize(DatabaseConfig config) throws DatabaseException {
        if (config.getType() == DatabaseType.MYSQL) {
            if (config.getMysql().getHost() == null || config.getMysql().getHost().isEmpty()) {
                throw new DatabaseException("MySQL host cannot be empty");
            }
        }
        
        if (config.getPool().getConnectionTimeoutMs() <= 0) {
            throw new DatabaseException("Connection timeout must be positive");
        }
        
        if (config.getExecutor().getQueueCapacity() <= 0) {
            throw new DatabaseException("Queue capacity must be positive");
        }
        if (config.getExecutor().getThreads() <= 0) {
            throw new DatabaseException("Executor threads must be positive");
        }
        if (config.getPool().getMaximumPoolSize() <= 0 || config.getPool().getMinimumIdle() < 0
                || config.getPool().getMinimumIdle() > config.getPool().getMaximumPoolSize()) {
            throw new DatabaseException("Invalid connection pool size");
        }
    }
}
