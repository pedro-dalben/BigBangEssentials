package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V022CreatePokeMarketNotificationsTable implements DatabaseMigration {
    @Override public long version() { return 22; }
    @Override public String description() { return "Create persistent PokéMarket notifications"; }
    @Override public String checksum() { return "pokemarket-notifications-v1-20260721"; }
    @Override public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_pokemarket_notifications (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, type VARCHAR(48) NOT NULL, title_key VARCHAR(128) NOT NULL, message_key VARCHAR(128) NOT NULL, reference_type VARCHAR(48), reference_id VARCHAR(128), status VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL, delivered_at BIGINT, read_at BIGINT, metadata_json TEXT)");
            String prefix = dialect.type() == DatabaseType.MYSQL ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
            s.executeUpdate(prefix + "bbe_pokemarket_notifications_player ON bbe_pokemarket_notifications(player_uuid,status,created_at)");
            s.executeUpdate(prefix + "bbe_pokemarket_notifications_reference ON bbe_pokemarket_notifications(reference_type,reference_id)");
        }
    }
}
