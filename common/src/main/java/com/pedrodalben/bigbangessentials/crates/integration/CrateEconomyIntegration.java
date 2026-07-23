package com.pedrodalben.bigbangessentials.crates.integration;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

public class CrateEconomyIntegration {
    private static final CrateEconomyIntegration INSTANCE = new CrateEconomyIntegration();

    private CrateEconomyIntegration() {}

    public static CrateEconomyIntegration getInstance() {
        return INSTANCE;
    }

    /**
     * Check if a player has sufficient balance.
     */
    public boolean hasBalance(UUID playerId, double amount) {
        if (!Double.isFinite(amount)) return false;
        if (!isEnabled()) return amount <= 0;
        if (amount <= 0) return true;
        return EconomyManager.getInstance().getBalance(playerId).compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    /**
     * Withdraw an amount from a player's balance.
     */
    public boolean withdraw(UUID playerId, double amount, String reason) {
        return withdraw(playerId, amount, reason, "crate:purchase:" + reason);
    }
    public boolean withdraw(UUID playerId, double amount, String reason, String idempotencyKey) {
        if (!Double.isFinite(amount)) return false;
        if (!isEnabled()) {
            return amount <= 0;
        }
        if (amount <= 0) return true;
        return EconomyManager.getInstance().debit(playerId, BigDecimal.valueOf(amount), idempotencyKey, reason, Map.of("source", "crates", "reference", idempotencyKey)).status() == EconomyOperationStatus.COMPLETED;
    }

    /**
     * Deposit an amount to a player's balance.
     */
    public boolean deposit(UUID playerId, double amount, String reason) {
        return deposit(playerId, amount, reason, "crate:refund:" + reason);
    }
    public boolean deposit(UUID playerId, double amount, String reason, String idempotencyKey) {
        if (!Double.isFinite(amount)) return false;
        if (!isEnabled()) return false;
        if (amount <= 0) return false;
        return EconomyManager.getInstance().credit(playerId, BigDecimal.valueOf(amount), idempotencyKey, reason, Map.of("source", "crates", "reference", idempotencyKey)).status() == EconomyOperationStatus.COMPLETED;
    }

    /**
     * Format an amount using the economy's currency format.
     */
    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("%.2f", amount);
        }
        return EconomyManager.getInstance().getCurrencySymbol()
                + String.format(Locale.US, "%." + ConfigManager.getEconomyCurrencyScale() + "f", amount);
    }

    /**
     * Check if economy integration is enabled.
     */
    public boolean isEnabled() {
        return EconomyManager.getInstance().isEnabled();
    }
}
