package com.pedrodalben.bigbangessentials.database.execution;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Callback interface for database operations executed on a Connection.
 */
@FunctionalInterface
public interface ConnectionCallback<T> {
    T doInConnection(Connection connection) throws SQLException;
}
