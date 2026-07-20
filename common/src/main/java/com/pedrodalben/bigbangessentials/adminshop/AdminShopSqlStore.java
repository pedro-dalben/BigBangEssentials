package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** SQL backend for admin shop state. JSON remains the migration/fallback backend. */
final class AdminShopSqlStore {
    private final DatabaseManager database = DatabaseManager.getInstance();
    private Optional<DatabaseExecutor> executor() {
        if (!database.isReady()) return Optional.empty();
        try { return Optional.of(database.getExecutor()); } catch (Exception e) { return Optional.empty(); }
    }

    boolean load(AdminShopManager.State state) {
        Optional<DatabaseExecutor> optional = executor();
        if (optional.isEmpty()) return false;
        try {
            DatabaseExecutor db = optional.get();
            List<Map.Entry<String, Long>> stocks = db.queryList("adminshop stocks", "SELECT product_id, remaining FROM adminshop_state", null, rs -> Map.entry(rs.getString(1), rs.getLong(2))).join();
            List<Limit> limits = db.queryList("adminshop limits", "SELECT player_uuid, product_id, used FROM adminshop_limits", null, rs -> new Limit(rs.getString(1) + ":" + rs.getString(2), rs.getLong(3))).join();
            List<Map.Entry<String, Long>> demand = db.queryList("adminshop demand", "SELECT product_id, demand FROM adminshop_demand", null, rs -> Map.entry(rs.getString(1), rs.getLong(2))).join();
            boolean hasRows = !stocks.isEmpty() || !limits.isEmpty() || !demand.isEmpty();
            if (hasRows) {
                state.remaining.clear(); state.limits.clear(); state.demand.clear();
                stocks.forEach(e -> state.remaining.put(e.getKey(), e.getValue()));
                limits.forEach(e -> state.limits.put(e.key, e.value));
                demand.forEach(e -> state.demand.put(e.getKey(), e.getValue()));
            }
            return hasRows;
        } catch (Exception e) { return false; }
    }

    void save(AdminShopManager.State state) {
        Optional<DatabaseExecutor> optional = executor(); if (optional.isEmpty()) return;
        optional.get().transaction("adminshop state", c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("DELETE FROM adminshop_state"); s.executeUpdate("DELETE FROM adminshop_limits"); s.executeUpdate("DELETE FROM adminshop_demand");
            }
            try (PreparedStatement s = c.prepareStatement("INSERT INTO adminshop_state(product_id,remaining) VALUES (?,?)")) { for (var e:state.remaining.entrySet()) { s.setString(1,e.getKey()); s.setLong(2,e.getValue()); s.addBatch(); } s.executeBatch(); }
            try (PreparedStatement s = c.prepareStatement("INSERT INTO adminshop_limits(player_uuid,product_id,used) VALUES (?,?,?)")) { for (var e:state.limits.entrySet()) { String[] k=e.getKey().split(":",2); s.setString(1,k[0]); s.setString(2,k[1]); s.setLong(3,e.getValue()); s.addBatch(); } s.executeBatch(); }
            try (PreparedStatement s = c.prepareStatement("INSERT INTO adminshop_demand(product_id,demand) VALUES (?,?)")) { for (var e:state.demand.entrySet()) { s.setString(1,e.getKey()); s.setLong(2,e.getValue()); s.addBatch(); } s.executeBatch(); }
            return null;
        }).join();
    }

    void log(String tx, UUID player, String product, String operation, String currency, BigDecimal price) {
        Optional<DatabaseExecutor> optional = executor(); if (optional.isEmpty()) return;
        try { optional.get().executeUpdate("adminshop transaction", "INSERT INTO adminshop_transactions(tx_id,player_uuid,product_id,operation,currency,price,success,created_at) VALUES (?,?,?,?,?,?,?,?)", s -> { s.setString(1,tx); s.setString(2,player.toString()); s.setString(3,product); s.setString(4,operation); s.setString(5,currency); s.setBigDecimal(6,price); s.setBoolean(7,true); s.setLong(8,System.currentTimeMillis()); }).join(); } catch (Exception ignored) { }
    }
    private record Limit(String key, long value) {}
}
