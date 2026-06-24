package com.pedrodalben.bigbangessentials.database.execution;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Functional interface to map a row of a ResultSet to an object.
 */
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet resultSet) throws SQLException;
}
