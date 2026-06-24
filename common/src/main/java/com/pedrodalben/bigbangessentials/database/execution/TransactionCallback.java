package com.pedrodalben.bigbangessentials.database.execution;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Callback interface for database operations executed within a transaction.
 */
@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction(Connection connection) throws SQLException;
}
