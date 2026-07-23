package com.pedrodalben.bigbangessentials.api.economy;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.neoforged.neoforge.common.NeoForge;
import com.pedrodalben.bigbangessentials.api.event.EconomyDepositEvent;
import com.pedrodalben.bigbangessentials.api.event.EconomyWithdrawEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the EconomyService interface.
 * 
 * IMPORTANT: This class is now a WRAPPER around EconomyManager.
 * It no longer manages its own file or data - all operations are delegated
 * to EconomyManager to prevent data corruption from multiple systems
 * writing to the same balances.json file.
 * 
 * This class exists purely for API compatibility with existing code.
 * All actual balance storage is handled by EconomyManager.
 * 
 * @deprecated Use EconomyManager directly or EconomyAPI instead
 */
@Deprecated
public class EconomyServiceImpl implements EconomyService, IdempotentEconomyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EconomyServiceImpl.class);
    
    // No longer used - kept for API compatibility
    private final Path dataFile;
    
    // Migration flag - only migrate once
    private static boolean migrated = false;

    public EconomyServiceImpl(Path dataFile) {
        this.dataFile = dataFile;
        // One-time migration: Load old data file and import into EconomyManager
        if (!migrated && Files.exists(dataFile)) {
            migrated = true;
            LOGGER.info("=== EconomyServiceImpl Migration ===");
            LOGGER.info("Detecting old balance data format - migrating to EconomyManager...");
            migrateOldBalances();
        } else {
            LOGGER.debug("EconomyServiceImpl initialized as wrapper around EconomyManager");
        }
    }

    @Override
    public double getBalance(UUID playerId) {
        // Delegate to EconomyManager
        BigDecimal balance = EconomyManager.getInstance().getBalance(playerId);
        return balance.doubleValue();
    }

    @Override
    public boolean deposit(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return false;
        
        // Delegate to EconomyManager
        boolean success = EconomyManager.getInstance().addBalance(playerId, BigDecimal.valueOf(amount));
        
        if (success) {
            com.pedrodalben.bigbangessentials.util.Platform.postEvent(new EconomyDepositEvent(playerId, amount));
        }
        return success;
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return false;
        
        // Delegate to EconomyManager
        boolean success = EconomyManager.getInstance().subtractBalance(playerId, BigDecimal.valueOf(amount));
        
        if (success) {
            com.pedrodalben.bigbangessentials.util.Platform.postEvent(new EconomyWithdrawEvent(playerId, amount));
        }
        return success;
    }

    @Override
    public boolean setBalance(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        
        // Delegate to EconomyManager
        return EconomyManager.getInstance().setBalanceChecked(playerId, BigDecimal.valueOf(amount));
    }

    @Override
    public boolean resetBalance(UUID playerId) {
        // Delegate to EconomyManager
        return EconomyManager.getInstance().setBalanceChecked(playerId, BigDecimal.ZERO);
    }

    @Override
    public boolean hasAccount(UUID playerId) {
        // Check if player has a balance in EconomyManager's cache
        return EconomyManager.getInstance().getAllBalances().containsKey(playerId);
    }

    @Override
    public boolean createAccount(UUID playerId) {
        // Check if already exists
        if (hasAccount(playerId)) return false;
        
        // Create by setting starting balance
        BigDecimal startingBalance = BigDecimal.valueOf(com.pedrodalben.bigbangessentials.config.ConfigManager.getEconomyStartingBalance());
        return EconomyManager.getInstance().setBalanceChecked(playerId, startingBalance);
    }

    @Override
    public boolean deleteAccount(UUID playerId) {
        if (!hasAccount(playerId)) return false;
        
        // Delete by setting to zero and removing from cache
        // Note: EconomyManager doesn't have a delete method, so we set to zero
        return EconomyManager.getInstance().setBalanceChecked(playerId, BigDecimal.ZERO);
    }

    @Override
    public String format(double amount) {
        return String.format("%s%.2f", getCurrencySymbol(), amount);
    }

    @Override
    public String getCurrencySymbol() {
        return com.pedrodalben.bigbangessentials.config.ConfigManager.getCurrencySymbol();
    }

    @Override
    public CompletableFuture<EconomyOperationReceipt> debit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return CompletableFuture.completedFuture(EconomyManager.getInstance().debit(playerId, amount, key, reason, metadata));
    }

    @Override
    public CompletableFuture<EconomyOperationReceipt> credit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return CompletableFuture.completedFuture(EconomyManager.getInstance().credit(playerId, amount, key, reason, metadata));
    }

    @Override
    public CompletableFuture<Optional<EconomyOperationReceipt>> findOperation(String key) {
        return CompletableFuture.completedFuture(EconomyManager.getInstance().findOperation(key));
    }
    
    /**
     * One-time migration of old balance data into EconomyManager.
     * This prevents data loss when switching from the old file format
     * to the new EconomyManager format with version tracking.
     */
    private void migrateOldBalances() {
        try {
            if (!Files.exists(dataFile)) {
                LOGGER.info("No old balance data found, skipping migration");
                return;
            }
            
            // Read old format
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                java.lang.reflect.Type type = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> raw = new Gson().fromJson(reader, type);
                
                if (raw == null || raw.isEmpty()) {
                    LOGGER.info("Old balance file is empty, skipping migration");
                    return;
                }
                
                // Check if it's already the new format (has _dataVersion)
                if (raw.containsKey("_dataVersion")) {
                    LOGGER.info("Balance data already in new format, no migration needed");
                    return;
                }
                
                // Migrate each balance to EconomyManager
                int migratedCount = 0;
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        double amount = ((Number) entry.getValue()).doubleValue();
                        
                        // Set in EconomyManager
                        EconomyManager.getInstance().setBalance(playerId, BigDecimal.valueOf(amount));
                        migratedCount++;
                        
                    } catch (Exception e) {
                        LOGGER.warn("Failed to migrate balance for key: {}", entry.getKey(), e);
                    }
                }
                
                LOGGER.info("✓ Successfully migrated {} player balances to EconomyManager", migratedCount);
                
                // Rename old file as backup
                Path backupPath = dataFile.getParent().resolve(dataFile.getFileName() + ".old");
                Files.move(dataFile, backupPath);
                LOGGER.info("✓ Old balance file backed up to: {}", backupPath.getFileName());
                
            }
        } catch (Exception e) {
            LOGGER.error("Failed to migrate old balance data - manual recovery may be needed!", e);
        }
    }
    
    /**
     * Get balance as Optional (for API compatibility).
     * 
     * @deprecated Use getBalance() instead
     */
    @Deprecated
    public Optional<Double> getBalanceOptional(UUID playerId) {
        double balance = getBalance(playerId);
        return Optional.of(balance);
    }
}
