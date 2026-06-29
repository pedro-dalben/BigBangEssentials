package com.pedrodalben.bigbangessentials.crates.integration;

import com.pedrodalben.bigbangessentials.api.BigBangEssentialsAPI;
import com.pedrodalben.bigbangessentials.api.economy.EconomyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class CrateEconomyIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateEconomyIntegration.class);
    private static final CrateEconomyIntegration INSTANCE = new CrateEconomyIntegration();

    private EconomyService economyService;
    private boolean enabled = false;

    private CrateEconomyIntegration() {
        try {
            this.economyService = BigBangEssentialsAPI.getEconomyService();
            this.enabled = (economyService != null);
            if (enabled) {
                LOGGER.info("Crate economy integration enabled");
            } else {
                LOGGER.info("Crate economy integration disabled - EconomyService not available");
            }
        } catch (Exception e) {
            LOGGER.warn("Crate economy integration unavailable: {}", e.getMessage());
            this.enabled = false;
        }
    }

    public static CrateEconomyIntegration getInstance() {
        return INSTANCE;
    }

    /**
     * Check if a player has sufficient balance.
     */
    public boolean hasBalance(UUID playerId, double amount) {
        if (!enabled || economyService == null) return amount <= 0;
        if (amount <= 0) return true;
        return economyService.getBalance(playerId) >= amount;
    }

    /**
     * Withdraw an amount from a player's balance.
     */
    public boolean withdraw(UUID playerId, double amount, String reason) {
        if (!enabled || economyService == null) {
            return amount <= 0;
        }
        if (amount <= 0) return true;
        return economyService.withdraw(playerId, amount);
    }

    /**
     * Deposit an amount to a player's balance.
     */
    public boolean deposit(UUID playerId, double amount, String reason) {
        if (!enabled || economyService == null) return false;
        if (amount <= 0) return false;
        return economyService.deposit(playerId, amount);
    }

    /**
     * Format an amount using the economy's currency format.
     */
    public String format(double amount) {
        if (!enabled || economyService == null) {
            return String.format("%.2f", amount);
        }
        return economyService.format(amount);
    }

    /**
     * Check if economy integration is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
