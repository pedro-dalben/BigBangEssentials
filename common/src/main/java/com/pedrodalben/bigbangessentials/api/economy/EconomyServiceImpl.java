package com.pedrodalben.bigbangessentials.api.economy;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.economy.repository.EconomyOperationRepository;

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
    private final DatabaseEconomyService databaseDelegate;
    
    // Migration flag - only migrate once
    private static boolean migrated = false;
    private final EconomyOperationRepository operationRepository = new EconomyOperationRepository();

    public EconomyServiceImpl(Path dataFile) {
        this.dataFile = dataFile;
        this.databaseDelegate = "DATABASE".equals(com.pedrodalben.bigbangessentials.config.ConfigManager.getEconomyBackend()) ? new DatabaseEconomyService() : null;
        
        // One-time migration: Load old data file and import into EconomyManager
        if (databaseDelegate == null && !migrated && Files.exists(dataFile)) {
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
        if (amount <= 0) return false;
        
        // Delegate to EconomyManager
        boolean success = EconomyManager.getInstance().addBalance(playerId, BigDecimal.valueOf(amount));
        
        if (success) {
            com.pedrodalben.bigbangessentials.util.Platform.postEvent(new EconomyDepositEvent(playerId, amount));
        }
        return success;
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        if (amount <= 0) return false;
        
        // Delegate to EconomyManager
        boolean success = EconomyManager.getInstance().subtractBalance(playerId, BigDecimal.valueOf(amount));
        
        if (success) {
            com.pedrodalben.bigbangessentials.util.Platform.postEvent(new EconomyWithdrawEvent(playerId, amount));
        }
        return success;
    }

    @Override
    public boolean setBalance(UUID playerId, double amount) {
        if (amount < 0) return false;
        
        // Delegate to EconomyManager
        EconomyManager.getInstance().setBalance(playerId, BigDecimal.valueOf(amount));
        return true;
    }

    @Override
    public boolean resetBalance(UUID playerId) {
        // Delegate to EconomyManager
        EconomyManager.getInstance().setBalance(playerId, BigDecimal.ZERO);
        return true;
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
        EconomyManager.getInstance().setBalance(playerId, startingBalance);
        return true;
    }

    @Override
    public boolean deleteAccount(UUID playerId) {
        if (!hasAccount(playerId)) return false;
        
        // Delete by setting to zero and removing from cache
        // Note: EconomyManager doesn't have a delete method, so we set to zero
        EconomyManager.getInstance().setBalance(playerId, BigDecimal.ZERO);
        return true;
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
        return journaled(playerId, amount, key, reason, "DEBIT");
    }

    @Override
    public CompletableFuture<EconomyOperationReceipt> credit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return journaled(playerId, amount, key, reason, "CREDIT");
    }

    @Override
    public CompletableFuture<Optional<EconomyOperationReceipt>> findOperation(String key) {
        return operationRepository.find(key);
    }

    private CompletableFuture<EconomyOperationReceipt> journaled(UUID playerId, BigDecimal amount, String key, String reason, String type) {
        if (databaseDelegate != null) return "DEBIT".equals(type)
                ? databaseDelegate.debit(playerId, amount, key, reason, Map.of("source", "legacy-api"))
                : databaseDelegate.credit(playerId, amount, key, reason, Map.of("source", "legacy-api"));
        if (amount == null || amount.scale() > 2 || amount.compareTo(BigDecimal.ZERO) <= 0 || !amount.equals(amount.setScale(2, java.math.RoundingMode.HALF_UP))) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid monetary amount"));
        }
        return operationRepository.find(key).thenCompose(existing -> {
            if (existing.isPresent()) return resume(existing.get(), type, playerId, amount, reason);
            BigDecimal before = EconomyManager.getInstance().getBalance(playerId).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal after = "DEBIT".equals(type) ? before.subtract(amount) : before.add(amount);
            UUID operationId = UUID.randomUUID();
            return operationRepository.create(operationId, playerId, type, amount, key, reason, before, after)
                .thenCompose(ignored -> applyAndComplete(operationId, playerId, amount, key, type, before, after));
        }).exceptionallyCompose(error -> operationRepository.find(key).thenCompose(found -> found.isPresent() ? resume(found.get(), type, playerId, amount, reason) : CompletableFuture.failedFuture(error)));
    }

    private CompletableFuture<EconomyOperationReceipt> resume(EconomyOperationReceipt operation, String type, UUID playerId, BigDecimal amount, String reason) {
        if (operation.status() != EconomyOperationStatus.PENDING) return CompletableFuture.completedFuture(operation);
        BigDecimal current = EconomyManager.getInstance().getBalance(playerId).setScale(2, java.math.RoundingMode.HALF_UP);
        if (current.compareTo(operation.balanceAfter()) == 0) return operationRepository.complete(operation.idempotencyKey(), EconomyOperationStatus.COMPLETED, null).thenApply(x -> new EconomyOperationReceipt(operation.id(), playerId, amount, EconomyOperationStatus.COMPLETED, operation.balanceBefore(), operation.balanceAfter(), operation.idempotencyKey()));
        if (current.compareTo(operation.balanceBefore()) != 0) return operationRepository.complete(operation.idempotencyKey(), EconomyOperationStatus.RECONCILIATION_REQUIRED, "Balance differs from journal before/after").thenApply(x -> new EconomyOperationReceipt(operation.id(), playerId, amount, EconomyOperationStatus.RECONCILIATION_REQUIRED, operation.balanceBefore(), operation.balanceAfter(), operation.idempotencyKey()));
        return applyAndComplete(operation.id(), playerId, amount, operation.idempotencyKey(), type, operation.balanceBefore(), operation.balanceAfter());
    }

    private CompletableFuture<EconomyOperationReceipt> applyAndComplete(UUID id, UUID playerId, BigDecimal amount, String key, String type, BigDecimal before, BigDecimal after) {
        boolean applied = "DEBIT".equals(type) ? EconomyManager.getInstance().subtractBalance(playerId, amount) : EconomyManager.getInstance().addBalance(playerId, amount);
        if (!applied) return operationRepository.complete(key, EconomyOperationStatus.REJECTED, "Insufficient funds or economy disabled").thenApply(x -> new EconomyOperationReceipt(id, playerId, amount, EconomyOperationStatus.REJECTED, before, before, key));
        EconomyManager.getInstance().flush();
        return operationRepository.complete(key, EconomyOperationStatus.COMPLETED, null).thenApply(x -> new EconomyOperationReceipt(id, playerId, amount, EconomyOperationStatus.COMPLETED, before, after, key));
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
