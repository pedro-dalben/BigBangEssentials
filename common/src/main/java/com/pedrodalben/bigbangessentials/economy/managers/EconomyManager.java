
package com.pedrodalben.bigbangessentials.economy.managers;

import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.config.EconomyConfig;
import com.pedrodalben.bigbangessentials.economy.EconomyTransactionLogger;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferReceipt;
import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferStatus;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationFingerprint;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;

import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EconomyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EconomyManager.class);
    
    // Data version tracking - increment when JSON structure changes
    private static final String DATA_VERSION_KEY = "_dataVersion";
    private static final int CURRENT_DATA_VERSION = 2;
    
    // Thread-safe singleton using Bill Pugh Singleton Pattern
    private static class SingletonHolder {
        private static final EconomyManager INSTANCE = new EconomyManager();
    }
    
    public static EconomyManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    // Use ConcurrentHashMap for balances
    private ConcurrentHashMap<UUID, BigDecimal> balancesCache;
    private final ConcurrentHashMap<String, EconomyOperationReceipt> localOperations = new ConcurrentHashMap<>();
    private final DatabaseEconomyService databaseBackend;
    private final boolean databaseMode;
    // Store balances in root/bigbangessentials/balances.json
    private final File balancesFile = com.pedrodalben.bigbangessentials.util.ResourceUtil.getDataFile("balances.json");
    private final Gson gson = new Gson();
    // Use daemon thread to prevent blocking JVM shutdown
private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "EconomyManager-Save");
    t.setDaemon(true);
    return t;
});
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // Track last activity (epoch millis) for each account
    private final ConcurrentHashMap<UUID, Long> lastActivityMap = new ConcurrentHashMap<>();
    private final File lastActivityFile = new File("bigbangessentials/balances_activity.json");
    
    // Track current file versions to avoid unnecessary backups
    private volatile int currentBalancesVersion = CURRENT_DATA_VERSION;
    private volatile int currentActivityVersion = CURRENT_DATA_VERSION;

    private void loadBalances() {
        if (!balancesFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            balancesFile.getParentFile().mkdirs();
        }
        if (!balancesFile.exists()) return;
        try (FileReader reader = new FileReader(balancesFile)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            if (data.has(DATA_VERSION_KEY)) currentBalancesVersion = data.get(DATA_VERSION_KEY).getAsInt();
            if (data.has("_operations")) {
                Type operationType = new TypeToken<Map<String, EconomyOperationReceipt>>(){}.getType();
                Map<String, EconomyOperationReceipt> operations = gson.fromJson(data.get("_operations"), operationType);
                if (operations != null) localOperations.putAll(operations);
            }
            for (Map.Entry<String, com.google.gson.JsonElement> entry : data.entrySet()) {
                if (!entry.getKey().startsWith("_")) balancesCache.put(UUID.fromString(entry.getKey()), new BigDecimal(entry.getValue().getAsString()));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load balances from file", e);
        }
    }

    private synchronized boolean saveBalancesAtomic() {
        if (!balancesFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            balancesFile.getParentFile().mkdirs();
        }
        try {
            // Check if version has changed - if so, create backup before overwriting
            if (balancesFile.exists() && shouldCreateBackup(balancesFile, currentBalancesVersion)) {
                LOGGER.info("Economy data version mismatch detected, creating backup...");
                createBackupFile(balancesFile);
            }
            
            // Write to temp file first
            File tempFile = new File(balancesFile.getAbsolutePath() + ".tmp");
            try (FileWriter writer = new FileWriter(tempFile)) {
                Map<String, Object> data = new java.util.LinkedHashMap<>();
                
                // Add version marker
                data.put(DATA_VERSION_KEY, CURRENT_DATA_VERSION);
                
                // Add balances
                for (Map.Entry<UUID, BigDecimal> entry : balancesCache.entrySet()) {
                    data.put(entry.getKey().toString(), entry.getValue().toPlainString());
                }
                data.put("_operations", new java.util.LinkedHashMap<>(localOperations));
                gson.toJson(data, writer);
            }
            
            // Atomically move temp file to balancesFile
            Files.move(tempFile.toPath(), balancesFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            // Update current version tracker
            currentBalancesVersion = CURRENT_DATA_VERSION;
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save balances to file", e);
            return false;
        }
    }

    private void queueAsyncSave() {
        if (shuttingDown.get()) return; // Don't queue new saves during shutdown
        if (saveQueued.compareAndSet(false, true)) {
            saveExecutor.execute(() -> {
                try {
                    saveBalancesAtomic();
                } finally {
                    saveQueued.set(false);
                }
            });
        }
    }

    private void cleanupInactiveAccounts() {
        if (!ConfigManager.isCleanupInactiveAccountsEnabled()) return;
        long now = System.currentTimeMillis();
        long thresholdMillis = ConfigManager.getInactiveAccountCleanupDays() * 24L * 60L * 60L * 1000L;
        for (UUID uuid : balancesCache.keySet()) {
            Long lastActive = lastActivityMap.get(uuid);
            if (lastActive == null || (now - lastActive) >= thresholdMillis) {
                balancesCache.remove(uuid);
                lastActivityMap.remove(uuid);
            }
        }
            // Scheduled saves remain a durability backstop for legacy callers.
        queueAsyncSaveActivity();
    }

    private void loadLastActivity() {
        if (!lastActivityFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            lastActivityFile.getParentFile().mkdirs();
        }
        if (!lastActivityFile.exists()) return;
        try (FileReader reader = new FileReader(lastActivityFile)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> data = gson.fromJson(reader, type);
            if (data != null) {
                // Read version if present
                if (data.containsKey(DATA_VERSION_KEY)) {
                    Object versionObj = data.get(DATA_VERSION_KEY);
                    if (versionObj instanceof Number) {
                        currentActivityVersion = ((Number) versionObj).intValue();
                    }
                    data.remove(DATA_VERSION_KEY); // Don't process version as activity
                }
                
                // Load activity data
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (!entry.getKey().startsWith("_")) { // Skip metadata fields
                        lastActivityMap.put(UUID.fromString(entry.getKey()), ((Number) entry.getValue()).longValue());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load last activity data", e);
        }
    }

    private void saveLastActivityAtomic() {
        if (!lastActivityFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            lastActivityFile.getParentFile().mkdirs();
        }
        try {
            // Check if version has changed - if so, create backup before overwriting
            if (lastActivityFile.exists() && shouldCreateBackup(lastActivityFile, currentActivityVersion)) {
                LOGGER.info("Activity data version mismatch detected, creating backup...");
                createBackupFile(lastActivityFile);
            }
            
            File tempFile = new File(lastActivityFile.getAbsolutePath() + ".tmp");
            try (FileWriter writer = new FileWriter(tempFile)) {
                Map<String, Object> data = new ConcurrentHashMap<>();
                
                // Add version marker
                data.put(DATA_VERSION_KEY, CURRENT_DATA_VERSION);
                
                // Add activity data
                for (Map.Entry<UUID, Long> entry : lastActivityMap.entrySet()) {
                    data.put(entry.getKey().toString(), entry.getValue());
                }
                gson.toJson(data, writer);
            }
            
            Files.move(tempFile.toPath(), lastActivityFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            // Update current version tracker
            currentActivityVersion = CURRENT_DATA_VERSION;
        } catch (IOException e) {
            LOGGER.error("Failed to save last activity data", e);
        }
    }

    private void queueAsyncSaveActivity() {
        saveExecutor.execute(this::saveLastActivityAtomic);
    }

    private EconomyManager() {
        databaseMode = "DATABASE".equals(ConfigManager.getEconomyBackend());
        databaseBackend = databaseMode && DatabaseManager.getInstance().isReady() ? new DatabaseEconomyService() : null;
        balancesCache = new ConcurrentHashMap<>();
        if (databaseMode) {
            if (databaseBackend == null) LOGGER.error("Economy backend DATABASE selected but database is unavailable; economy is unavailable");
            return;
        }
        // Check global config for module enable
        if (!ConfigManager.isEconomyEnabled()) {
            // Economy is globally disabled, do not load balances or settings
            return;
        }
        // Configuration is loaded automatically by ConfigManager
        // Initialize ConcurrentHashMap for balances
        loadBalances();
        loadLastActivity();
        // Schedule periodic batch save every 5 minutes
        saveExecutor.scheduleAtFixedRate(this::saveBalancesAtomic, 5, 5, TimeUnit.MINUTES);
        saveExecutor.scheduleAtFixedRate(this::saveLastActivityAtomic, 5, 5, TimeUnit.MINUTES);
        
        // Schedule periodic cache statistics logging every 30 minutes
        saveExecutor.scheduleAtFixedRate(this::logCacheMetrics, 30, 30, TimeUnit.MINUTES);
        // Schedule inactive account cleanup every hour (no time window)
        saveExecutor.scheduleAtFixedRate(this::cleanupInactiveAccounts, 1, 1, TimeUnit.HOURS);
    }

    public BigDecimal getBalance(UUID player) {
        if (databaseMode) return databaseBackend == null ? BigDecimal.ZERO : databaseBackend.getBalanceDecimal(player);
        return balancesCache.computeIfAbsent(player, 
            uuid -> BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance()));
    }

    /** Non-blocking balance boundary for integrations; the legacy getter remains for compatibility. */
    public CompletableFuture<BigDecimal> getBalanceAsync(UUID player) {
        if (databaseMode) return databaseBackend == null ? CompletableFuture.completedFuture(BigDecimal.ZERO) : databaseBackend.getBalanceDecimalAsync(player);
        return CompletableFuture.completedFuture(getBalance(player));
    }

    public synchronized void setBalance(UUID player, BigDecimal amount) {
        setBalanceChecked(player, amount);
    }

    /** Sets a balance and reports whether the durable snapshot was committed. */
    public synchronized boolean setBalanceChecked(UUID player, BigDecimal amount) {
        if (player == null || amount == null) return false;
        if (databaseMode) {
            if (databaseBackend == null) return false;
            try { return databaseBackend.setBalance(player, amount, "manager:set:" + UUID.randomUUID(), "SERVER", "Set balance", Map.of("source", "economy-manager" )).join().status() == EconomyOperationStatus.COMPLETED; }
            catch (RuntimeException e) { return false; }
        }
        if (shuttingDown.get()) {
            LOGGER.warn("Attempted to modify balance during shutdown for player {}", player);
            return false;
        }
        if (!ConfigManager.allowNegativeBalances() && amount.compareTo(BigDecimal.ZERO) < 0) amount = BigDecimal.ZERO;
        BigDecimal maxBalance = BigDecimal.valueOf(ConfigManager.getMaxBalance());
        if (amount.compareTo(maxBalance) > 0) amount = maxBalance;

        BigDecimal finalAmount = amount.setScale(ConfigManager.getEconomyCurrencyScale(), java.math.RoundingMode.HALF_UP);
        BigDecimal oldAmount = balancesCache.put(player, finalAmount);
        Long oldActivity = lastActivityMap.get(player);
        lastActivityMap.put(player, System.currentTimeMillis());
        if (!saveBalancesAtomic()) {
            if (oldAmount == null) balancesCache.remove(player); else balancesCache.put(player, oldAmount);
            if (oldActivity == null) lastActivityMap.remove(player); else lastActivityMap.put(player, oldActivity);
            return false;
        }
        queueAsyncSaveActivity();
        
        // Log transaction
        EconomyTransactionLogger.log("SET", player.toString(), "SERVER", finalAmount.toPlainString(), 
            "Set balance (was: " + (oldAmount != null ? oldAmount.toPlainString() : "new account") + ")");
        return true;
    }

    public synchronized boolean addBalance(UUID player, BigDecimal amount) {
        return credit(player, amount, "manager:credit:" + UUID.randomUUID(), "Credit", Map.of("source", "economy-manager")).status() == EconomyOperationStatus.COMPLETED;
    }

    static BigDecimal updatedBalance(BigDecimal current, BigDecimal amount, BigDecimal maxBalance, boolean allowNegative) {
        BigDecimal updated = current.add(amount);
        return (!allowNegative && updated.signum() < 0) || updated.compareTo(maxBalance) > 0 ? null : updated;
    }

    /** Idempotent credit for transactional consumers; JSON mode keeps its legacy atomic path. */
    public boolean addBalance(UUID player, BigDecimal amount, String operationKey, String reason, Map<String, String> metadata) {
        return credit(player, amount, operationKey, reason, metadata).status() == EconomyOperationStatus.COMPLETED;
    }

    public synchronized boolean subtractBalance(UUID player, BigDecimal amount) {
        return debit(player, amount, "manager:debit:" + UUID.randomUUID(), "Debit", Map.of("source", "economy-manager")).status() == EconomyOperationStatus.COMPLETED;
    }

    /** Idempotent debit for transactional consumers; JSON mode keeps its legacy atomic path. */
    public boolean subtractBalance(UUID player, BigDecimal amount, String operationKey, String reason, Map<String, String> metadata) {
        return debit(player, amount, operationKey, reason, metadata).status() == EconomyOperationStatus.COMPLETED;
    }

    /** Structured, idempotent credit; boolean methods above remain source-compatible. */
    public EconomyOperationReceipt credit(UUID player, BigDecimal amount, String operationKey, String reason, Map<String, String> metadata) {
        if (databaseMode) {
            if (databaseBackend == null) return failedReceipt(player, amount, operationKey);
            try { return databaseBackend.credit(player, amount, operationKey, reason, metadata).join(); }
            catch (RuntimeException e) { LOGGER.error("Economy credit failed for {}", player, e); return failedReceipt(player, amount, operationKey); }
        }
        return mutateLocal(player, amount, operationKey, true, reason, metadata);
    }

    /** Structured, idempotent debit; boolean methods above remain source-compatible. */
    public EconomyOperationReceipt debit(UUID player, BigDecimal amount, String operationKey, String reason, Map<String, String> metadata) {
        if (databaseMode) {
            if (databaseBackend == null) return failedReceipt(player, amount, operationKey);
            try { return databaseBackend.debit(player, amount, operationKey, reason, metadata).join(); }
            catch (RuntimeException e) { LOGGER.error("Economy debit failed for {}", player, e); return failedReceipt(player, amount, operationKey); }
        }
        return mutateLocal(player, amount, operationKey, false, reason, metadata);
    }

    public CompletableFuture<EconomyOperationReceipt> creditAsync(UUID player, BigDecimal amount, String operationKey, String reason, Map<String, String> metadata) {
        if (databaseMode) return databaseBackend == null ? CompletableFuture.completedFuture(failedReceipt(player, amount, operationKey)) : databaseBackend.credit(player, amount, operationKey, reason, metadata);
        return CompletableFuture.supplyAsync(() -> credit(player, amount, operationKey, reason, metadata));
    }

    public CompletableFuture<EconomyOperationReceipt> debitAsync(UUID player, BigDecimal amount, String operationKey, String reason, Map<String, String> metadata) {
        if (databaseMode) return databaseBackend == null ? CompletableFuture.completedFuture(failedReceipt(player, amount, operationKey)) : databaseBackend.debit(player, amount, operationKey, reason, metadata);
        return CompletableFuture.supplyAsync(() -> debit(player, amount, operationKey, reason, metadata));
    }

    /** Commerce-only atomic transfer. It has no /pay policy and never blocks its caller. */
    public CompletableFuture<CommercialTransferReceipt> commercialTransferAsync(UUID sender, UUID receiver,
                                                                                  BigDecimal amount, String operationKey,
                                                                                  String sourceModule) {
        if (databaseMode) {
            if (databaseBackend == null) {
                return CompletableFuture.completedFuture(new CommercialTransferReceipt(UUID.randomUUID(), sender, receiver,
                        amount, null, null, null, null, operationKey, null,
                        CommercialTransferStatus.DATABASE_UNAVAILABLE, "Database economy is unavailable", false,
                        System.currentTimeMillis()));
            }
            return databaseBackend.commercialTransfer(sender, receiver, amount, operationKey, sourceModule);
        }
        return CompletableFuture.supplyAsync(() -> commercialTransferLocal(sender, receiver, amount, operationKey, sourceModule));
    }

    public CompletableFuture<EconomyOperationReceipt> transferResultAsync(UUID sender, UUID receiver, BigDecimal amount, BigDecimal fee, String operationKey) {
        if (databaseMode) {
            if (databaseBackend == null) return CompletableFuture.completedFuture(failedReceipt(sender, amount, operationKey));
            return databaseBackend.transferResult(sender, receiver, amount, fee, operationKey);
        }
        BigDecimal before = getBalance(sender);
        boolean success = transfer(sender, receiver, amount, fee, operationKey);
        BigDecimal after = getBalance(sender);
        return CompletableFuture.completedFuture(new EconomyOperationReceipt(UUID.randomUUID(), sender, amount,
                success ? EconomyOperationStatus.COMPLETED : EconomyOperationStatus.REJECTED, before, after, operationKey, null));
    }

    /** Transfers money without leaving the sender debited when the recipient cannot be credited. */
    public synchronized boolean transfer(UUID sender, UUID receiver, BigDecimal amount, BigDecimal fee) {
        return transfer(sender, receiver, amount, fee, "pay:" + sender + ":" + receiver + ":" + UUID.randomUUID());
    }

    /** Idempotent transfer boundary used by payments and integrations. */
    public synchronized boolean transfer(UUID sender, UUID receiver, BigDecimal amount, BigDecimal fee, String operationKey) {
        if (sender == null || receiver == null || sender.equals(receiver) || amount == null || fee == null
                || amount.signum() <= 0 || fee.signum() < 0 || operationKey == null || operationKey.isBlank()) return false;
        BigDecimal credit = amount.subtract(fee);
        if (credit.signum() <= 0) return false;

        try {
            amount = normalize(amount);
            fee = normalize(fee);
        } catch (RuntimeException e) { return false; }
        String fingerprint = com.pedrodalben.bigbangessentials.api.economy.EconomyOperationFingerprint.of(sender,
                "TRANSFER", amount, "money", "pay", operationKey,
                Map.of("source", "pay", "reference", operationKey, "receiver", receiver.toString(), "fee", fee.toPlainString()));
        if (databaseMode) {
            if (databaseBackend == null) return false;
            try { return databaseBackend.transfer(sender, receiver, amount, fee, operationKey).join(); }
            catch (RuntimeException e) { LOGGER.error("Economy transfer failed from {} to {}", sender, receiver, e); return false; }
        }
        if (shuttingDown.get()) return false;

        EconomyOperationReceipt existing = localOperations.get(operationKey);
        if (existing != null) {
            if (existing.fingerprint() != null && !existing.fingerprint().equals(fingerprint)) return false;
            return existing.status() == EconomyOperationStatus.COMPLETED;
        }

        BigDecimal senderBefore = getBalance(sender);
        BigDecimal receiverBefore = getBalance(receiver);
        BigDecimal senderAfter = updatedBalance(senderBefore, amount.negate(), BigDecimal.valueOf(ConfigManager.getMaxBalance()), ConfigManager.allowNegativeBalances());
        BigDecimal receiverAfter = updatedBalance(receiverBefore, credit, BigDecimal.valueOf(ConfigManager.getMaxBalance()), ConfigManager.allowNegativeBalances());
        if (senderAfter == null || receiverAfter == null) return false;

        balancesCache.put(sender, senderAfter);
        balancesCache.put(receiver, receiverAfter);
        long now = System.currentTimeMillis();
        lastActivityMap.put(sender, now);
        lastActivityMap.put(receiver, now);
        EconomyOperationReceipt receipt = new EconomyOperationReceipt(UUID.randomUUID(), sender, amount,
                EconomyOperationStatus.COMPLETED, senderBefore, senderAfter, operationKey, fingerprint);
        localOperations.put(operationKey, receipt);
        if (!saveBalancesAtomic()) {
            balancesCache.put(sender, senderBefore);
            balancesCache.put(receiver, receiverBefore);
            localOperations.remove(operationKey);
            return false;
        }
        queueAsyncSaveActivity();
        EconomyTransactionLogger.log("SUBTRACT", sender.toString(), "SERVER", amount.toPlainString(), "Player payment");
        EconomyTransactionLogger.log("ADD", "SERVER", receiver.toString(), credit.toPlainString(), "Player payment");
        return true;
    }

    private synchronized CommercialTransferReceipt commercialTransferLocal(UUID sender, UUID receiver, BigDecimal amount,
                                                                              String operationKey, String sourceModule) {
        if (sender == null || receiver == null || sender.equals(receiver) || amount == null || amount.signum() <= 0
                || operationKey == null || operationKey.isBlank() || sourceModule == null || sourceModule.isBlank()) {
            return new CommercialTransferReceipt(UUID.randomUUID(), sender, receiver, amount, null, null, null, null,
                    operationKey, null, CommercialTransferStatus.INVALID_AMOUNT, "Invalid transfer request", false,
                    System.currentTimeMillis());
        }
        BigDecimal normalized;
        try { normalized = amount.setScale(ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode()); }
        catch (RuntimeException invalid) {
            return new CommercialTransferReceipt(UUID.randomUUID(), sender, receiver, amount, null, null, null, null,
                    operationKey, null, CommercialTransferStatus.INVALID_AMOUNT, invalid.getMessage(), false,
                    System.currentTimeMillis());
        }
        String fingerprint = EconomyOperationFingerprint.of(sender, "COMMERCE_TRANSFER", normalized, "money",
                sourceModule, operationKey, Map.of("source", sourceModule, "reference", operationKey, "receiver", receiver.toString()));
        EconomyOperationReceipt existing = localOperations.get(operationKey);
        if (existing != null) {
            if (!fingerprint.equals(existing.fingerprint())) {
                return new CommercialTransferReceipt(existing.id(), sender, receiver, normalized,
                        existing.balanceBefore(), existing.balanceAfter(), null, null, operationKey, fingerprint,
                        CommercialTransferStatus.IDEMPOTENCY_CONFLICT, "IDEMPOTENCY_CONFLICT", false, existing.timestamp());
            }
            return new CommercialTransferReceipt(existing.id(), sender, receiver, normalized,
                    existing.balanceBefore(), existing.balanceAfter(), getBalance(receiver), getBalance(receiver),
                    operationKey, fingerprint,
                    existing.status() == EconomyOperationStatus.COMPLETED ? CommercialTransferStatus.IDEMPOTENT_REPLAY : CommercialTransferStatus.TECHNICAL_FAILURE,
                    existing.error(), true, existing.timestamp());
        }
        BigDecimal senderBefore = getBalance(sender);
        BigDecimal receiverBefore = getBalance(receiver);
        BigDecimal senderAfter = senderBefore.subtract(normalized);
        BigDecimal receiverAfter = updatedBalance(receiverBefore, normalized,
                BigDecimal.valueOf(ConfigManager.getMaxBalance()), ConfigManager.allowNegativeBalances());
        CommercialTransferStatus rejection = senderAfter.signum() < 0 ? CommercialTransferStatus.INSUFFICIENT_FUNDS
                : receiverAfter == null ? CommercialTransferStatus.MAXIMUM_BALANCE : null;
        if (rejection != null) {
            EconomyOperationReceipt failed = new EconomyOperationReceipt(UUID.randomUUID(), sender, normalized,
                    EconomyOperationStatus.REJECTED, senderBefore, senderBefore, operationKey, fingerprint,
                    "money", "Commerce transfer", rejection.name(), false, System.currentTimeMillis(), sourceModule, operationKey);
            localOperations.put(operationKey, failed);
            saveBalancesAtomic();
            return new CommercialTransferReceipt(failed.id(), sender, receiver, normalized, senderBefore, senderBefore,
                    receiverBefore, receiverBefore, operationKey, fingerprint, rejection, rejection.name(), false, failed.timestamp());
        }
        balancesCache.put(sender, senderAfter);
        balancesCache.put(receiver, receiverAfter);
        EconomyOperationReceipt completed = new EconomyOperationReceipt(UUID.randomUUID(), sender, normalized,
                EconomyOperationStatus.COMPLETED, senderBefore, senderAfter, operationKey, fingerprint,
                "money", "Commerce transfer", null, false, System.currentTimeMillis(), sourceModule, operationKey);
        localOperations.put(operationKey, completed);
        if (!saveBalancesAtomic()) {
            balancesCache.put(sender, senderBefore);
            balancesCache.put(receiver, receiverBefore);
            localOperations.remove(operationKey);
            return new CommercialTransferReceipt(completed.id(), sender, receiver, normalized, senderBefore, senderBefore,
                    receiverBefore, receiverBefore, operationKey, fingerprint, CommercialTransferStatus.TECHNICAL_FAILURE,
                    "Could not persist commerce transfer", false, completed.timestamp());
        }
        return new CommercialTransferReceipt(completed.id(), sender, receiver, normalized, senderBefore, senderAfter,
                receiverBefore, receiverAfter, operationKey, fingerprint, CommercialTransferStatus.COMPLETED, null,
                false, completed.timestamp());
    }

    private synchronized EconomyOperationReceipt mutateLocal(UUID player, BigDecimal amount, String key, boolean credit, String reason, Map<String, String> metadata) {
        if (key == null || key.isBlank() || amount == null || amount.signum() <= 0) return failedReceipt(player, amount, key);
        try { amount = normalize(amount); } catch (RuntimeException e) { return failedReceipt(player, amount, key); }
        Map<String, String> safeMetadata = metadata == null ? Map.of() : metadata;
        String type = credit ? "CREDIT" : "DEBIT";
        String fingerprint = com.pedrodalben.bigbangessentials.api.economy.EconomyOperationFingerprint.of(player, type,
                amount, "money", safeMetadata.getOrDefault("source", "economy"), safeMetadata.get("reference"), safeMetadata);
        EconomyOperationReceipt existing = localOperations.get(key);
        if (existing != null) {
            if (existing.fingerprint() != null && !existing.fingerprint().equals(fingerprint)) {
                return new EconomyOperationReceipt(existing.id(), player, amount, EconomyOperationStatus.IDEMPOTENCY_CONFLICT,
                        existing.balanceBefore(), existing.balanceAfter(), key, fingerprint);
            }
            return existing.replay();
        }
        BigDecimal before = getBalance(player);
        BigDecimal after = credit ? updatedBalance(before, amount, BigDecimal.valueOf(ConfigManager.getMaxBalance()), ConfigManager.allowNegativeBalances()) : before.subtract(amount);
        boolean allowed = after != null && (ConfigManager.allowNegativeBalances() || after.signum() >= 0) && !shuttingDown.get();
        if (!allowed) {
            EconomyOperationReceipt rejected = new EconomyOperationReceipt(UUID.randomUUID(), player, amount, shuttingDown.get() ? EconomyOperationStatus.FAILED : EconomyOperationStatus.REJECTED, before, before, key, fingerprint);
            localOperations.put(key, rejected);
            saveBalancesAtomic();
            return rejected;
        }
        BigDecimal oldBalance = before;
        balancesCache.put(player, after);
        lastActivityMap.put(player, System.currentTimeMillis());
        EconomyOperationReceipt receipt = new EconomyOperationReceipt(UUID.randomUUID(), player, amount,
                EconomyOperationStatus.COMPLETED, before, after, key, fingerprint);
        localOperations.put(key, receipt);
        if (!saveBalancesAtomic()) {
            balancesCache.put(player, oldBalance);
            localOperations.remove(key);
            return failedReceipt(player, amount, key);
        }
        queueAsyncSaveActivity();
        EconomyTransactionLogger.log(credit ? "ADD" : "SUBTRACT", credit ? "SERVER" : player.toString(), credit ? player.toString() : "SERVER", amount.toPlainString(), reason);
        return receipt;
    }

    private BigDecimal normalize(BigDecimal amount) {
        int scale = ConfigManager.getEconomyCurrencyScale();
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Invalid monetary amount");
        BigDecimal normalized = amount.setScale(scale, java.math.RoundingMode.UNNECESSARY);
        if (normalized.compareTo(BigDecimal.valueOf(ConfigManager.getMaxBalance())) > 0) throw new IllegalArgumentException("Amount too large");
        return normalized;
    }

    private EconomyOperationReceipt failedReceipt(UUID player, BigDecimal amount, String key) {
        BigDecimal balance;
        try { balance = getBalance(player); } catch (RuntimeException ignored) { balance = BigDecimal.ZERO; }
        return new EconomyOperationReceipt(UUID.randomUUID(), player, amount, EconomyOperationStatus.FAILED, balance, balance, key);
    }

    public Map<UUID, BigDecimal> getAllBalances() {
        if (databaseMode) return databaseBackend == null ? Map.of() : databaseBackend.getAllBalances();
        return new ConcurrentHashMap<>(balancesCache);
    }

    public java.util.Optional<EconomyOperationReceipt> findOperation(String key) {
        if (key == null || key.isBlank()) return java.util.Optional.empty();
        if (databaseMode) return databaseBackend == null ? java.util.Optional.empty() : databaseBackend.findOperation(key).join();
        return java.util.Optional.ofNullable(localOperations.get(key));
    }

    /**
     * @deprecated Use ConfigManager.getInstance() directly for config access
     */
    @Deprecated
    public EconomyConfig getConfig() {
        return new EconomyConfig(); // Return a fresh instance that delegates to ConfigManager
    }

    public boolean isEnabled() {
        return ConfigManager.isEconomyEnabled() && (!databaseMode || databaseBackend != null);
    }

    public String getCurrencySymbol() {
        return ConfigManager.getCurrencySymbol();
    }

    // Vault compatibility stub removed; use EconomyService API instead

    /**
     * Manually optimize the balances cache by cleaning up expired or low-activity entries.
     * This can be called after large batch operations or periodically for memory efficiency.
     */
    @SuppressWarnings("unused") // Public API method
    public void optimizeCache() {
        // ConcurrentHashMap doesn't need explicit cleanup
        // Account cleanup is handled by cleanupInactiveAccounts
    }

    /**
     * Returns cache statistics for monitoring and tuning.
     */
    @SuppressWarnings("unused") // Public API method
    public String getCacheStats() {
        return "EconomyManager Cache Size: " + balancesCache.size();
    }
    
    /**
     * Logs cache metrics for monitoring and debugging.
     */
    private void logCacheMetrics() {
        LOGGER.info("EconomyManager Cache Metrics - Size: {}", balancesCache.size());
    }
    
    /**
     * Shuts down the economy manager and its executor services properly.
     */
    public void shutdown() {
        LOGGER.info("Shutting down EconomyManager...");
        
        // Set shutdown flag to prevent new operations
        shuttingDown.set(true);
        if (databaseMode) {
            EconomyTransactionLogger.shutdown();
            return;
        }
        
        // Wait a moment for any in-flight operations to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Save any pending data (this will block)
        saveBalancesAtomic();
        saveLastActivityAtomic();
        
        // Shutdown executor service
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warn("EconomyManager executor did not terminate gracefully, forcing shutdown...");
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for EconomyManager executor shutdown");
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        EconomyTransactionLogger.shutdown();
        
        LOGGER.info("EconomyManager shutdown complete.");
    }

    /** Durability barrier for journaled operations. */
    public synchronized void flush() {
        if (databaseMode) return;
        saveBalancesAtomic();
    }
    
    /**
     * Check if a backup should be created by comparing file version with current version.
     * Only creates backup if file exists and version differs (similar to ConfigManager behavior).
     */
    @SuppressWarnings("unused") // currentVersion parameter reserved for future versioning logic
    private boolean shouldCreateBackup(File file, int currentVersion) {
        if (!file.exists()) {
            return false; // No file to backup
        }
        
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> data = gson.fromJson(reader, type);
            
            if (data != null && data.containsKey(DATA_VERSION_KEY)) {
                Object versionObj = data.get(DATA_VERSION_KEY);
                if (versionObj instanceof Number) {
                    int fileVersion = ((Number) versionObj).intValue();
                    // Only backup if version differs
                    return fileVersion != CURRENT_DATA_VERSION;
                }
            }
            
            // No version field means old format - create backup
            return true;
            
        } catch (Exception e) {
            LOGGER.warn("Error checking version for {}: {}", file.getName(), e.getMessage());
            return false; // Don't backup on error
        }
    }
    
    /**
     * Create backup file with incremental numbering (.bak1, .bak2, etc.)
     * Same behavior as ConfigManager for consistency.
     */
    private void createBackupFile(File originalFile) {
        try {
            File parent = originalFile.getParentFile();
            String baseName = originalFile.getName();
            
            // Find next available backup number
            File backupFile = null;
            for (int i = 1; i <= 999; i++) {
                File candidate = new File(parent, baseName + ".bak" + i);
                if (!candidate.exists()) {
                    backupFile = candidate;
                    break;
                }
            }
            
            // Fallback if we somehow have 999 backups
            if (backupFile == null) {
                backupFile = new File(parent, baseName + ".bak999");
            }
            
            // Copy existing file to backup
            Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("✓ Created backup: {} -> {} (version mismatch detected)", 
                originalFile.getName(), backupFile.getName());
            
        } catch (IOException e) {
            LOGGER.error("Failed to create backup for {}: {}", originalFile.getName(), e.getMessage());
        }
    }
}
