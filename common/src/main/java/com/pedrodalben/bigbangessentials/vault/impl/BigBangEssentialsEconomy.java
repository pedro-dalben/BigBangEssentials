package com.pedrodalben.bigbangessentials.vault.impl;

import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.vault.api.VaultEconomy;
import net.neoforged.neoforge.common.NeoForge;
import com.pedrodalben.bigbangessentials.api.event.EconomyDepositEvent;
import com.pedrodalben.bigbangessentials.api.event.EconomyWithdrawEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;

/**
 * BigBangEssentials built-in {@link VaultEconomy} implementation.
 * Delegates all calls to {@link EconomyManager} and uses the same formatting
 * and currency configuration as the rest of the mod.
 */
public class BigBangEssentialsEconomy extends VaultEconomy {

    private static final Logger LOGGER = LoggerFactory.getLogger(BigBangEssentialsEconomy.class);
    private final DecimalFormat fmt;

    public BigBangEssentialsEconomy() {
        fmt = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.US));
    }

    @Override public String getName() { return "BigBangEssentials Economy"; }

    @Override
    public boolean isEnabled() {
        return ConfigManager.isEconomyEnabled() && EconomyManager.getInstance() != null;
    }

    /**
     * Format uses the same symbol + decimal pattern as BalanceCommand / EcoCommand.
     */
    @Override
    public String format(double amount) {
        // Use EconomyManager so it respects whatever getCurrencySymbol() returns at runtime
        String symbol = EconomyManager.getInstance().getCurrencySymbol();
        return symbol + fmt.format(amount);
    }

    /**
     * There is no separate singular/plural currency name in the current config;
     * we return the symbol as the name (same behaviour as BalanceCommand).
     */
    @Override public String currencyNameSingular() { return EconomyManager.getInstance().getCurrencySymbol(); }
    @Override public String currencyNamePlural()   { return EconomyManager.getInstance().getCurrencySymbol(); }

    // ── Account ───────────────────────────────────────────────────────────────

    @Override
    public boolean hasAccount(UUID playerId) {
        try {
            return EconomyManager.getInstance().hasAccount(playerId);
        } catch (Exception e) {
            LOGGER.error("VaultEconomy: hasAccount error for {}: {}", playerId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createPlayerAccount(UUID playerId) {
        try {
            if (hasAccount(playerId)) return true;
            return EconomyManager.getInstance().createAccount(playerId);
        } catch (Exception e) {
            LOGGER.error("VaultEconomy: createPlayerAccount error for {}: {}", playerId, e.getMessage());
            return false;
        }
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    @Override
    public double getBalance(UUID playerId) {
        try {
            return EconomyManager.getInstance().getBalance(playerId).doubleValue();
        } catch (Exception e) {
            LOGGER.error("VaultEconomy: getBalance error for {}: {}", playerId, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public boolean has(UUID playerId, double amount) {
        return Double.isFinite(amount) && amount >= 0 && getBalance(playerId) >= amount;
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    /**
     * Withdraw — delegates to EconomyManager.subtractBalance() and fires the
     * same EconomyWithdrawEvent that the /pay and /eco commands fire.
     */
    @Override
    public EconomyResponse withdrawPlayer(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0)
            return fail(amount, playerId, "Cannot withdraw a negative amount");
        try {
            BigDecimal amt = BigDecimal.valueOf(amount).setScale(ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode());
            var receipt = EconomyManager.getInstance().debit(playerId, amt, "vault:withdraw:" + UUID.randomUUID(), "Vault withdrawal", java.util.Map.of("source", "vault"));
            if (receipt.status() != EconomyOperationStatus.COMPLETED)
                return fail(amount, playerId, "Insufficient funds");
            // Fire the same event the rest of the mod uses
            com.pedrodalben.bigbangessentials.util.Platform.postEvent(new EconomyWithdrawEvent(playerId, amount));
            double newBalance = receipt.balanceAfter() != null ? receipt.balanceAfter().doubleValue() : getBalance(playerId);
            return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, "");
        } catch (Exception e) {
            LOGGER.error("VaultEconomy: withdrawPlayer error for {}: {}", playerId, e.getMessage());
            return fail(amount, playerId, e.getMessage());
        }
    }

    /**
     * Deposit — delegates to EconomyManager.addBalance() and fires the
     * same EconomyDepositEvent that the rest of the mod uses.
     */
    @Override
    public EconomyResponse depositPlayer(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0)
            return fail(amount, playerId, "Cannot deposit a negative amount");
        try {
            if (!hasAccount(playerId)) createPlayerAccount(playerId);
            BigDecimal amt = BigDecimal.valueOf(amount).setScale(ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode());
            var receipt = EconomyManager.getInstance().credit(playerId, amt, "vault:deposit:" + UUID.randomUUID(), "Vault deposit", java.util.Map.of("source", "vault"));
            if (receipt.status() != EconomyOperationStatus.COMPLETED)
                return fail(amount, playerId, "Deposit rejected (max balance reached?)");
            // Fire the same event the rest of the mod uses
            com.pedrodalben.bigbangessentials.util.Platform.postEvent(new EconomyDepositEvent(playerId, amount));
            double newBalance = receipt.balanceAfter() != null ? receipt.balanceAfter().doubleValue() : getBalance(playerId);
            return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, "");
        } catch (Exception e) {
            LOGGER.error("VaultEconomy: depositPlayer error for {}: {}", playerId, e.getMessage());
            return fail(amount, playerId, e.getMessage());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private EconomyResponse fail(double amount, UUID playerId, String msg) {
        return new EconomyResponse(amount, getBalance(playerId), EconomyResponse.ResponseType.FAILURE, msg);
    }
}
