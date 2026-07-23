package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.google.gson.Gson;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** SQL backend for admin shop state. JSON remains the migration/fallback backend. */
final class AdminShopSqlStore {
    private final DatabaseManager database = DatabaseManager.getInstance();
    private final Gson gson = new Gson();
    private final java.nio.file.Path jsonPath = ResourceUtil.getDataPath("adminshop_audits.json");
    private final Map<String, JsonAudit> jsonAudits = new LinkedHashMap<>();
    private boolean jsonLoaded;
    private Optional<DatabaseExecutor> executor() {
        if (!database.isReady()) return Optional.empty();
        try { return Optional.of(database.getExecutor()); } catch (Exception e) { return Optional.empty(); }
    }

    private synchronized void loadJson() {
        if (jsonLoaded) return;
        jsonLoaded = true;
        try {
            if (java.nio.file.Files.exists(jsonPath)) {
                Map<String, JsonAudit> loaded = gson.fromJson(java.nio.file.Files.readString(jsonPath),
                        new com.google.gson.reflect.TypeToken<Map<String, JsonAudit>>(){}.getType());
                if (loaded != null) jsonAudits.putAll(loaded);
            }
        } catch (Exception ignored) { }
    }
    private synchronized boolean saveJson() {
        try {
            java.nio.file.Files.createDirectories(jsonPath.getParent());
            java.nio.file.Path tmp = jsonPath.resolveSibling(jsonPath.getFileName() + ".tmp");
            java.nio.file.Files.writeString(tmp, gson.toJson(jsonAudits));
            java.nio.file.Files.move(tmp, jsonPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception e) { return false; }
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

    boolean saveDelta(String productId, long oldRemaining, long remaining, UUID player,
                      long oldUsed, long used, long oldDemand, long demand,
                      boolean hadRemaining, boolean hadLimit, boolean hadDemand,
                      boolean hasRemaining, boolean hasLimit, boolean hasDemand) {
        Optional<DatabaseExecutor> optional = executor(); if (optional.isEmpty()) return true;
        try {
            return optional.get().transaction("adminshop state delta", c ->
                updateLong(c, "adminshop_state", "product_id", productId, "remaining",
                    oldRemaining, remaining, hadRemaining, hasRemaining)
                && updateLong(c, "adminshop_limits", "player_uuid", player.toString(), "product_id", productId,
                    "used", oldUsed, used, hadLimit, hasLimit)
                && updateLong(c, "adminshop_demand", "product_id", productId, "demand",
                    oldDemand, demand, hadDemand, hasDemand)
            ).join();
        } catch (Exception ignored) { return false; }
    }

    private boolean updateLong(Connection c, String table, String keyColumn, String key, String valueColumn,
                               long oldValue, long newValue, boolean oldPresent, boolean newPresent) throws SQLException {
        String select = "SELECT " + valueColumn + " FROM " + table + " WHERE " + keyColumn + "=?";
        if (database.getType() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL) select += " FOR UPDATE";
        Long current;
        try (PreparedStatement s = c.prepareStatement(select)) {
            s.setString(1, key);
            try (ResultSet r = s.executeQuery()) { current = r.next() ? r.getLong(1) : null; }
        }
        if (!Objects.equals(current, oldPresent ? oldValue : null)) return false;
        if (newPresent) {
            if (current == null) {
                try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table + "(" + keyColumn + "," + valueColumn + ") VALUES (?,?)")) {
                    s.setString(1, key); s.setLong(2, newValue); s.executeUpdate();
                }
                return true;
            }
            try (PreparedStatement s = c.prepareStatement("UPDATE " + table + " SET " + valueColumn + "=? WHERE " + keyColumn + "=? AND " + valueColumn + "=?")) {
                s.setLong(1, newValue); s.setString(2, key); s.setLong(3, oldValue); return s.executeUpdate() == 1;
            }
        }
        if (current == null) return true;
        try (PreparedStatement s = c.prepareStatement("DELETE FROM " + table + " WHERE " + keyColumn + "=? AND " + valueColumn + "=?")) {
            s.setString(1, key); s.setLong(2, oldValue); return s.executeUpdate() == 1;
        }
    }

    private boolean updateLong(Connection c, String table, String firstColumn, String firstKey,
                               String secondColumn, String secondKey, String valueColumn,
                               long oldValue, long newValue, boolean oldPresent, boolean newPresent) throws SQLException {
        String select = "SELECT " + valueColumn + " FROM " + table + " WHERE " + firstColumn + "=? AND " + secondColumn + "=?";
        if (database.getType() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL) select += " FOR UPDATE";
        Long current;
        try (PreparedStatement s = c.prepareStatement(select)) {
            s.setString(1, firstKey); s.setString(2, secondKey);
            try (ResultSet r = s.executeQuery()) { current = r.next() ? r.getLong(1) : null; }
        }
        if (!Objects.equals(current, oldPresent ? oldValue : null)) return false;
        if (newPresent) {
            if (current == null) {
                try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table + "(" + firstColumn + "," + secondColumn + "," + valueColumn + ") VALUES (?,?,?)")) {
                    s.setString(1, firstKey); s.setString(2, secondKey); s.setLong(3, newValue); s.executeUpdate();
                }
                return true;
            }
            try (PreparedStatement s = c.prepareStatement("UPDATE " + table + " SET " + valueColumn + "=? WHERE " + firstColumn + "=? AND " + secondColumn + "=? AND " + valueColumn + "=?")) {
                s.setLong(1, newValue); s.setString(2, firstKey); s.setString(3, secondKey); s.setLong(4, oldValue); return s.executeUpdate() == 1;
            }
        }
        if (current == null) return true;
        try (PreparedStatement s = c.prepareStatement("DELETE FROM " + table + " WHERE " + firstColumn + "=? AND " + secondColumn + "=? AND " + valueColumn + "=?")) {
            s.setString(1, firstKey); s.setString(2, secondKey); s.setLong(3, oldValue); return s.executeUpdate() == 1;
        }
    }

    boolean startAudit(String tx, UUID player, String product, String operation, String currency, long quantity,
                       BigDecimal price, String economicKey, String stockStage, String limitStage) {
        Optional<DatabaseExecutor> optional = executor();
        if (optional.isEmpty()) {
            loadJson();
            JsonAudit row = new JsonAudit(); row.tx = tx; row.player = player.toString(); row.product = product; row.operation = operation;
            row.currency = currency; row.quantity = quantity; row.price = price; row.status = AdminShopAuditStatus.STARTED.name(); row.key = economicKey;
            row.item = "PENDING"; row.stock = stockStage; row.limit = limitStage; row.demand = "PENDING"; row.created = row.updated = System.currentTimeMillis();
            jsonAudits.put(tx, row);
            return saveJson();
        }
        long now = System.currentTimeMillis();
        try { return optional.get().executeUpdate("adminshop audit.start", "INSERT INTO adminshop_transaction_audit(tx_id,player_uuid,product_id,operation,currency,quantity,effective_price,status,economic_operation_key,item_stage,stock_stage,limit_stage,demand_stage,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", s -> {
            s.setString(1,tx); s.setString(2,player.toString()); s.setString(3,product); s.setString(4,operation); s.setString(5,currency); s.setLong(6,quantity); s.setBigDecimal(7,price);
            s.setString(8,AdminShopAuditStatus.STARTED.name()); s.setString(9,economicKey); s.setString(10,"PENDING"); s.setString(11,stockStage); s.setString(12,limitStage); s.setString(13,"PENDING"); s.setLong(14,now); s.setLong(15,now);
        }).join() == 1; } catch (Exception e) { return false; }
    }

    boolean updateAudit(String tx, AdminShopAuditStatus status, EconomyOperationReceipt receipt,
                        String itemStage, String stockStage, String limitStage, String demandStage, String failure) {
        Optional<DatabaseExecutor> optional = executor();
        if (optional.isEmpty()) {
            loadJson(); JsonAudit row = jsonAudits.get(tx); if (row == null) return false;
            row.status = status.name(); row.item = itemStage; row.stock = stockStage; row.limit = limitStage; row.demand = demandStage; row.failure = failure; row.updated = System.currentTimeMillis();
            if (receipt != null) { row.economicId = receipt.id() == null ? null : receipt.id().toString(); row.before = receipt.balanceBefore(); row.after = receipt.balanceAfter(); }
            return saveJson();
        }
        try { return optional.get().executeUpdate("adminshop audit.update", "UPDATE adminshop_transaction_audit SET status=?,economic_operation_id=COALESCE(?,economic_operation_id),balance_before=COALESCE(?,balance_before),balance_after=COALESCE(?,balance_after),item_stage=?,stock_stage=?,limit_stage=?,demand_stage=?,failure=?,updated_at=?,completed_at=CASE WHEN ? IN ('COMPLETED','ROLLED_BACK','COMPENSATION_FAILED','RECONCILIATION_REQUIRED') THEN ? ELSE completed_at END WHERE tx_id=?", s -> {
            long now = System.currentTimeMillis(); s.setString(1,status.name());
            if (receipt == null || receipt.id() == null) s.setNull(2, Types.VARCHAR); else s.setString(2, receipt.id().toString());
            if (receipt == null || receipt.balanceBefore() == null) s.setNull(3, Types.DECIMAL); else s.setBigDecimal(3, receipt.balanceBefore());
            if (receipt == null || receipt.balanceAfter() == null) s.setNull(4, Types.DECIMAL); else s.setBigDecimal(4, receipt.balanceAfter());
            s.setString(5,itemStage); s.setString(6,stockStage); s.setString(7,limitStage); s.setString(8,demandStage); s.setString(9,failure); s.setLong(10,now); s.setString(11,status.name()); s.setLong(12,now); s.setString(13,tx);
        }).join() == 1; } catch (Exception e) { return false; }
    }

    boolean log(String tx, UUID player, String product, String operation, String currency, BigDecimal price) {
        Optional<DatabaseExecutor> optional = executor(); if (optional.isEmpty()) return true;
        try { return optional.get().executeUpdate("adminshop transaction", "INSERT INTO adminshop_transactions(tx_id,player_uuid,product_id,operation,currency,price,success,created_at) VALUES (?,?,?,?,?,?,?,?)", s -> { s.setString(1,tx); s.setString(2,player.toString()); s.setString(3,product); s.setString(4,operation); s.setString(5,currency); s.setBigDecimal(6,price); s.setBoolean(7,true); s.setLong(8,System.currentTimeMillis()); }).join() == 1; } catch (Exception ignored) { return false; }
    }

    List<AuditRow> forPlayer(UUID player, int limit) {
        Optional<DatabaseExecutor> optional = executor();
        if (optional.isEmpty()) {
            loadJson();
            return jsonAudits.values().stream().filter(row -> row.player.equals(player.toString())).sorted((a,b) -> Long.compare(b.created, a.created)).limit(Math.max(1, Math.min(100, limit))).map(this::mapJson).toList();
        }
        try { return optional.get().queryList("adminshop audit.player", "SELECT * FROM adminshop_transaction_audit WHERE player_uuid=? ORDER BY created_at DESC LIMIT " + Math.max(1, Math.min(100, limit)), s -> s.setString(1, player.toString()), this::mapAudit).join(); }
        catch (Exception e) { return List.of(); }
    }

    Optional<AuditRow> inspect(String tx) {
        Optional<DatabaseExecutor> optional = executor();
        if (optional.isEmpty()) { loadJson(); return Optional.ofNullable(jsonAudits.get(tx)).map(this::mapJson); }
        try { return optional.get().querySingle("adminshop audit.inspect", "SELECT * FROM adminshop_transaction_audit WHERE tx_id=?", s -> s.setString(1,tx), this::mapAudit).join(); }
        catch (Exception e) { return Optional.empty(); }
    }

    List<String> reconcile() {
        Optional<DatabaseExecutor> optional = executor();
        if (optional.isEmpty()) {
            loadJson();
            List<String> findings = jsonAudits.values().stream().filter(row -> !List.of("COMPLETED", "ROLLED_BACK").contains(row.status)).map(row -> "PENDENCIA " + row.tx + ":" + row.status).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            return findings.isEmpty() ? List.of("OK") : findings;
        }
        DatabaseExecutor db = optional.get(); List<String> findings = new ArrayList<>();
        try {
            findings.addAll(db.queryList("adminshop reconcile.sales", "SELECT a.tx_id FROM adminshop_transaction_audit a LEFT JOIN bbe_economy_operations e ON e.idempotency_key=a.economic_operation_key WHERE a.operation='SELL' AND a.currency='money' AND a.status='COMPLETED' AND (e.id IS NULL OR e.status <> 'COMPLETED')", null, r -> "VENDA_SEM_CREDITO tx=" + r.getString(1)).join());
            findings.addAll(db.queryList("adminshop reconcile.credits", "SELECT e.idempotency_key FROM bbe_economy_operations e LEFT JOIN adminshop_transaction_audit a ON a.economic_operation_key=e.idempotency_key WHERE e.source_module='adminshop' AND e.operation_type='CREDIT' AND e.idempotency_key LIKE 'adminshop:sell:%' AND e.status='COMPLETED' AND (a.tx_id IS NULL OR a.status IN ('STARTED','MONEY_APPLIED','ITEM_APPLIED','COMPENSATION_FAILED','RECONCILIATION_REQUIRED'))", null, r -> "CREDITO_SEM_CONCLUSAO key=" + r.getString(1)).join());
            findings.addAll(db.queryList("adminshop reconcile.compensation", "SELECT tx_id FROM adminshop_transaction_audit WHERE status='COMPENSATION_FAILED'", null, r -> "COMPENSACAO_FALHA tx=" + r.getString(1)).join());
            findings.addAll(db.queryList("adminshop reconcile.pending", "SELECT tx_id,status FROM adminshop_transaction_audit WHERE status IN ('STARTED','MONEY_APPLIED','ITEM_APPLIED','RECONCILIATION_REQUIRED')", null, r -> "PENDENCIA " + r.getString(1) + ":" + r.getString(2)).join());
            findings.addAll(db.queryList("adminshop reconcile.compensation.operations", "SELECT idempotency_key,status FROM bbe_economy_operations WHERE source_module='adminshop' AND idempotency_key LIKE 'adminshop:%:compensate' AND status <> 'COMPLETED'", null, r -> "COMPENSACAO_FALHA key=" + r.getString(1) + " status=" + r.getString(2)).join());
            findings.addAll(db.queryList("adminshop reconcile.legacy", "SELECT t.tx_id FROM adminshop_transactions t LEFT JOIN adminshop_transaction_audit a ON a.tx_id=t.tx_id WHERE t.success=1 AND a.tx_id IS NULL", null, r -> "HISTORICO_SEM_AUDITORIA tx=" + r.getString(1)).join());
        } catch (Exception e) { findings.add("RECONCILIATION_ERROR " + e.getClass().getSimpleName()); }
        if (findings.isEmpty()) findings.add("OK");
        return findings;
    }

    private AuditRow mapAudit(ResultSet r) throws SQLException {
        return new AuditRow(r.getString("tx_id"), UUID.fromString(r.getString("player_uuid")), r.getString("product_id"), r.getString("operation"), r.getString("currency"), r.getLong("quantity"), r.getBigDecimal("effective_price"), AdminShopAuditStatus.valueOf(r.getString("status")), r.getString("economic_operation_key"), r.getString("economic_operation_id"), r.getBigDecimal("balance_before"), r.getBigDecimal("balance_after"), r.getString("item_stage"), r.getString("stock_stage"), r.getString("limit_stage"), r.getString("demand_stage"), r.getString("failure"), r.getLong("created_at"), r.getLong("updated_at"));
    }

    private AuditRow mapJson(JsonAudit r) {
        return new AuditRow(r.tx, UUID.fromString(r.player), r.product, r.operation, r.currency, r.quantity, r.price,
                AdminShopAuditStatus.valueOf(r.status), r.key, r.economicId, r.before, r.after, r.item, r.stock, r.limit, r.demand,
                r.failure, r.created, r.updated);
    }

    private static final class JsonAudit {
        String tx, player, product, operation, currency, status, key, economicId, item, stock, limit, demand, failure;
        long quantity, created, updated;
        BigDecimal price, before, after;
    }

    record AuditRow(String tx, UUID player, String product, String operation, String currency, long quantity,
                    BigDecimal price, AdminShopAuditStatus status, String economicKey, String economicId,
                    BigDecimal balanceBefore, BigDecimal balanceAfter, String itemStage, String stockStage,
                    String limitStage, String demandStage, String failure, long createdAt, long updatedAt) {
        String format() { return "tx=" + tx + " player=" + player + " product=" + product + " op=" + operation + " " + quantity + "x " + price + " " + currency + " status=" + status + " money=" + economicKey + " balance=" + balanceBefore + "->" + balanceAfter + " stages=item:" + itemStage + ",stock:" + stockStage + ",limit:" + limitStage + ",demand:" + demandStage + (failure == null ? "" : " failure=" + failure); }
    }
    private record Limit(String key, long value) {}
}
