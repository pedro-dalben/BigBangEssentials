package com.zerog.bigbangessentials.webdashboard.data;

import com.google.gson.JsonObject;
import com.zerog.bigbangessentials.config.ConfigManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central data collection and management system for the Dashboard API
 * Coordinates all specialized data collectors and provides unified access
 * 
 * Design:
 * - Real-time data collection from Minecraft server
 * - Specialized collectors for different data domains
 * - Efficient caching to reduce server load
 * - Event-driven updates for live data
 */
public class DataCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataCollector.class);
    private static DataCollector INSTANCE;
    
    private final Map<String, CachedData> dataCache;
    
    // Specialized collectors
    private PlayerDataCollector playerCollector;
    private ServerDataCollector serverCollector;
    private GameDataCollector gameCollector;
    private LoggingDataCollector loggingCollector;
    
    private DataCollector() {
        this.dataCache = new ConcurrentHashMap<>();
    }
    
    public static DataCollector getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DataCollector();
        }
        return INSTANCE;
    }
    
    /**
     * Initialize data collector with server instance
     */
    public void initialize(MinecraftServer server) {
        // Initialize specialized collectors
        clearCache();
        this.playerCollector = new PlayerDataCollector(server);
        this.serverCollector = new ServerDataCollector(server);
        this.gameCollector = new GameDataCollector(server);
        this.loggingCollector = new LoggingDataCollector();
        this.gameCollector.clearEvents();
        this.gameCollector.setCollectionEnabled(true);
        
        LOGGER.info("Data Collector initialized with all specialized collectors");
    }
    
    /**
     * Stop data collection
     */
    public void shutdown() {
        if (this.gameCollector != null) {
            this.gameCollector.setCollectionEnabled(false);
            this.gameCollector.clearEvents();
        }
        this.dataCache.clear();
        LOGGER.info("Data Collector stopped");
    }
    
    // ===== PLAYER DATA METHODS =====
    
    public JsonObject getPlayerProfile(UUID playerUuid) {
        return getCachedOrCompute("player_profile_" + playerUuid,
            () -> playerCollector.getPlayerProfile(playerUuid), cacheMillis(2));
    }
    
    public JsonObject getPlayerStatistics(UUID playerUuid) {
        return getCachedOrCompute("player_stats_" + playerUuid,
            () -> playerCollector.getPlayerStatistics(playerUuid), cacheMillis(2));
    }
    
    public JsonObject getPlayerAchievements(UUID playerUuid) {
        return getCachedOrCompute("player_achievements_" + playerUuid,
            () -> playerCollector.getPlayerAchievements(playerUuid), cacheMillis(2));
    }
    
    public JsonObject getPlayerInventory(UUID playerUuid) {
        return getCachedOrCompute("player_inventory_" + playerUuid,
            () -> playerCollector.getPlayerInventory(playerUuid), cacheMillis(2));
    }
    
    public JsonObject getPlayerStatus(UUID playerUuid) {
        return getCachedOrCompute("player_status_" + playerUuid,
            () -> playerCollector.getPlayerStatus(playerUuid), cacheMillis(1));
    }
    
    public JsonObject getPlayerHealth(UUID playerUuid) {
        return getCachedOrCompute("player_health_" + playerUuid,
            () -> playerCollector.getPlayerHealth(playerUuid), cacheMillis(1));
    }
    
    public JsonObject getPlayerXP(UUID playerUuid) {
        return getCachedOrCompute("player_xp_" + playerUuid,
            () -> playerCollector.getPlayerXP(playerUuid), cacheMillis(2));
    }
    
    public JsonObject getPlayerLocation(UUID playerUuid) {
        return getCachedOrCompute("player_location_" + playerUuid,
            () -> playerCollector.getPlayerLocation(playerUuid), cacheMillis(1));
    }
    
    public JsonObject getPlayerHomes(String username) {
        return getCachedOrCompute("player_homes_" + username.toLowerCase(),
            () -> playerCollector.getPlayerHomes(username), cacheMillis(6));
    }
    
    public JsonObject getOnlinePlayers() {
        return getCachedOrCompute("online_players",
            () -> playerCollector.getOnlinePlayers(), cacheMillis(6));
    }
    
    // ===== SERVER DATA METHODS =====
    
    public JsonObject getServerProfile() {
        return getCachedOrCompute("server_profile",
            () -> serverCollector.getServerProfile(), cacheMillis(12));
    }
    
    public JsonObject getServerStatistics() {
        return getCachedOrCompute("server_statistics",
            () -> serverCollector.getServerStatistics(), cacheMillis(2));
    }
    
    /**
     * Collect server status data (cached)
     */
    public JsonObject getServerStatus() {
        return getCachedOrCompute("server_status", 
            () -> serverCollector.getServerStatus(), 1000);
    }
    
    public JsonObject getServerHealth() {
        return serverCollector.getServerHealth();
    }
    
    public JsonObject getServerWorlds() {
        return getCachedOrCompute("server_worlds",
            () -> serverCollector.getServerWorlds(), cacheMillis(6));
    }
    
    public JsonObject getServerConfig() {
        return getCachedOrCompute("server_config",
            () -> serverCollector.getServerConfig(), cacheMillis(12));
    }
    
    public JsonObject getServerPerformance() {
        return getCachedOrCompute("server_performance",
            () -> serverCollector.getServerPerformance(), cacheMillis(2));
    }
    
    /**
     * Collect server info data (cached)
     */
    public JsonObject getServerInfo() {
        return getCachedOrCompute("server_info", 
            () -> serverCollector.getServerProfile(), 60000);
    }
    
    /**
     * Collect memory usage data (cached)
     */
    public JsonObject getMemoryInfo() {
        return getCachedOrCompute("memory_info", () -> {
            JsonObject stats = serverCollector.getServerStatistics();
            return stats.getAsJsonObject("memory");
        }, 2000);
    }
    
    // ===== GAME DATA METHODS =====
    
    public JsonObject getGameEvents(int limit) {
        return getCachedOrCompute("game_events_" + limit,
            () -> gameCollector.getGameEvents(limit), cacheMillis(1));
    }
    
    public JsonObject getGameStatistics() {
        return getCachedOrCompute("game_statistics",
            () -> gameCollector.getGameStatistics(), cacheMillis(2));
    }
    
    public JsonObject getGameActivity() {
        return getCachedOrCompute("game_activity",
            () -> gameCollector.getGameActivity(), cacheMillis(2));
    }
    
    public JsonObject getTopBlocks() {
        return getCachedOrCompute("game_top_blocks",
            () -> gameCollector.getTopBlocks(), cacheMillis(6));
    }
    
    public void clearGameEvents() {
        gameCollector.clearEvents();
    }
    
    // ===== LOGGING & ANALYTICS METHODS =====
    
    public JsonObject getRequestLogs(int limit) {
        return loggingCollector.getRequestLogs(limit);
    }
    
    public JsonObject getErrorLogs(int limit, String severity) {
        return loggingCollector.getErrorLogs(limit, severity);
    }
    
    public JsonObject getPerformanceMetrics() {
        return loggingCollector.getPerformanceMetrics();
    }
    
    public JsonObject getUserActivity() {
        return loggingCollector.getUserActivity();
    }
    
    public JsonObject getServerLogs(int lines) {
        return loggingCollector.getServerLogs(lines);
    }
    
    public JsonObject getErrorStatistics() {
        return loggingCollector.getErrorStatistics();
    }
    
    public void clearLogs(String logType) {
        loggingCollector.clearLogs(logType);
    }
    
    // Static logging methods for API access
    public static void logRequest(String endpoint, String method, int statusCode, 
                                   long responseTimeMs, String username) {
        LoggingDataCollector.logRequest(endpoint, method, statusCode, responseTimeMs, username);
    }
    
    public static void logError(String message, String severity, Exception exception) {
        LoggingDataCollector.logError(message, severity, exception);
    }
    
    // ===== LEGACY/COMPATIBILITY METHODS =====
    
    /**
     * Collect world data (delegated to server collector)
     */
    public JsonObject getWorldData() {
        return serverCollector.getServerWorlds();
    }
    
    /**
     * Collect economy data
     * FUTURE: Integrate with EconomyAPI for player balances and transactions
     */
    public JsonObject getEconomyData() {
        // Placeholder for future economy integration
        return new JsonObject();
    }
    
    /**
     * Get or compute cached data
     */
    private JsonObject getCachedOrCompute(String key, DataSupplier supplier, long cacheDuration) {
        if (cacheDuration <= 0) {
            return supplier.get();
        }

        CachedData cached = dataCache.get(key);
        long now = System.currentTimeMillis();
        
        if (cached != null && (now - cached.timestamp) < cacheDuration) {
            return cached.data;
        }
        
        JsonObject data = supplier.get();
        dataCache.put(key, new CachedData(data, now));
        return data;
    }

    private long cacheMillis(int multiplier) {
        int baseSeconds = ConfigManager.getInstance().getWebDashboardCacheTimeoutSeconds();
        if (baseSeconds <= 0 || multiplier <= 0) {
            return 0L;
        }
        return baseSeconds * 1000L * multiplier;
    }
    
    /**
     * Clear all cached data
     */
    public void clearCache() {
        dataCache.clear();
        LOGGER.info("All cached data cleared");
    }
    
    /**
     * Clear specific cached data
     */
    public void clearCache(String key) {
        dataCache.remove(key);
        LOGGER.info("Cache cleared for key: {}", key);
    }
    
    /**
     * Cached data holder
     */
    private static class CachedData {
        final JsonObject data;
        final long timestamp;
        
        CachedData(JsonObject data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Functional interface for data suppliers
     */
    @FunctionalInterface
    private interface DataSupplier {
        JsonObject get();
    }
}
