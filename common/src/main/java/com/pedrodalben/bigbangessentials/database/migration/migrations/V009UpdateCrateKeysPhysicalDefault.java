package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 9: Updates existing crate keys to PHYSICAL with default tripwire hook item.
 * Sets key_type='PHYSICAL', prefixes name with 'Chave ', and fills missing item_template_json.
 */
public class V009UpdateCrateKeysPhysicalDefault implements DatabaseMigration {

    private static final String DEFAULT_TRIPWIRE_HOOK_JSON =
        "{\"id\":\"minecraft:tripwire_hook\",\"count\":1}";

    @Override
    public long version() {
        return 9L;
    }

    @Override
    public String description() {
        return "Update existing crate keys to PHYSICAL type with default tripwire hook item";
    }

    @Override
    public String checksum() {
        return "b9c8d7e6f5a4b3c2d1e0f9a8b7c6d5e4";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, key_type, item_template_json FROM crate_keys")) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String keyType = rs.getString("key_type");
                String itemJson = rs.getString("item_template_json");

                boolean changed = false;
                String newKeyType = keyType;
                String newName = name;
                String newItemJson = itemJson;

                if (keyType == null || keyType.equalsIgnoreCase("VIRTUAL")) {
                    newKeyType = "PHYSICAL";
                    changed = true;
                }

                if (name != null && !name.toLowerCase().startsWith("chave ")) {
                    newName = "Chave " + name;
                    changed = true;
                }

                if (itemJson == null || itemJson.isBlank()) {
                    newItemJson = DEFAULT_TRIPWIRE_HOOK_JSON;
                    changed = true;
                }

                if (changed) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE crate_keys SET name = ?, key_type = ?, item_template_json = ?, updated_at = ? WHERE id = ?")) {
                        update.setString(1, newName);
                        update.setString(2, newKeyType);
                        update.setString(3, newItemJson);
                        update.setLong(4, System.currentTimeMillis());
                        update.setString(5, id);
                        update.executeUpdate();
                    }
                }
            }
        }
    }
}
