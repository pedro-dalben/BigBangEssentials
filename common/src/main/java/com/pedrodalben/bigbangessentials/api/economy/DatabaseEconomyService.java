package com.pedrodalben.bigbangessentials.api.economy;

import com.google.gson.Gson;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
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
import java.util.stream.Collectors;

/** JDBC economy. Account mutations and their receipts commit in one transaction. */
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
    private long starting() { return money(ConfigManager.getEconomyStartingBalanceDecimal(), false).minorUnits(); }
    private long maximum() { return money(ConfigManager.getMaxBalanceDecimal(), false).minorUnits(); }
    private DatabaseExecutor executor() { return database.getExecutor(); }

    @Override public double getBalance(UUID playerId) { return getBalanceDecimal(playerId).doubleValue(); }

    public CompletableFuture<BigDecimal> getBalanceDecimalAsync(UUID playerId) {
        return executor().querySingle("economy.account.balance",
                "SELECT balance_minor FROM bbe_economy_accounts WHERE player_uuid=?",
                s -> s.setString(1, playerId.toString()),
                r -> decimal(r.getLong(1))).thenApply(value -> value.orElse(BigDecimal.ZERO.setScale(scale())));
    }

    /** Legacy synchronous query. Callers with an async path should use getBalanceDecimalAsync. */
    public BigDecimal getBalanceDecimal(UUID playerId) { return getBalanceDecimalAsync(playerId).join(); }

    public Map<UUID, BigDecimal> getAllBalances() {
        return executor().queryList("economy.account.all", "SELECT player_uuid,balance_minor FROM bbe_economy_accounts", null,
                        r -> Map.entry(UUID.fromString(r.getString(1)), decimal(r.getLong(2)))).join()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, ConcurrentHashMap::new));
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
        try {
            return setBalance(playerId, BigDecimal.valueOf(amount), UUID.randomUUID().toString(), "ADMIN", "Administrative set", Map.of()).join().status() == EconomyOperationStatus.COMPLETED;
        } catch (RuntimeException e) { return false; }
    }

    @Override public boolean resetBalance(UUID playerId) { return setBalance(playerId, 0.0); }
    @Override public boolean hasAccount(UUID playerId) {
        return executor().querySingle("economy.account.exists", "SELECT 1 FROM bbe_economy_accounts WHERE player_uuid=?",
                s -> s.setString(1, playerId.toString()), r -> true).join().isPresent();
    }
    @Override public boolean createAccount(UUID playerId) { return createAccount(playerId, "api:create:" + UUID.randomUUID()).join(); }
    @Override public boolean deleteAccount(UUID playerId) { return setBalance(playerId, 0.0); }
    @Override public String format(double amount) { return getCurrencySymbol() + String.format(java.util.Locale.US, "%." + scale() + "f", amount); }
    @Override public String getCurrencySymbol() { return ConfigManager.getCurrencySymbol(); }

    public CompletableFuture<Boolean> createAccount(UUID playerId, String ignoredKey) {
        if (playerId == null) return CompletableFuture.completedFuture(false);
        return executor().transaction("economy.account.create", c -> insertAccountIfAbsent(c, playerId, starting(), System.currentTimeMillis()) == 1);
    }

    @Override public CompletableFuture<EconomyOperationReceipt> debit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return transact("DEBIT", playerId, amount, key, reason, metadata, c -> mutate(c, "DEBIT", playerId, amount, key, reason, metadata));
    }

    @Override public CompletableFuture<EconomyOperationReceipt> credit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return transact("CREDIT", playerId, amount, key, reason, metadata, c -> mutate(c, "CREDIT", playerId, amount, key, reason, metadata));
    }

    public EconomyOperationReceipt debit(Connection c, UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) throws SQLException {
        return mutate(c, "DEBIT", playerId, amount, key, reason, metadata);
    }

    public EconomyOperationReceipt credit(Connection c, UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) throws SQLException {
        return mutate(c, "CREDIT", playerId, amount, key, reason, metadata);
    }

    public CompletableFuture<Boolean> transfer(UUID sender, UUID receiver, BigDecimal amount, BigDecimal fee, String key) {
        return transferResult(sender, receiver, amount, fee, key).thenApply(r -> r.status() == EconomyOperationStatus.COMPLETED);
    }

    /** Atomic transfer boundary. The boolean overload remains for compatibility. */
    public CompletableFuture<EconomyOperationReceipt> transferResult(UUID sender, UUID receiver, BigDecimal amount, BigDecimal fee, String key) {
        if (sender == null || receiver == null || sender.equals(receiver) || key == null || key.isBlank()) {
            return CompletableFuture.completedFuture(rejected(sender, amount, key));
        }
        final Money value;
        final Money transferFee;
        try {
            value = money(amount, false);
            transferFee = money(fee, false);
            if (value.minorUnits() <= 0 || transferFee.minorUnits() < 0 || value.minorUnits() <= transferFee.minorUnits()) {
                return CompletableFuture.completedFuture(rejected(sender, amount, key));
            }
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(rejected(sender, amount, key));
        }

        return executor().transaction("economy.transfer", c -> transfer(c, sender, receiver, value, transferFee, key));
    }

    public CompletableFuture<EconomyOperationReceipt> setBalance(UUID playerId, BigDecimal amount, String key, String actor, String reason, Map<String, String> metadata) {
        return transact("ADMIN_SET", playerId, amount, key, reason + " actor=" + actor, metadata,
                c -> set(c, playerId, amount, key, reason + " actor=" + actor, metadata));
    }

    public EconomyOperationReceipt set(Connection c, UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) throws SQLException {
        Money target = money(amount, false);
        Map<String, String> safeMetadata = metadata == null ? Map.of() : metadata;
        String fingerprint = EconomyOperationFingerprint.of(playerId, "ADMIN_SET", target.decimal(), CURRENCY,
                safeMetadata.getOrDefault("source", "economy"), safeMetadata.get("reference"), safeMetadata);
        if (findAccount(c, playerId).isEmpty()) insertAccountIfAbsent(c, playerId, starting(), System.currentTimeMillis());
        Account account = lockAccount(c, playerId).orElseThrow(() -> new SQLException("Account disappeared"));
        Optional<EconomyOperationReceipt> old = existing(c, key);
        if (old.isPresent()) return compatible(old.get(), fingerprint, playerId, target.decimal());

        UUID operationId = UUID.randomUUID();
        try {
            insertPending(c, operationId, playerId, "ADMIN_SET", target.decimal(), key, reason,
                    account.decimal(), target.decimal(), safeMetadata, fingerprint);
        } catch (SQLException duplicate) {
            Optional<EconomyOperationReceipt> duplicateOperation = existing(c, key);
            if (duplicateOperation.isPresent()) return compatible(duplicateOperation.get(), fingerprint, playerId, target.decimal());
            throw duplicate;
        }
        updateExact(c, playerId, target.minorUnits(), account.version(), operationId.toString());
        return complete(c, key, EconomyOperationStatus.COMPLETED, null, account.decimal(), target.decimal());
    }

    @Override public CompletableFuture<Optional<EconomyOperationReceipt>> findOperation(String key) {
        return executor().querySingle("economy.operation.find",
                "SELECT id,player_uuid,amount,status,balance_before,balance_after,idempotency_key,fingerprint,currency,reason,last_error,created_at,source_module,source_reference FROM bbe_economy_operations WHERE idempotency_key=?",
                s -> s.setString(1, key), this::map);
    }

    private <T> CompletableFuture<T> transact(String type, UUID playerId, BigDecimal amount, String key, String reason,
                                               Map<String, String> metadata, SqlWork<T> work) {
        if (key == null || key.isBlank()) return CompletableFuture.failedFuture(new IllegalArgumentException("idempotency key required"));
        try { money(amount, false); } catch (RuntimeException e) {
            @SuppressWarnings("unchecked") T rejected = (T) rejected(playerId, amount, key);
            return CompletableFuture.completedFuture(rejected);
        }
        return executor().transaction("economy." + type.toLowerCase(), work::run)
                .exceptionallyCompose(error -> findOperation(key).thenCompose(found -> found.isPresent()
                        ? CompletableFuture.completedFuture((T) found.get()) : CompletableFuture.failedFuture(error)));
    }

    private EconomyOperationReceipt mutate(Connection c, String type, UUID playerId, BigDecimal amount, String key,
                                           String reason, Map<String, String> metadata) throws SQLException {
        if (playerId == null) throw new IllegalArgumentException("player required");
        Money value = money(amount, false);
        if (value.minorUnits() <= 0) throw new IllegalArgumentException("Amount must be positive");
        Map<String, String> safeMetadata = metadata == null ? Map.of() : metadata;
        String fingerprint = EconomyOperationFingerprint.of(playerId, type, value.decimal(), CURRENCY,
                safeMetadata.getOrDefault("source", "economy"), safeMetadata.get("reference"), safeMetadata);
        if (type.equals("CREDIT") && findAccount(c, playerId).isEmpty()) insertAccountIfAbsent(c, playerId, starting(), System.currentTimeMillis());
        Optional<Account> account = lockAccount(c, playerId);
        long before = account.map(Account::balance).orElse(starting());
        Optional<EconomyOperationReceipt> old = existing(c, key);
        if (old.isPresent()) return compatible(old.get(), fingerprint, playerId, value.decimal());

        UUID operationId = UUID.randomUUID();
        if (account.isEmpty() && type.equals("DEBIT")) {
            insertPending(c, operationId, playerId, type, value.decimal(), key, reason, decimal(before), decimal(before), safeMetadata, fingerprint);
            return complete(c, key, EconomyOperationStatus.REJECTED, "Account does not exist", decimal(before), decimal(before));
        }

        long after;
        String rejection = null;
        try {
            after = type.equals("CREDIT") ? Math.addExact(before, value.minorUnits()) : Math.subtractExact(before, value.minorUnits());
        } catch (ArithmeticException e) {
            after = before;
            rejection = "Monetary balance overflow";
        }
        if (rejection == null && after < 0) rejection = "Insufficient funds";
        if (rejection == null && after > maximum()) rejection = "Maximum balance exceeded";
        BigDecimal afterDecimal = rejection == null ? decimal(after) : decimal(before);
        try {
            insertPending(c, operationId, playerId, type, value.decimal(), key, reason, decimal(before), afterDecimal, safeMetadata, fingerprint);
        } catch (SQLException duplicate) {
            Optional<EconomyOperationReceipt> duplicateOperation = existing(c, key);
            if (duplicateOperation.isPresent()) return compatible(duplicateOperation.get(), fingerprint, playerId, value.decimal());
            throw duplicate;
        }
        if (rejection != null) return complete(c, key, EconomyOperationStatus.REJECTED, rejection, decimal(before), decimal(before));
        updateExact(c, playerId, after, account.orElseThrow().version(), operationId.toString());
        return complete(c, key, EconomyOperationStatus.COMPLETED, null, decimal(before), decimal(after));
    }

    private EconomyOperationReceipt transfer(Connection c, UUID sender, UUID receiver, Money amount, Money fee, String key) throws SQLException {
        Map<String, String> metadata = Map.of("source", "pay", "reference", key, "receiver", receiver.toString(), "fee", fee.decimal().toPlainString());
        String fingerprint = EconomyOperationFingerprint.of(sender, "TRANSFER", amount.decimal(), CURRENCY, "pay", key, metadata);
        UUID first = sender.compareTo(receiver) < 0 ? sender : receiver;
        UUID second = first.equals(sender) ? receiver : sender;
        if (findAccount(c, first).isEmpty()) insertAccountIfAbsent(c, first, starting(), System.currentTimeMillis());
        if (findAccount(c, second).isEmpty()) insertAccountIfAbsent(c, second, starting(), System.currentTimeMillis());
        Account firstAccount = lockAccount(c, first).orElseThrow(() -> new SQLException("Sender or receiver account disappeared"));
        Account secondAccount = lockAccount(c, second).orElseThrow(() -> new SQLException("Sender or receiver account disappeared"));
        Optional<EconomyOperationReceipt> old = existing(c, key);
        if (old.isPresent()) return compatible(old.get(), fingerprint, sender, amount.decimal());

        Account senderAccount = sender.equals(first) ? firstAccount : secondAccount;
        Account receiverAccount = receiver.equals(first) ? firstAccount : secondAccount;
        long senderBefore = senderAccount.balance();
        long receiverBefore = receiverAccount.balance();
        long net = amount.minorUnits() - fee.minorUnits();
        String rejection = null;
        long senderAfter = senderBefore;
        long receiverAfter = receiverBefore;
        try {
            senderAfter = Math.subtractExact(senderBefore, amount.minorUnits());
            receiverAfter = Math.addExact(receiverBefore, net);
        } catch (ArithmeticException e) { rejection = "Monetary balance overflow"; }
        if (rejection == null && senderAfter < 0) rejection = "Insufficient funds";
        if (rejection == null && receiverAfter > maximum()) rejection = "Maximum balance exceeded";
        UUID operationId = UUID.randomUUID();
        try {
            insertPending(c, operationId, sender, "TRANSFER", amount.decimal(), key, "Player payment",
                    decimal(senderBefore), rejection == null ? decimal(senderAfter) : decimal(senderBefore), metadata, fingerprint);
        } catch (SQLException duplicate) {
            Optional<EconomyOperationReceipt> duplicateOperation = existing(c, key);
            if (duplicateOperation.isPresent()) return compatible(duplicateOperation.get(), fingerprint, sender, amount.decimal());
            throw duplicate;
        }
        if (rejection != null) return complete(c, key, EconomyOperationStatus.REJECTED, rejection, decimal(senderBefore), decimal(senderBefore));
        updateExact(c, sender, senderAfter, senderAccount.version(), operationId.toString());
        updateExact(c, receiver, receiverAfter, receiverAccount.version(), operationId.toString());
        return complete(c, key, EconomyOperationStatus.COMPLETED, null, decimal(senderBefore), decimal(senderAfter));
    }

    private Optional<Account> lockAccount(Connection c, UUID id) throws SQLException {
        String lock = database.getType() == DatabaseType.MYSQL ? " FOR UPDATE" : "";
        try (var s = c.prepareStatement("SELECT balance_minor,version FROM bbe_economy_accounts WHERE player_uuid=?" + lock)) {
            s.setString(1, id.toString());
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(new Account(r.getLong(1), r.getLong(2))) : Optional.empty(); }
        }
    }

    private Optional<Account> findAccount(Connection c, UUID id) throws SQLException {
        try (var s = c.prepareStatement("SELECT balance_minor,version FROM bbe_economy_accounts WHERE player_uuid=?")) {
            s.setString(1, id.toString());
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(new Account(r.getLong(1), r.getLong(2))) : Optional.empty(); }
        }
    }

    private int insertAccountIfAbsent(Connection c, UUID id, long balance, long now) throws SQLException {
        String prefix = database.getType() == DatabaseType.MYSQL ? "INSERT IGNORE" : "INSERT OR IGNORE";
        try (var s = c.prepareStatement(prefix + " INTO bbe_economy_accounts (player_uuid,balance_minor,currency,created_at,updated_at,version,last_operation_id) VALUES (?,?,?,?,?,?,?)")) {
            s.setString(1, id.toString()); s.setLong(2, balance); s.setString(3, CURRENCY); s.setLong(4, now); s.setLong(5, now); s.setLong(6, 0); s.setNull(7, Types.VARCHAR);
            return s.executeUpdate();
        }
    }

    private void updateExact(Connection c, UUID id, long target, long version, String operation) throws SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_economy_accounts SET balance_minor=?,version=version+1,updated_at=?,last_operation_id=? WHERE player_uuid=? AND version=?")) {
            s.setLong(1, target); s.setLong(2, System.currentTimeMillis()); s.setString(3, operation); s.setString(4, id.toString()); s.setLong(5, version);
            if (s.executeUpdate() != 1) throw new SQLException("Optimistic locking conflict");
        }
    }

    private Optional<EconomyOperationReceipt> existing(Connection c, String key) throws SQLException {
        try (var s = c.prepareStatement("SELECT id,player_uuid,amount,status,balance_before,balance_after,idempotency_key,fingerprint,currency,reason,last_error,created_at,source_module,source_reference FROM bbe_economy_operations WHERE idempotency_key=?")) {
            s.setString(1, key);
            try (var r = s.executeQuery()) { return r.next() ? Optional.of(map(r)) : Optional.empty(); }
        }
    }

    private void insertPending(Connection c, UUID id, UUID player, String type, BigDecimal amount, String key, String reason,
                                BigDecimal before, BigDecimal after, Map<String, String> metadata, String fingerprint) throws SQLException {
        Map<String, String> safeMetadata = metadata == null ? Map.of() : metadata;
        try (var s = c.prepareStatement("INSERT INTO bbe_economy_operations (id,player_uuid,operation_type,amount,currency,idempotency_key,reason,source_module,source_reference,status,balance_before,balance_after,created_at,metadata_json,fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, id.toString()); s.setString(2, player.toString()); s.setString(3, type); s.setBigDecimal(4, amount); s.setString(5, CURRENCY); s.setString(6, key); s.setString(7, reason == null ? "" : reason);
            s.setString(8, safeMetadata.getOrDefault("source", "economy")); s.setString(9, safeMetadata.get("reference")); s.setString(10, EconomyOperationStatus.PENDING.name());
            s.setBigDecimal(11, before); s.setBigDecimal(12, after); s.setLong(13, System.currentTimeMillis()); s.setString(14, gson.toJson(safeMetadata)); s.setString(15, fingerprint); s.executeUpdate();
        }
    }

    private EconomyOperationReceipt complete(Connection c, String key, EconomyOperationStatus status, String error,
                                             BigDecimal before, BigDecimal after) throws SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_economy_operations SET status=?,completed_at=?,last_error=?,balance_before=?,balance_after=? WHERE idempotency_key=? AND status=?")) {
            s.setString(1, status.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error); s.setBigDecimal(4, before); s.setBigDecimal(5, after); s.setString(6, key); s.setString(7, EconomyOperationStatus.PENDING.name()); s.executeUpdate();
        }
        return existing(c, key).orElseThrow(() -> new SQLException("Economy operation disappeared"));
    }

    private EconomyOperationReceipt compatible(EconomyOperationReceipt old, String fingerprint, UUID player, BigDecimal amount) {
        if (old.fingerprint() == null || old.fingerprint().equals(fingerprint)) return old.replay();
        return new EconomyOperationReceipt(old.id(), player, amount, EconomyOperationStatus.IDEMPOTENCY_CONFLICT,
                old.balanceBefore(), old.balanceAfter(), old.idempotencyKey(), fingerprint, CURRENCY,
                old.reason(), "IDEMPOTENCY_CONFLICT", false, old.timestamp(), old.sourceModule(), old.externalReference());
    }

    private EconomyOperationReceipt map(ResultSet r) throws SQLException {
        return new EconomyOperationReceipt(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("player_uuid")),
                r.getBigDecimal("amount"), EconomyOperationStatus.valueOf(r.getString("status")), r.getBigDecimal("balance_before"),
                r.getBigDecimal("balance_after"), r.getString("idempotency_key"), r.getString("fingerprint"),
                r.getString("currency"), r.getString("reason"), r.getString("last_error"), false,
                r.getLong("created_at"), r.getString("source_module"), r.getString("source_reference"));
    }

    private BigDecimal decimal(long minor) { return BigDecimal.valueOf(minor, scale()); }
    private EconomyOperationReceipt rejected(UUID player, BigDecimal amount, String key) {
        return new EconomyOperationReceipt(UUID.randomUUID(), player, amount, EconomyOperationStatus.REJECTED, null, null, key, null,
                CURRENCY, null, "INVALID_REQUEST", false, System.currentTimeMillis(), "economy", null);
    }
    private record Account(long balance, long version) { BigDecimal decimal() { return BigDecimal.valueOf(balance, ConfigManager.getEconomyCurrencyScale()); } }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
}
