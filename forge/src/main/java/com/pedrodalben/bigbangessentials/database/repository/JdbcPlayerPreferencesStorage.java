package com.pedrodalben.bigbangessentials.database.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JdbcPlayerPreferencesStorage extends JdbcRepository implements PlayerPreferencesStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPlayerPreferencesStorage.class);

    public JdbcPlayerPreferencesStorage() {
        super();
    }

    public JdbcPlayerPreferencesStorage(DatabaseExecutor executor) {
        super(executor);
    }

    @Override
    public CompletableFuture<PlayerPreferences> loadPreferences(UUID playerId) {
        return getDatabase().querySingle(
                "SELECT * FROM bbe_player_preferences WHERE uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()),
                this::mapPreferences
        ).thenApply(opt -> opt.orElseGet(PlayerPreferences::defaults));
    }

    @Override
    public CompletableFuture<Void> savePreferences(UUID playerId, PlayerPreferences prefs) {
        long now = System.currentTimeMillis();
        String sql = buildSavePreferencesSql();
        return getDatabase().executeUpdate("savePreferences", sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setBoolean(2, prefs.vanishMode());
            stmt.setBoolean(3, prefs.godMode());
            stmt.setBoolean(4, prefs.flyMode());
            stmt.setBoolean(5, prefs.tpToggle());
            stmt.setBoolean(6, prefs.msgToggle());
            stmt.setBoolean(7, prefs.payToggle());
            stmt.setBoolean(8, prefs.socialspy());
            stmt.setBoolean(9, prefs.teleportMenusEnabled());
            stmt.setString(10, prefs.warpsDisplayMode().name());
            stmt.setString(11, prefs.homesDisplayMode().name());
            stmt.setString(12, prefs.pwarpsDisplayMode().name());
            stmt.setString(13, prefs.lastLocation());
            stmt.setLong(14, now);
            stmt.setLong(15, now);
        }).whenComplete((count, err) -> {
            if (err != null) {
                LOGGER.error("Failed to save player preferences for {}", playerId, err);
            }
        }).thenApply(count -> null);
    }

    @Override
    public CompletableFuture<Void> updateToggle(UUID playerId, String toggleKey, boolean value) {
        long now = System.currentTimeMillis();
        String column;
        switch (toggleKey) {
            case "vanishMode" -> column = "vanish_mode";
            case "godMode" -> column = "god_mode";
            case "flyMode" -> column = "fly_mode";
            case "tpToggle" -> column = "tp_toggle";
            case "msgToggle" -> column = "msg_toggle";
            case "payToggle" -> column = "pay_toggle";
            case "socialspy" -> column = "socialspy";
            case "teleportMenusEnabled" -> column = "teleport_menus_enabled";
            default -> {
                LOGGER.warn("Unknown toggle key: {}", toggleKey);
                return CompletableFuture.completedFuture(null);
            }
        }
        String sql = buildUpdateToggleSql(column);
        return getDatabase().executeUpdate("updateToggle:" + column, sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setBoolean(2, value);
            stmt.setLong(3, now);
            stmt.setLong(4, now);
        }).whenComplete((count, err) -> {
            if (err != null) {
                LOGGER.error("Failed to update toggle '{}' for {}", toggleKey, playerId, err);
            }
        }).thenApply(count -> null);
    }

    @Override
    public CompletableFuture<String> loadNickname(UUID playerId) {
        return getDatabase().querySingle(
                "SELECT nickname FROM bbe_player_nicknames WHERE uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> rs.getString("nickname")
        ).thenApply(opt -> opt.orElse(null));
    }

    @Override
    public CompletableFuture<Void> saveNickname(UUID playerId, String nickname) {
        long now = System.currentTimeMillis();
        String sql = buildSaveNicknameSql();
        return getDatabase().executeUpdate("saveNickname", sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, nickname);
            stmt.setLong(3, now);
            stmt.setLong(4, now);
        }).whenComplete((count, err) -> {
            if (err != null) {
                LOGGER.error("Failed to save nickname for {}", playerId, err);
            }
        }).thenApply(count -> null);
    }

    @Override
    public CompletableFuture<Void> deleteNickname(UUID playerId) {
        return getDatabase().executeUpdate(
                "DELETE FROM bbe_player_nicknames WHERE uuid = ?",
                stmt -> stmt.setString(1, playerId.toString())
        ).thenApply(count -> null);
    }

    @Override
    public CompletableFuture<String> loadTag(UUID playerId) {
        return getDatabase().querySingle(
                "SELECT tag_name FROM bbe_player_tags WHERE uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> rs.getString("tag_name")
        ).thenApply(opt -> opt.orElse(null));
    }

    @Override
    public CompletableFuture<Void> saveTag(UUID playerId, String tagName) {
        long now = System.currentTimeMillis();
        String value = tagName != null ? tagName : "";
        String sql = buildSaveTagSql();
        return getDatabase().executeUpdate("saveTag", sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, value);
            stmt.setLong(3, now);
            stmt.setLong(4, now);
        }).whenComplete((count, err) -> {
            if (err != null) {
                LOGGER.error("Failed to save tag for {}", playerId, err);
            }
        }).thenApply(count -> null);
    }

    @Override
    public CompletableFuture<Void> deleteTag(UUID playerId) {
        return getDatabase().executeUpdate(
                "DELETE FROM bbe_player_tags WHERE uuid = ?",
                stmt -> stmt.setString(1, playerId.toString())
        ).thenApply(count -> null);
    }

    @Override
    public CompletableFuture<List<UUID>> loadIgnoreList(UUID playerId) {
        return getDatabase().queryList(
                "SELECT ignored_uuid FROM bbe_player_ignore_list WHERE player_uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> UUID.fromString(rs.getString("ignored_uuid"))
        );
    }

    @Override
    public CompletableFuture<Void> addIgnoredPlayer(UUID playerId, UUID ignoredPlayer) {
        long now = System.currentTimeMillis();
        String sql = DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
                ? "INSERT IGNORE INTO bbe_player_ignore_list (player_uuid, ignored_uuid, created_at) VALUES (?, ?, ?)"
                : "INSERT OR IGNORE INTO bbe_player_ignore_list (player_uuid, ignored_uuid, created_at) VALUES (?, ?, ?)";
        return getDatabase().executeUpdate(sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, ignoredPlayer.toString());
            stmt.setLong(3, now);
        }).thenApply(count -> null);
    }

    @Override
    public CompletableFuture<Void> removeIgnoredPlayer(UUID playerId, UUID ignoredPlayer) {
        return getDatabase().executeUpdate(
                "DELETE FROM bbe_player_ignore_list WHERE player_uuid = ? AND ignored_uuid = ?",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, ignoredPlayer.toString());
                }
        ).thenApply(count -> null);
    }

    @Override
    public CompletableFuture<Boolean> isIgnored(UUID playerId, UUID ignoredPlayer) {
        return getDatabase().querySingle(
                "SELECT 1 FROM bbe_player_ignore_list WHERE player_uuid = ? AND ignored_uuid = ?",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, ignoredPlayer.toString());
                },
                rs -> true
        ).thenApply(opt -> opt.orElse(false));
    }

    @Override
    public CompletableFuture<LegacyImportResult> importFromJson(UUID playerId, Map<String, Object> legacyData) {
        if (legacyData == null || legacyData.isEmpty()) {
            return CompletableFuture.completedFuture(new LegacyImportResult(0, 0, "No data"));
        }
        return getDatabase().transaction("import-preferences", conn -> {
            int imported = 0;
            int skipped = 0;

            boolean vanish = boolVal(legacyData.get("vanishMode"));
            boolean god = boolVal(legacyData.get("godMode"));
            boolean fly = boolVal(legacyData.get("flyMode"));
            boolean tpToggle = boolVal(legacyData.getOrDefault("tpToggle", true));
            boolean msgToggle = boolVal(legacyData.getOrDefault("msgToggle", true));
            String lastLoc = strVal(legacyData.get("lastLocation"));

            String upsert = DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
                    ? "INSERT INTO bbe_player_preferences (uuid, vanish_mode, god_mode, fly_mode, tp_toggle, msg_toggle, last_location, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE vanish_mode = VALUES(vanish_mode), god_mode = VALUES(god_mode), fly_mode = VALUES(fly_mode), tp_toggle = VALUES(tp_toggle), msg_toggle = VALUES(msg_toggle), last_location = VALUES(last_location), updated_at = VALUES(updated_at)"
                    : "INSERT INTO bbe_player_preferences (uuid, vanish_mode, god_mode, fly_mode, tp_toggle, msg_toggle, last_location, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET vanish_mode = EXCLUDED.vanish_mode, god_mode = EXCLUDED.god_mode, fly_mode = EXCLUDED.fly_mode, tp_toggle = EXCLUDED.tp_toggle, msg_toggle = EXCLUDED.msg_toggle, last_location = EXCLUDED.last_location, updated_at = EXCLUDED.updated_at";

            try (var ps = conn.prepareStatement(upsert)) {
                ps.setString(1, playerId.toString());
                ps.setBoolean(2, vanish);
                ps.setBoolean(3, god);
                ps.setBoolean(4, fly);
                ps.setBoolean(5, tpToggle);
                ps.setBoolean(6, msgToggle);
                ps.setString(7, lastLoc);
                ps.setLong(8, System.currentTimeMillis());
                ps.setLong(9, System.currentTimeMillis());
                ps.executeUpdate();
                imported++;
            }

            return new LegacyImportResult(imported, skipped, "Imported " + imported + " fields for " + playerId);
        });
    }

    private static boolean boolVal(Object val) {
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() != 0;
        return false;
    }

    private static String strVal(Object val) {
        return val != null ? val.toString() : null;
    }

    private PlayerPreferences mapPreferences(ResultSet rs) throws SQLException {
        return new PlayerPreferences(
                rs.getBoolean("vanish_mode"),
                rs.getBoolean("god_mode"),
                rs.getBoolean("fly_mode"),
                rs.getBoolean("tp_toggle"),
                rs.getBoolean("msg_toggle"),
                rs.getBoolean("pay_toggle"),
                rs.getBoolean("socialspy"),
                rs.getBoolean("teleport_menus_enabled"),
                parseDisplayMode(rs.getString("warps_display_mode")),
                parseDisplayMode(rs.getString("homes_display_mode")),
                parseDisplayMode(rs.getString("pwarps_display_mode")),
                rs.getString("last_location")
        );
    }

    private static CommandDisplayMode parseDisplayMode(String value) {
        if (value == null) return CommandDisplayMode.MENU;
        try {
            return CommandDisplayMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CommandDisplayMode.MENU;
        }
    }

    private String upsertClause() {
        return upsertClause("uuid");
    }

    private String upsertClause(String primaryKey) {
        return DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
                ? "ON DUPLICATE KEY UPDATE"
                : "ON CONFLICT(" + primaryKey + ") DO UPDATE SET";
    }

    private String upsertValueReference(String column) {
        return DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
                ? "VALUES(" + column + ")"
                : "EXCLUDED." + column;
    }

    private String buildSavePreferencesSql() {
        return "INSERT INTO bbe_player_preferences (uuid, vanish_mode, god_mode, fly_mode, " +
                "tp_toggle, msg_toggle, pay_toggle, socialspy, teleport_menus_enabled, " +
                "warps_display_mode, homes_display_mode, pwarps_display_mode, last_location, " +
                "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                upsertClause() + " " +
                "vanish_mode = " + upsertValueReference("vanish_mode") + ", " +
                "god_mode = " + upsertValueReference("god_mode") + ", " +
                "fly_mode = " + upsertValueReference("fly_mode") + ", " +
                "tp_toggle = " + upsertValueReference("tp_toggle") + ", " +
                "msg_toggle = " + upsertValueReference("msg_toggle") + ", " +
                "pay_toggle = " + upsertValueReference("pay_toggle") + ", " +
                "socialspy = " + upsertValueReference("socialspy") + ", " +
                "teleport_menus_enabled = " + upsertValueReference("teleport_menus_enabled") + ", " +
                "warps_display_mode = " + upsertValueReference("warps_display_mode") + ", " +
                "homes_display_mode = " + upsertValueReference("homes_display_mode") + ", " +
                "pwarps_display_mode = " + upsertValueReference("pwarps_display_mode") + ", " +
                "last_location = " + upsertValueReference("last_location") + ", " +
                "updated_at = " + upsertValueReference("updated_at");
    }

    private String buildUpdateToggleSql(String column) {
        return "INSERT INTO bbe_player_preferences (uuid, " + column + ", created_at, updated_at) " +
                "VALUES (?, ?, ?, ?) " + upsertClause() + " " +
                column + " = " + upsertValueReference(column) + ", " +
                "updated_at = " + upsertValueReference("updated_at");
    }

    private String buildSaveNicknameSql() {
        return "INSERT INTO bbe_player_nicknames (uuid, nickname, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?) " + upsertClause("uuid") + " " +
                "nickname = " + upsertValueReference("nickname") + ", " +
                "updated_at = " + upsertValueReference("updated_at");
    }

    private String buildSaveTagSql() {
        return "INSERT INTO bbe_player_tags (uuid, tag_name, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?) " + upsertClause("uuid") + " " +
                "tag_name = " + upsertValueReference("tag_name") + ", " +
                "updated_at = " + upsertValueReference("updated_at");
    }
}
