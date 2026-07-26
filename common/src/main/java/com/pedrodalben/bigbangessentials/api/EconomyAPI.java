package com.pedrodalben.bigbangessentials.api;

import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.economy.managers.PayToggleManager;
import com.pedrodalben.bigbangessentials.economy.managers.TransactionHistoryManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;

/**
 * Public API for other mods to interact with BigBangEssentials Economy.
 * All methods are thread-safe and delegate to the internal managers.
 */
public class EconomyAPI {
    /**
     * Get a player's current balance.
     */
    public static BigDecimal getBalance(UUID player) {
        return EconomyManager.getInstance().getBalance(player);
    }

    /**
     * Set a player's balance. Respects negative balance config.
     */
    public static void setBalance(UUID player, BigDecimal amount) {
        EconomyManager.getInstance().setBalance(player, amount);
    }

    /**
     * Deposit money to a player's account. Returns true if successful.
     */
    public static boolean deposit(UUID player, BigDecimal amount) {
        return EconomyManager.getInstance().addBalance(player, amount);
    }

    /**
     * Withdraw money from a player's account. Returns true if successful.
     */
    public static boolean withdraw(UUID player, BigDecimal amount) {
        return EconomyManager.getInstance().subtractBalance(player, amount);
    }

    public static com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt deposit(
            UUID player, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return EconomyManager.getInstance().credit(player, amount, key, reason, metadata);
    }

    public static com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt withdraw(
            UUID player, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return EconomyManager.getInstance().debit(player, amount, key, reason, metadata);
    }

    /** Async structured deposit. Database-backed calls never block the caller on JDBC. */
    public static CompletableFuture<EconomyOperationReceipt> depositAsync(UUID player, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return EconomyManager.getInstance().creditAsync(player, amount, key, reason, metadata);
    }

    /** Async structured withdrawal. Database-backed calls never block the caller on JDBC. */
    public static CompletableFuture<EconomyOperationReceipt> withdrawAsync(UUID player, BigDecimal amount, String key, String reason, Map<String, String> metadata) {
        return EconomyManager.getInstance().debitAsync(player, amount, key, reason, metadata);
    }

    /**
     * Check if a player is accepting payments (paytoggle).
     */
    public static boolean isPayToggled(UUID player) {
        return PayToggleManager.getInstance().getPayToggle(player);
    }

    /**
     * Set a player's paytoggle state.
     */
    public static void setPayToggle(UUID player, boolean enabled) {
        PayToggleManager.getInstance().setPayToggle(player, enabled);
    }

    /**
     * Get a player's recent transaction history (as strings).
     */
    public static List<String> getTransactionHistory(UUID player) {
        return TransactionHistoryManager.getInstance().getHistory(player);
    }

    /**
     * Pay another player with a transaction fee applied. Returns true if successful.
     * The fee is removed from the system (no bank accounts).
     * This operation is ATOMIC to prevent double-spending exploits.
     */
    public static boolean payPlayer(UUID sender, UUID receiver, BigDecimal amount) {
        return payPlayer(sender, receiver, amount, "pay:" + sender + ":" + receiver + ":" + UUID.randomUUID());
    }

    /** Idempotent payment overload; the request key is part of the durable operation. */
    public static boolean payPlayer(UUID sender, UUID receiver, BigDecimal amount, String requestKey) {
        if (sender == null || receiver == null || amount == null || !isValidAmount(amount)) return false;
        if (sender.equals(receiver)) return false; // Prevent self-payment
        
        EconomyManager manager = EconomyManager.getInstance();
        BigDecimal fee;
        try {
            fee = amount.multiply(ConfigManager.getTaxPercentageDecimal().movePointLeft(2))
                    .setScale(ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode());
        } catch (ArithmeticException e) { return false; }
        BigDecimal netAmount = amount.subtract(fee);
        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) return false; // Fee too high for amount

        return manager.transfer(sender, receiver, amount, fee, requestKey);
    }

    /** Async structured payment; the receipt describes the sender-side atomic transfer. */
    public static CompletableFuture<EconomyOperationReceipt> payPlayerAsync(UUID sender, UUID receiver, BigDecimal amount, String requestKey) {
        if (sender == null || receiver == null || amount == null || sender.equals(receiver) || !isValidAmount(amount)) {
            return CompletableFuture.completedFuture(new EconomyOperationReceipt(UUID.randomUUID(), sender, amount,
                    com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.REJECTED, null, null, requestKey, null));
        }
        BigDecimal fee;
        try {
            fee = amount.multiply(ConfigManager.getTaxPercentageDecimal().movePointLeft(2))
                    .setScale(ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode());
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(new EconomyOperationReceipt(UUID.randomUUID(), sender, amount,
                    com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.REJECTED, null, null, requestKey, null));
        }
        if (amount.subtract(fee).signum() <= 0) {
            return CompletableFuture.completedFuture(new EconomyOperationReceipt(UUID.randomUUID(), sender, amount,
                    com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.REJECTED, null, null, requestKey, null));
        }
        return EconomyManager.getInstance().transferResultAsync(sender, receiver, amount, fee, requestKey);
    }

    private static boolean isValidAmount(BigDecimal amount) {
        try {
            if (amount.signum() <= 0) return false;
            int scale = ConfigManager.getEconomyCurrencyScale();
            amount.setScale(scale, java.math.RoundingMode.UNNECESSARY);
            return amount.compareTo(ConfigManager.getInstance().getMaxEconomyAmount()) <= 0;
        } catch (RuntimeException e) { return false; }
    }
}
