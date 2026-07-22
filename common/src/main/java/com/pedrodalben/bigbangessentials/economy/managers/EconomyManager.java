
package com.pedrodalben.bigbangessentials.economy.managers;

import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.config.EconomyConfig;
import com.pedrodalben.bigbangessentials.economy.EconomyTransactionLogger;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
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
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;

import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EconomyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EconomyManager.class);
    
    // Data version tracking - increment when JSON structure changes
    private static final String DATA_VERSION_KEY = "_dataVersion";
    private static final int CURRENT_DATA_VERSION = 1;
    
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
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> data = gson.fromJson(reader, type);
            if (data != null) {
                // Read version if present
                if (data.containsKey(DATA_VERSION_KEY)) {
                    Object versionObj = data.get(DATA_VERSION_KEY);
                    if (versionObj instanceof Number) {
                        currentBalancesVersion = ((Number) versionObj).intValue();
                    }
                    data.remove(DATA_VERSION_KEY); // Don't process version as balance
                }
                
                // Load balances
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (!entry.getKey().startsWith("_")) { // Skip metadata fields
                        balancesCache.put(UUID.fromString(entry.getKey()), new BigDecimal(entry.getValue().toString()));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load balances from file", e);
        }
    }

    private void saveBalancesAtomic() {
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
                Map<String, Object> data = new ConcurrentHashMap<>();
                
                // Add version marker
                data.put(DATA_VERSION_KEY, CURRENT_DATA_VERSION);
                
                // Add balances
                for (Map.Entry<UUID, BigDecimal> entry : balancesCache.entrySet()) {
                    data.put(entry.getKey().toString(), entry.getValue().toPlainString());
                }
                gson.toJson(data, writer);
            }
            
            // Atomically move temp file to balancesFile
            Files.move(tempFile.toPath(), balancesFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            // Update current version tracker
            currentBalancesVersion = CURRENT_DATA_VERSION;
        } catch (IOException e) {
            LOGGER.error("Failed to save balances to file", e);
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
        queueAsyncSave();
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

    public synchronized void setBalance(UUID player, BigDecimal amount) {
        if (databaseMode) {
            if (databaseBackend != null) databaseBackend.setBalance(player, amount, "manager:set:" + UUID.randomUUID(), "SERVER", "Set balance", Map.of("source", "economy-manager" )).join();
            return;
        }
        if (shuttingDown.get()) {
            LOGGER.warn("Attempted to modify balance during shutdown for player {}", player);
            return;
        }
        if (!ConfigManager.allowNegativeBalances() && amount.compareTo(BigDecimal.ZERO) < 0) amount = BigDecimal.ZERO;
        BigDecimal maxBalance = BigDecimal.valueOf(ConfigManager.getMaxBalance());
        if (amount.compareTo(maxBalance) > 0) amount = maxBalance;

        BigDecimal finalAmount = amount;
        BigDecimal oldAmount = balancesCache.put(player, finalAmount);
        lastActivityMap.put(player, System.currentTimeMillis());
        queueAsyncSave();
        queueAsyncSaveActivity();
        
        // Log transaction
        EconomyTransactionLogger.log("SET", player.toString(), "SERVER", finalAmount.toPlainString(), 
            "Set balance (was: " + (oldAmount != null ? oldAmount.toPlainString() : "new account") + ")");
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
        return mutateLocal(player, amount, operationKey, true, reason);
    }

    /** Structured, idempotent debit; boolean methods above remain source-compatible. */
    public EconomyOperationReceipt debit(UUID player, BigDecimal amount, String operationKey, String reason, Map<String, String> metadata) {
        if (databaseMode) {
            if (databaseBackend == null) return failedReceipt(player, amount, operationKey);
            try { return databaseBackend.debit(player, amount, operationKey, reason, metadata).join(); }
            catch (RuntimeException e) { LOGGER.error("Economy debit failed for {}", player, e); return failedReceipt(player, amount, operationKey); }
        }
        return mutateLocal(player, amount, operationKey, false, reason);
    }

    private synchronized EconomyOperationReceipt mutateLocal(UUID player, BigDecimal amount, String key, boolean credit, String reason) {
        if (key == null || key.isBlank() || amount == null || amount.signum() <= 0) return failedReceipt(player, amount, key);
        EconomyOperationReceipt existing = localOperations.get(key);
        if (existing != null) return existing;
        BigDecimal before = getBalance(player);
        BigDecimal after = credit ? updatedBalance(before, amount, BigDecimal.valueOf(ConfigManager.getMaxBalance()), ConfigManager.allowNegativeBalances()) : before.subtract(amount);
        boolean allowed = after != null && (ConfigManager.allowNegativeBalances() || after.signum() >= 0) && !shuttingDown.get();
        if (!allowed) {
            EconomyOperationReceipt rejected = new EconomyOperationReceipt(UUID.randomUUID(), player, amount, shuttingDown.get() ? EconomyOperationStatus.FAILED : EconomyOperationStatus.REJECTED, before, before, key);
            localOperations.put(key, rejected);
            return rejected;
        }
        balancesCache.put(player, after);
        lastActivityMap.put(player, System.currentTimeMillis());
        queueAsyncSave();
        queueAsyncSaveActivity();
        EconomyTransactionLogger.log(credit ? "ADD" : "SUBTRACT", credit ? "SERVER" : player.toString(), credit ? player.toString() : "SERVER", amount.toPlainString(), reason);
        EconomyOperationReceipt receipt = new EconomyOperationReceipt(UUID.randomUUID(), player, amount, EconomyOperationStatus.COMPLETED, before, after, key);
        localOperations.put(key, receipt);
        return receipt;
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
