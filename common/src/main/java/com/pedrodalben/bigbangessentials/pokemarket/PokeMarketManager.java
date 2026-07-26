package com.pedrodalben.bigbangessentials.pokemarket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.Cobblemon173MarketBridge;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import com.pedrodalben.bigbangessentials.pokemarket.service.*;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;

/** Lifecycle gate. The real module stays unavailable until Cobblemon is installed. */
public final class PokeMarketManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokeMarketManager.class);
    private static final PokeMarketManager INSTANCE = new PokeMarketManager();
    private boolean initialized;
    private PokeMarketListingService listingService;
    private PokeMarketPurchaseService purchaseService;
    private PokeMarketClaimService claimService;
    private PokeMarketExpirationService expirationService;
    private PokeMarketRecoveryService recoveryService;
    private PokeMarketTradeService tradeService;
    private PokeMarketNotificationRepository notificationRepository;
    private Cobblemon173MarketBridge bridge;

    private PokeMarketManager() {}
    public static PokeMarketManager getInstance() { return INSTANCE; }

    public synchronized void initialize() {
        if (!isCobblemonPresent()) throw new IllegalStateException("Cobblemon API not present; PokéMarket remains disabled");
        if (!DatabaseManager.getInstance().isReady()) throw new IllegalStateException("Database unavailable; PokéMarket remains fail-closed");
        bridge = new Cobblemon173MarketBridge();
        if (!Cobblemon173MarketBridge.isSupportedVersion()) {
            throw new IllegalStateException("Unsupported Cobblemon version " + Cobblemon173MarketBridge.runtimeVersion() + "; expected " + Cobblemon173MarketBridge.COBBLEMON_VERSION);
        }
        PokeMarketListingRepository listings = new PokeMarketListingRepository();
        PokeMarketClaimRepository claims = new PokeMarketClaimRepository();
        PokeMarketAuditRepository audit = new PokeMarketAuditRepository();
        listingService = new PokeMarketListingService(bridge, listings, claims, audit);
        purchaseService = new PokeMarketPurchaseService(listings, claims, new PokeMarketTransactionRepository(), audit);
        claimService = new PokeMarketClaimService(bridge, claims);
        expirationService = new PokeMarketExpirationService(listings, claims, audit);
        tradeService = new PokeMarketTradeService(listings, claims, audit);
        notificationRepository = new PokeMarketNotificationRepository();
        com.pedrodalben.bigbangessentials.pokemarket.menu.PokeMarketMenuIntegration.register();
        recoveryService = new PokeMarketRecoveryService(listings, audit);
        recoveryService.recover().thenCompose(ignored -> purchaseService.recover()).thenCompose(ignored -> tradeService.recover()).exceptionally(error -> { LOGGER.error("[PokeMarket] recovery failed", error); return null; });
        expirationService.start();
        initialized = true;
        LOGGER.info("[PokeMarket] initialized");
    }

    public synchronized void shutdown() { if (expirationService != null) expirationService.stop(); initialized = false; LOGGER.info("[PokeMarket] stopped"); }
    /** Re-run only the durable recovery workers; safe to invoke from an admin command. */
    public synchronized java.util.concurrent.CompletableFuture<Void> recoverNow() {
        if (!initialized || recoveryService == null) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("PokéMarket is not initialized"));
        return recoveryService.recover().thenCompose(ignored -> purchaseService.recover()).thenCompose(ignored -> tradeService.recover());
    }
    public java.util.concurrent.CompletableFuture<Void> retry(java.util.UUID operationId) {
        return java.util.concurrent.CompletableFuture.allOf(purchaseService.recover(operationId).thenApply(ignored -> null), tradeService.recover(operationId).thenApply(ignored -> null));
    }
    public boolean isInitialized() { return initialized; }
    public Cobblemon173MarketBridge bridge() { return bridge; }
    public PokeMarketListingService listingService() { return listingService; }
    public PokeMarketPurchaseService purchaseService() { return purchaseService; }
    public PokeMarketTradeService tradeService() { return tradeService; }
    public PokeMarketClaimService claimService() { return claimService; }
    public PokeMarketNotificationRepository notificationRepository() { return notificationRepository; }
    public static boolean isCobblemonPresent() {
        try { Class.forName("com.cobblemon.mod.common.Cobblemon", false, PokeMarketManager.class.getClassLoader()); return true; }
        catch (ClassNotFoundException | LinkageError ignored) { return false; }
    }
}
