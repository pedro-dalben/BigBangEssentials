package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 5: Normalizes the jobs skill rank column name for MySQL compatibility.
 */
public class V005NormalizeJobSkillRankColumn implements DatabaseMigration {

    private static final String TABLE_NAME = "bbe_player_job_skills";

    @Override
    public long version() {
        return 5L;
    }

    @Override
    public String description() {
        return "Normalize jobs skill rank column";
    }

    @Override
    public String checksum() {
        return "8cdbdb99ff8c965e9f5d5d2d457c0c7b";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        boolean hasLegacyRank = hasColumn(connection, TABLE_NAME, "rank");
        boolean hasSkillRank = hasColumn(connection, TABLE_NAME, "skill_rank");

        if (!hasLegacyRank && !hasSkillRank) {
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            if (!hasSkillRank) {
                stmt.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN skill_rank INT NOT NULL DEFAULT 0");
            }
            if (hasLegacyRank) {
                String legacyColumnReference = dialect.type() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL
                        ? "`rank`"
                        : "rank";
                stmt.execute("UPDATE " + TABLE_NAME + " SET skill_rank = " + legacyColumnReference);
            }
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = metaData.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            return columns.next();
        }
    }
}
