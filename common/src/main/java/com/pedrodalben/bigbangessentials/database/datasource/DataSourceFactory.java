package com.pedrodalben.bigbangessentials.database.datasource;

import com.zaxxer.hikari.HikariDataSource;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfig;

/**
 * Factory interface for creating Hikari connection pools.
 */
public interface DataSourceFactory {
    HikariDataSource create(DatabaseConfig config);
}
