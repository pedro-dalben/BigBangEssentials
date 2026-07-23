package com.pedrodalben.bigbangessentials.api.economy;

import com.google.gson.Gson;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** JDBC economy. Every public mutation uses the same transaction for account and receipt. */
public final class DatabaseEconomyService implements EconomyService, IdempotentEconomyService {
    private static final String CURRENCY = "money";
    private final DatabaseManager database;
    private final Gson gson = new Gson();

    public DatabaseEconomyService() { this(DatabaseManager.getInstance()); }
    public DatabaseEconomyService(DatabaseManager database) { this.database = database; }

    private int scale() { return ConfigManager.getEconomyCurrencyScale(); }
    private Money money(BigDecimal value, boolean allowNegative) {
        return Money.from(value, scale(), ConfigManager.getEconomyRoundingMode(), allowNegative);
    }
    private long starting() { return money(BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance()), false).minorUnits(); }
    private long maximum() { return money(BigDecimal.valueOf(ConfigManager.getMaxBalance()), false).minorUnits(); }
    private DatabaseExecutor executor() { return database.getExecutor(); }

    @Override public double getBalance(UUID playerId) { return getBalanceDecimal(playerId).doubleValue(); }
    public BigDecimal getBalanceDecimal(UUID playerId) {
        return executor().querySingle("economy.account.balance", "SELECT balance_minor FROM bbe_economy_accounts WHERE player_uuid=?", s -> s.setString(1, playerId.toString()), r -> BigDecimal.valueOf(r.getLong(1), scale())).join().orElse(BigDecimal.ZERO.setScale(scale()));
    }

    public Map<UUID, BigDecimal> getAllBalances() {
        return executor().queryList("economy.account.all", "SELECT player_uuid,balance_minor FROM bbe_economy_accounts", null,
                r -> Map.entry(UUID.fromString(r.getString(1)), BigDecimal.valueOf(r.getLong(2), scale()))).join()
                .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, ConcurrentHashMap::new));
    }

    @Override public boolean deposit(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return false;
        return credit(playerId, BigDecimal.valueOf(amount), "api:deposit:" + UUID.randomUUID(), "API deposit", Map.of()).join().status() == EconomyOperationStatus.COMPLETED;
    }
    @Override public boolean withdraw(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return false;
        return debit(playerId, BigDecimal.valueOf(amount), "api:withdraw:" + UUID.randomUUID(), "API withdrawal", Map.of()).join().status() == EconomyOperationStatus.COMPLETED;
    }
    @Override public boolean setBalance(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        try { return setBalance(playerId, BigDecimal.valueOf(amount), UUID.randomUUID().toString(), "ADMIN", "Administrative set", Map.of()).join().status() == EconomyOperationStatus.COMPLETED; }
        catch (RuntimeException e) { return false; }
    }
    @Override public boolean resetBalance(UUID playerId) { return setBalance(playerId, 0.0); }
    @Override public boolean hasAccount(UUID playerId) { return executor().querySingle("economy.account.exists", "SELECT 1 FROM bbe_economy_accounts WHERE player_uuid=?", s -> s.setString(1, playerId.toString()), r -> true).join().isPresent(); }
    @Override public boolean createAccount(UUID playerId) { return createAccount(playerId, "api:create:" + UUID.randomUUID()).join(); }
    @Override public boolean deleteAccount(UUID playerId) { return setBalance(playerId, 0.0); }
    @Override public String format(double amount) { return getCurrencySymbol() + String.format(java.util.Locale.US, "%." + scale() + "f", amount); }
    @Override public String getCurrencySymbol() { return ConfigManager.getCurrencySymbol(); }

    public CompletableFuture<Boolean> createAccount(UUID playerId, String key) {
        return executor().transaction("economy.account.create", c -> {
            if (findAccount(c, playerId).isPresent()) return false;
            try (var s = c.prepareStatement("INSERT INTO bbe_economy_accounts (player_uuid,balance_minor,currency,created_at,updated_at,version,last_operation_id) VALUES (?,?,?,?,?,?,?)")) {
                long now = System.currentTimeMillis(); s.setString(1, playerId.toString()); s.setLong(2, starting()); s.setString(3, CURRENCY); s.setLong(4, now); s.setLong(5, now); s.setLong(6, 0); s.setNull(7, Types.VARCHAR); s.executeUpdate(); return true;
            }
        }).exceptionallyCompose(error -> hasAccount(playerId) ? CompletableFuture.completedFuture(false) : CompletableFuture.failedFuture(error));
    }

    @Override public CompletableFuture<EconomyOperationReceipt> debit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return transact("DEBIT", playerId, amount, key, reason, metadata, c -> debit(c, playerId, amount, key, reason, metadata));
    }
    @Override public CompletableFuture<EconomyOperationReceipt> credit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return transact("CREDIT", playerId, amount, key, reason, metadata, c -> credit(c, playerId, amount, key, reason, metadata));
    }

    public EconomyOperationReceipt debit(Connection c, UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) throws SQLException { return mutate(c, "DEBIT", playerId, amount, key, reason, metadata); }
    public EconomyOperationReceipt credit(Connection c, UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) throws SQLException { return mutate(c, "CREDIT", playerId, amount, key, reason, metadata); }

    public CompletableFuture<Boolean> transfer(UUID sender, UUID receiver, BigDecimal amount, BigDecimal fee, String key) {
        if (sender == null || receiver == null || sender.equals(receiver) || amount == null || fee == null
                || amount.signum() <= 0 || fee.signum() < 0 || amount.compareTo(fee) <= 0
                || key == null || key.isBlank()) return CompletableFuture.completedFuture(false);
        return executor().transaction("economy.transfer", c -> {
            Map<String, String> metadata = Map.of("source", "pay", "reference", key,
                    "receiver", receiver.toString(), "fee", fee.toPlainString());
            String fingerprint = EconomyOperationFingerprint.of(sender, "TRANSFER", amount, CURRENCY, "pay", key, metadata);
            Optional<EconomyOperationReceipt> old = existing(c, key);
            if (old.isPresent()) return old.get().fingerprint() != null && !old.get().fingerprint().equals(fingerprint)
                    ? false : old.get().status() == EconomyOperationStatus.COMPLETED;

            Account senderAccount = findAccount(c, sender).orElse(new Account(starting(), 0));
            insertPending(c, UUID.randomUUID(), sender, "TRANSFER", amount, key, "Player payment",
                    senderAccount.decimal(), senderAccount.decimal().subtract(amount), metadata, fingerprint);
            EconomyOperationReceipt debit = debit(c, sender, amount, key + ":debit", "Player payment", metadata);
            if (debit.status() != EconomyOperationStatus.COMPLETED) {
                complete(c, key, debit.status(), "Insufficient funds", senderAccount.decimal(), senderAccount.decimal());
                return false;
            }
            EconomyOperationReceipt credit = credit(c, receiver, amount.subtract(fee), key + ":credit", "Player payment", metadata);
            if (credit.status() != EconomyOperationStatus.COMPLETED) throw new SQLException("Transfer credit rejected");
            complete(c, key, EconomyOperationStatus.COMPLETED, null, senderAccount.decimal(), debit.balanceAfter());
            return true;
        }).exceptionally(error -> false);
    }

    public CompletableFuture<EconomyOperationReceipt> setBalance(UUID playerId, BigDecimal amount, String key, String actor, String reason, Map<String, String> metadata) {
        return transact("ADMIN_SET", playerId, amount, key, reason + " actor=" + actor, metadata, c -> set(c, playerId, amount, key, reason + " actor=" + actor, metadata));
    }
    public EconomyOperationReceipt set(Connection c, UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) throws SQLException {
        Money target = money(amount, false);
        Map<String, String> safeMetadata = metadata == null ? Map.of() : metadata;
        String fingerprint = EconomyOperationFingerprint.of(playerId, "ADMIN_SET", amount, CURRENCY,
                safeMetadata.getOrDefault("source", "economy"), safeMetadata.get("reference"), safeMetadata);
        Optional<EconomyOperationReceipt> existing = existing(c, key);
        if (existing.isPresent()) return compatible(existing.get(), fingerprint, playerId, amount);
        Account account = findAccount(c, playerId).orElse(new Account(starting(), 0));
        long now = System.currentTimeMillis();
        insertPending(c, UUID.randomUUID(), playerId, "ADMIN_SET", target.decimal(), key, reason, account.decimal(), target.decimal(), safeMetadata, fingerprint);
        if (account.balance() == starting() && findAccount(c, playerId).isEmpty()) insertAccount(c, playerId, target.minorUnits(), now);
        else updateExact(c, playerId, target.minorUnits(), account.version(), null);
        return complete(c, key, EconomyOperationStatus.COMPLETED, null, account.decimal(), target.decimal());
    }

    @Override public CompletableFuture<Optional<EconomyOperationReceipt>> findOperation(String key) {
        return executor().querySingle("economy.operation.find", "SELECT * FROM bbe_economy_operations WHERE idempotency_key=?", s -> s.setString(1, key), this::map);
    }

    private <T> CompletableFuture<T> transact(String name, UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata, SqlWork<T> work) {
        if (key == null || key.isBlank()) return CompletableFuture.failedFuture(new IllegalArgumentException("idempotency key required"));
        return executor().transaction("economy." + name.toLowerCase(), work::run)
                .exceptionallyCompose(error -> findOperation(key).thenCompose(found -> found.isPresent() ? CompletableFuture.completedFuture((T) found.get()) : CompletableFuture.failedFuture(error)));
    }

    private EconomyOperationReceipt mutate(Connection c, String type, UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) throws SQLException {
        Money value = money(amount, false);
        if (value.minorUnits() <= 0) throw new IllegalArgumentException("Amount must be positive");
        Map<String, String> safeMetadata = metadata == null ? Map.of() : metadata;
        String fingerprint = EconomyOperationFingerprint.of(playerId, type, value.decimal(), CURRENCY,
                safeMetadata.getOrDefault("source", "economy"), safeMetadata.get("reference"), safeMetadata);
        Optional<EconomyOperationReceipt> old = existing(c, key);
        if (old.isPresent()) return compatible(old.get(), fingerprint, playerId, value.decimal());
        Optional<Account> found = findAccount(c, playerId);
        long before = found.map(Account::balance).orElse(starting());
        if (found.isEmpty() && type.equals("DEBIT")) {
            insertPending(c, UUID.randomUUID(), playerId, type, value.decimal(), key, reason, decimal(before), decimal(before), safeMetadata, fingerprint);
            return complete(c, key, EconomyOperationStatus.REJECTED, "Account does not exist", decimal(before), decimal(before));
        }
        long after;
        if (type.equals("DEBIT")) {
            after = before - value.minorUnits();
            insertPending(c, UUID.randomUUID(), playerId, type, value.decimal(), key, reason, decimal(before), decimal(Math.max(0, after)), safeMetadata, fingerprint);
            if (after < 0 || updateAtomic(c, playerId, value.minorUnits(), true) != 1) return complete(c, key, EconomyOperationStatus.REJECTED, "Insufficient funds", decimal(before), decimal(before));
        } else {
            try { after = Math.addExact(before, value.minorUnits()); } catch (ArithmeticException e) { after = Long.MAX_VALUE; }
            insertPending(c, UUID.randomUUID(), playerId, type, value.decimal(), key, reason, decimal(before), decimal(after), safeMetadata, fingerprint);
            if (found.isEmpty()) {
                if (after > maximum()) return complete(c, key, EconomyOperationStatus.REJECTED, "Maximum balance exceeded", decimal(before), decimal(before));
                insertAccount(c, playerId, after, System.currentTimeMillis());
            }
            else if (after > maximum() || updateAtomic(c, playerId, value.minorUnits(), false) != 1) return complete(c, key, EconomyOperationStatus.REJECTED, "Maximum balance exceeded", decimal(before), decimal(before));
        }
        return complete(c, key, EconomyOperationStatus.COMPLETED, null, decimal(before), decimal(after));
    }

    private Optional<Account> findAccount(Connection c, UUID id) throws SQLException {
        try (var s = c.prepareStatement("SELECT balance_minor,version FROM bbe_economy_accounts WHERE player_uuid=?")) { s.setString(1, id.toString()); try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(new Account(r.getLong(1), r.getLong(2))) : Optional.empty(); } }
    }
    private void insertAccount(Connection c, UUID id, long balance, long now) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_economy_accounts (player_uuid,balance_minor,currency,created_at,updated_at,version,last_operation_id) VALUES (?,?,?,?,?,?,?)")) { s.setString(1, id.toString()); s.setLong(2, balance); s.setString(3, CURRENCY); s.setLong(4, now); s.setLong(5, now); s.setLong(6, 0); s.setNull(7, Types.VARCHAR); s.executeUpdate(); }
    }
    private int updateAtomic(Connection c, UUID id, long amount, boolean debit) throws SQLException {
        String sql = debit ? "UPDATE bbe_economy_accounts SET balance_minor=balance_minor-?,version=version+1,updated_at=? WHERE player_uuid=? AND balance_minor>=?" : "UPDATE bbe_economy_accounts SET balance_minor=balance_minor+?,version=version+1,updated_at=? WHERE player_uuid=? AND balance_minor<=?";
        long bound = debit ? amount : maximum() - amount;
        try (var s = c.prepareStatement(sql)) { s.setLong(1, amount); s.setLong(2, System.currentTimeMillis()); s.setString(3, id.toString()); s.setLong(4, bound); return s.executeUpdate(); }
    }
    private void updateExact(Connection c, UUID id, long target, long version, String operation) throws SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_economy_accounts SET balance_minor=?,version=version+1,updated_at=?,last_operation_id=? WHERE player_uuid=? AND version=?")) { s.setLong(1, target); s.setLong(2, System.currentTimeMillis()); if (operation == null) s.setNull(3, Types.VARCHAR); else s.setString(3, operation); s.setString(4, id.toString()); s.setLong(5, version); if (s.executeUpdate() != 1) throw new SQLException("Optimistic locking conflict"); }
    }
    private Optional<EconomyOperationReceipt> existing(Connection c, String key) throws SQLException {
        try (var s = c.prepareStatement("SELECT * FROM bbe_economy_operations WHERE idempotency_key=?")) { s.setString(1, key); try (var r = s.executeQuery()) { return r.next() ? Optional.of(map(r)) : Optional.empty(); } }
    }
    private void insertPending(Connection c, UUID id, UUID player, String type, BigDecimal amount, String key, String reason, BigDecimal before, BigDecimal after, Map<String, String> metadata, String fingerprint) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_economy_operations (id,player_uuid,operation_type,amount,currency,idempotency_key,reason,source_module,source_reference,status,balance_before,balance_after,created_at,metadata_json,fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, id.toString()); s.setString(2, player.toString()); s.setString(3, type); s.setBigDecimal(4, amount); s.setString(5, CURRENCY); s.setString(6, key); s.setString(7, reason); s.setString(8, metadata.getOrDefault("source", "economy")); s.setString(9, metadata.get("reference")); s.setString(10, EconomyOperationStatus.PENDING.name()); s.setBigDecimal(11, before); s.setBigDecimal(12, after); s.setLong(13, System.currentTimeMillis()); s.setString(14, gson.toJson(metadata)); s.setString(15, fingerprint); s.executeUpdate();
        }
    }
    private EconomyOperationReceipt complete(Connection c, String key, EconomyOperationStatus status, String error, BigDecimal before, BigDecimal after) throws SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_economy_operations SET status=?,completed_at=?,last_error=?,balance_before=?,balance_after=? WHERE idempotency_key=? AND status=?")) { s.setString(1, status.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error); s.setBigDecimal(4, before); s.setBigDecimal(5, after); s.setString(6, key); s.setString(7, EconomyOperationStatus.PENDING.name()); s.executeUpdate(); }
        return existing(c, key).orElseThrow(() -> new SQLException("Economy operation disappeared"));
    }
    private EconomyOperationReceipt compatible(EconomyOperationReceipt old, String fingerprint, UUID player, BigDecimal amount) {
        if (old.fingerprint() == null || old.fingerprint().equals(fingerprint)) return old;
        return new EconomyOperationReceipt(old.id(), player, amount, EconomyOperationStatus.IDEMPOTENCY_CONFLICT,
                old.balanceBefore(), old.balanceAfter(), old.idempotencyKey(), fingerprint);
    }
    private EconomyOperationReceipt map(ResultSet r) throws SQLException { return new EconomyOperationReceipt(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("player_uuid")), r.getBigDecimal("amount"), EconomyOperationStatus.valueOf(r.getString("status")), r.getBigDecimal("balance_before"), r.getBigDecimal("balance_after"), r.getString("idempotency_key"), r.getString("fingerprint")); }
    private BigDecimal decimal(long minor) { return BigDecimal.valueOf(minor, scale()); }
    private record Account(long balance, long version) { BigDecimal decimal() { return BigDecimal.valueOf(balance, ConfigManager.getEconomyCurrencyScale()); } }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
}
