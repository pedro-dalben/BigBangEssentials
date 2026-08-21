package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class V003CreatePlayerPreferencesTables implements DatabaseMigration {

    @Override
    public long version() {
        return 3L;
    }

    @Override
    public String description() {
        return "Create player preferences, nicknames, tags, and ignore list tables";
    }

    @Override
    public String checksum() {
        return "3e4b7f1a9c6d2e8b0f5a7c3d9e1b4f6a";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_player_preferences (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "vanish_mode TINYINT NOT NULL DEFAULT 0, " +
                    "god_mode TINYINT NOT NULL DEFAULT 0, " +
                    "fly_mode TINYINT NOT NULL DEFAULT 0, " +
                    "tp_toggle TINYINT NOT NULL DEFAULT 1, " +
                    "msg_toggle TINYINT NOT NULL DEFAULT 1, " +
                    "pay_toggle TINYINT NOT NULL DEFAULT 1, " +
                    "socialspy TINYINT NOT NULL DEFAULT 0, " +
                    "teleport_menus_enabled TINYINT NOT NULL DEFAULT 1, " +
                    "warps_display_mode VARCHAR(32) NOT NULL DEFAULT 'MENU', " +
                    "homes_display_mode VARCHAR(32) NOT NULL DEFAULT 'MENU', " +
                    "pwarps_display_mode VARCHAR(32) NOT NULL DEFAULT 'MENU', " +
                    "last_location TEXT, " +
                    "created_at BIGINT NOT NULL, " +
                    "updated_at BIGINT NOT NULL " +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_player_nicknames (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "nickname VARCHAR(256) NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "updated_at BIGINT NOT NULL " +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_player_tags (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "tag_name VARCHAR(64) NOT NULL DEFAULT '', " +
                    "created_at BIGINT NOT NULL, " +
                    "updated_at BIGINT NOT NULL " +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_player_ignore_list (" +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "ignored_uuid VARCHAR(36) NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "PRIMARY KEY (player_uuid, ignored_uuid) " +
                    ")");
        }
    }
}
