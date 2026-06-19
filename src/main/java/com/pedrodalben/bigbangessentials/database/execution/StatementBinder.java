package com.pedrodalben.bigbangessentials.database.execution;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Functional interface to bind variables to a PreparedStatement.
 */
@FunctionalInterface
public interface StatementBinder {
    void bind(PreparedStatement statement) throws SQLException;
}
