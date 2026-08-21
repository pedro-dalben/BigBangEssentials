package com.pedrodalben.bigbangessentials.vault;

import com.pedrodalben.bigbangessentials.vault.api.VaultServiceRegistry;
import com.pedrodalben.bigbangessentials.vault.api.VaultServiceRegistry.ServicePriority;
import com.pedrodalben.bigbangessentials.vault.impl.BigBangEssentialsChat;
import com.pedrodalben.bigbangessentials.vault.impl.BigBangEssentialsEconomy;
import com.pedrodalben.bigbangessentials.vault.impl.BigBangEssentialsPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BigBangEssentials Vault sub-system manager.
 * <p>
 * Initialised at server start.  Registers the three built-in BigBangEssentials
 * providers (Economy, Permission, Chat) into {@link VaultServiceRegistry} so
 * that other NeoForge mods can access them via the Vault API without depending
 * on any specific implementation.
 *
 * <h3>Usage for other mod developers</h3>
 * <pre>{@code
 * // Economy
 * VaultServiceRegistry.getInstance().getEconomy().ifPresent(eco -> {
 *     eco.depositPlayer(playerId, 100.0);
 *     double bal = eco.getBalance(playerId);
 * });
 *
 * // Permissions
 * VaultServiceRegistry.getInstance().getPermission().ifPresent(perm -> {
 *     boolean has = perm.playerHas(playerId, "yourmod.use");
 *     perm.playerAddGroup(playerId, "vip");
 * });
 *
 * // Chat metadata (prefix/suffix)
 * VaultServiceRegistry.getInstance().getChat().ifPresent(chat -> {
 *     String prefix = chat.getPlayerPrefix(playerId);
 *     String groupPrefix = chat.getGroupPrefix("admin");
 * });
 * }</pre>
 *
 * <h3>Registering your own provider (higher priority overrides built-in)</h3>
 * <pre>{@code
 * VaultServiceRegistry.getInstance().registerEconomy(
 *     myEconomyImpl, ServicePriority.HIGH, "mymod");
 * }</pre>
 */
public class VaultManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(VaultManager.class);
    private static boolean initialised = false;

    /** Called once during server startup (after permission system is ready). */
    public static void initialize() {
        if (initialised) {
            LOGGER.warn("[VaultAPI] VaultManager.initialize() called more than once — skipping");
            return;
        }

        LOGGER.info("[VaultAPI] Initialising BigBangEssentials Vault API...");

        VaultServiceRegistry registry = VaultServiceRegistry.getInstance();

        // Register built-in Economy provider
        try {
            BigBangEssentialsEconomy economy = new BigBangEssentialsEconomy();
            registry.registerEconomy(economy, ServicePriority.NORMAL, "bigbangessentials");
        } catch (Exception e) {
            LOGGER.error("[VaultAPI] Failed to register Economy provider: {}", e.getMessage(), e);
        }

        // Register built-in Permission provider
        try {
            BigBangEssentialsPermission permission = new BigBangEssentialsPermission();
            registry.registerPermission(permission, ServicePriority.NORMAL, "bigbangessentials");
        } catch (Exception e) {
            LOGGER.error("[VaultAPI] Failed to register Permission provider: {}", e.getMessage(), e);
        }

        // Register built-in Chat provider
        try {
            BigBangEssentialsChat chat = new BigBangEssentialsChat();
            registry.registerChat(chat, ServicePriority.NORMAL, "bigbangessentials");
        } catch (Exception e) {
            LOGGER.error("[VaultAPI] Failed to register Chat provider: {}", e.getMessage(), e);
        }

        registry.logStatus();
        initialised = true;
        LOGGER.info("[VaultAPI] Vault API ready.");
    }

    /** Called during server shutdown to clear all registrations. */
    public static void shutdown() {
        VaultServiceRegistry.getInstance().clear();
        initialised = false;
        LOGGER.info("[VaultAPI] Vault API shut down.");
    }

    /** Convenience accessor — economy (may be empty if disabled). */
    public static java.util.Optional<com.pedrodalben.bigbangessentials.vault.api.VaultEconomy> getEconomy() {
        return VaultServiceRegistry.getInstance().getEconomy();
    }

    /** Convenience accessor — permission. */
    public static java.util.Optional<com.pedrodalben.bigbangessentials.vault.api.VaultPermission> getPermission() {
        return VaultServiceRegistry.getInstance().getPermission();
    }

    /** Convenience accessor — chat metadata. */
    public static java.util.Optional<com.pedrodalben.bigbangessentials.vault.api.VaultChat> getChat() {
        return VaultServiceRegistry.getInstance().getChat();
    }

    private VaultManager() {}
}

