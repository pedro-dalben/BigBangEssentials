package com.pedrodalben.bigbangessentials.teleportation.Warp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.integrations.BigBangRegionsPwarpBridge;
import com.pedrodalben.bigbangessentials.teleportation.TeleportLocation;
import com.pedrodalben.bigbangessentials.teleportation.TeleportUtil;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PlayerDataMigration;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages public warp points accessible to all players
 */
@SuppressWarnings({"unused", "InvertedCondition"}) // Public API class
public class WarpManager {
    private static final long PLAYER_WARP_LIMIT_CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(60);

    // --- Persistence for player warps ---
    // private static final String PLAYER_WARPS_FILE = "playerwarps.json";


    // (savePlayerWarps and loadPlayerWarps are defined only once below)
    // Player warps: UUID -> (warpName -> TeleportLocation)
    private final Map<UUID, Map<String, TeleportLocation>> playerWarps = new ConcurrentHashMap<>();
    // Player warp visits: UUID -> (warpName -> visitCount)
    private final Map<UUID, Map<String, Integer>> playerWarpVisits = new ConcurrentHashMap<>();
    private final Map<UUID, CachedWarpLimit> playerWarpLimitCache = new ConcurrentHashMap<>();
    // Player warp config
    private int maxPlayerWarps = 3;
    private boolean allowPlayerWarps = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(WarpManager.class);
    private static final String WARPS_FILE = "warps.json";
    private static final String LEGACY_PLAYER_WARPS_FILE = "playerwarps.json";
    
    // Singleton pattern
    private static class SingletonHolder {
        private static final WarpManager INSTANCE = new WarpManager();
    }
    
    private static WarpManager instanceOverride = null;

    public static WarpManager getInstance() {
        return instanceOverride != null ? instanceOverride : SingletonHolder.INSTANCE;
    }

    public static void setInstance(WarpManager override) {
        if (!isTestingEnvironment()) {
            throw new IllegalStateException("Cannot override singleton instance in production.");
        }
        instanceOverride = override;
    }

    private static boolean isTestingEnvironment() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }
    
    private final Map<String, TeleportLocation> warps = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final BigBangRegionsPwarpBridge bigBangRegionsPwarpBridge = BigBangRegionsPwarpBridge.create();
    
    // Configuration
    private int teleportDelay = 0; // Instant for warps by default
    private boolean requireSafeLocations = true;
    private boolean allowOverworldOnly = false;
    private int maxWarps = 50;
    private boolean caseSensitiveNames = false;
    private boolean allowCrossDimensionWarps = true;

    private WarpManager() {
        loadConfig();
        loadWarps();
        loadPlayerWarps();
    }

    /**
     * Load configuration values from config file
     */
    private void loadConfig() {
        try {
            ConfigManager configManager = ConfigManager.getInstance();
            if (configManager != null) {
                JsonObject config = configManager.getConfig(ConfigManager.MAIN_CONFIG);
                if (config.has("teleportation")) {
                    JsonObject tp = config.getAsJsonObject("teleportation");

                    // Load warp settings
                    if (tp.has("warpSettings")) {
                        JsonObject warpSettings = tp.getAsJsonObject("warpSettings");

                        if (warpSettings.has("enableWarpSafety")) {
                            requireSafeLocations = warpSettings.get("enableWarpSafety").getAsBoolean();
                        }
                        if (warpSettings.has("allowPlayerWarps")) {
                            allowPlayerWarps = warpSettings.get("allowPlayerWarps").getAsBoolean();
                        }
                        if (warpSettings.has("maxPlayerWarps")) {
                            try {
                                maxPlayerWarps = warpSettings.get("maxPlayerWarps").getAsInt();
                            } catch (Exception ignored) {}
                        }
                        if (warpSettings.has("allowCrossDimensionWarps")) {
                            try {
                                allowCrossDimensionWarps = warpSettings.get("allowCrossDimensionWarps").getAsBoolean();
                            } catch (Exception ignored) {}
                        }
                    }

                    // Load general teleportation settings that apply to warps
                    if (tp.has("generalSettings")) {
                        JsonObject generalSettings = tp.getAsJsonObject("generalSettings");
                        if (generalSettings.has("teleportDelay")) {
                            try {
                                teleportDelay = generalSettings.get("teleportDelay").getAsInt();
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            LOGGER.debug("Warp config loaded: requireSafe={}, maxWarps={}, delay={}",
                requireSafeLocations, maxWarps, teleportDelay);
        } catch (Exception e) {
            LOGGER.warn("Failed to load warp config, using defaults: {}", e.getMessage());
        }
    }

    // --- Player Warps API ---
    public java.util.Map<java.util.UUID, java.util.Map<String, TeleportLocation>> getAllPlayerWarps() {
        return playerWarps;
    }

    public int getPlayerWarpVisits(UUID ownerId, String warpName) {
        if (ownerId == null || warpName == null) {
            return 0;
        }
        Map<String, Integer> visits = playerWarpVisits.get(ownerId);
        if (visits == null) {
            return 0;
        }
        return visits.getOrDefault(normalizePlayerWarpName(warpName), 0);
    }

    public int recordPlayerWarpVisit(UUID ownerId, String warpName) {
        if (ownerId == null || warpName == null) {
            return 0;
        }

        String normalizedName = normalizePlayerWarpName(warpName);
        Map<String, Integer> visits = playerWarpVisits.computeIfAbsent(ownerId, key -> new ConcurrentHashMap<>());
        int updated = visits.merge(normalizedName, 1, Integer::sum);
        savePlayerWarpVisits();
        return updated;
    }

    public TeleportLocation getPlayerWarp(UUID ownerId, String warpName) {
        if (ownerId == null || warpName == null) {
            return null;
        }
        Map<String, TeleportLocation> warps = playerWarps.get(ownerId);
        if (warps == null) return null;
        return warps.get(normalizePlayerWarpName(warpName));
    }

    public boolean isPlayerWarpsEnabled() {
        return allowPlayerWarps;
    }

    public int getMaxPlayerWarps() {
        return maxPlayerWarps;
    }

    /**
     * Returns the maximum number of player warps allowed for a player, considering permissions.
     * Priority order:
     *   1. bigbangessentials.pwarp.set.<N> (e.g. .5 = 5 warps) — explicit limit from permission
     *   2. bigbangessentials.pwarp.set (base, no number) — 1 warp
     *   3. bigbangessentials.warp.limit.unlimited — unlimited (legacy compat)
     *   4. bigbangessentials.warp.limit.<N> — explicit limit (legacy compat)
     *   5. Config maxPlayerWarps (fallback)
     *
     * <p>Permission examples:</p>
     * <ul>
     *   <li>bigbangessentials.pwarp.set.5 - Allows up to 5 player warps</li>
     *   <li>bigbangessentials.pwarp.set - Allows 1 player warp</li>
     *   <li>bigbangessentials.warp.limit.10 - Allows 10 player warps (legacy)</li>
     *   <li>bigbangessentials.warp.limit.unlimited - Unlimited player warps (legacy)</li>
     * </ul>
     *
     * @param player The player to check
     * @return Maximum number of player warps allowed (or -1 for unlimited)
     */
    public int getMaxPlayerWarpsForPlayer(ServerPlayer player) {
        if (player == null) {
            return this.maxPlayerWarps;
        }
        return getMaxPlayerWarpsForPlayer(player.getUUID());
    }

    public int getMaxPlayerWarpsForPlayer(UUID playerId) {
        long now = System.currentTimeMillis();
        CachedWarpLimit cached = playerWarpLimitCache.get(playerId);
        if (cached != null && (now - cached.timestampMs) < PLAYER_WARP_LIMIT_CACHE_TTL_MS) {
            return cached.maxWarps;
        }

        int configMax = this.maxPlayerWarps;
        int permMax = resolvePermissionWarpLimit(playerId);

        // Unlimited from permission
        if (permMax == -1) {
            playerWarpLimitCache.put(playerId, new CachedWarpLimit(-1, now));
            return -1;
        }

        // No permission limit found — use config default
        if (permMax == -2) {
            // Config unlimited without permission = 0 (must have pwarp.set permission)
            if (configMax <= 0) {
                playerWarpLimitCache.put(playerId, new CachedWarpLimit(0, now));
                return 0;
            }
            playerWarpLimitCache.put(playerId, new CachedWarpLimit(configMax, now));
            return configMax;
        }

        // Permission limit found — permission limit overrides config
        int maxWarps = Math.max(permMax, configMax);
        playerWarpLimitCache.put(playerId, new CachedWarpLimit(maxWarps, now));
        return maxWarps;
    }

    public boolean createPlayerWarp(ServerPlayer player, String warpName, TeleportLocation location) {
        if (!allowPlayerWarps) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.playerwarps_disabled"));
            return false;
        }
        if (location == null) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.no_per_warp_permission", warpName));
            return false;
        }
        if (!allowCrossDimensionWarps && !isOverworld(location)) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.cross_dimension_disabled"));
            return false;
        }
        if (bigBangRegionsPwarpBridge != null
                && !bigBangRegionsPwarpBridge.canCreate(player.getUUID(), location.getWorldName(),
                floor(location.getX()), floor(location.getY()), floor(location.getZ()))) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.no_per_warp_permission", warpName));
            return false;
        }
        
        UUID playerId = player.getUUID();
        String normalizedName = caseSensitiveNames ? warpName : warpName.toLowerCase();
        
        if (!isValidWarpName(warpName)) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.invalid_name", warpName));
            return false;
        }
        
        // Atomic warp creation with limit check using compute()
        Map<String, TeleportLocation> warps = playerWarps.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        
        // Check if warp already exists and enforce limit atomically
        if (warps.containsKey(normalizedName)) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.already_exists", warpName));
            return false;
        }
        
        // Get max warps for this player (considers permissions)
        int maxWarpsForPlayer = getMaxPlayerWarpsForPlayer(player);

        // Atomic limit check and insertion (-1 means unlimited)
        if (maxWarpsForPlayer >= 0 && warps.size() >= maxWarpsForPlayer) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.playerwarps_limit", maxWarpsForPlayer));
            return false;
        }
        
        // Use putIfAbsent to prevent race condition on duplicate names
        TeleportLocation existing = warps.putIfAbsent(normalizedName, location);
        if (existing != null) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.already_exists", warpName));
            return false;
        }
        
        savePlayerWarps();
        playerWarpVisits.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).putIfAbsent(normalizedName, 0);
        savePlayerWarpVisits();
        player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.teleport.warp.playerwarp_created", warpName, location.getLocationString()));
        LOGGER.info("Player {} created player warp '{}' at {}", player.getName().getString(), warpName, location.getLocationString());
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(new com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.PlayerWarpCreatedEvent(playerId, warpName));
        return true;
    }

    public boolean deletePlayerWarp(UUID playerId, String warpName) {
        Map<String, TeleportLocation> warps = playerWarps.get(playerId);
        if (warps == null) {
            return false;
        }
        
        String normalizedName = caseSensitiveNames ? warpName : warpName.toLowerCase();
        
        // Atomic removal - remove() returns null if key doesn't exist
        TeleportLocation removed = warps.remove(normalizedName);
        if (removed == null) {
            return false;
        }
        
        savePlayerWarps();
        Map<String, Integer> visits = playerWarpVisits.get(playerId);
        if (visits != null) {
            visits.remove(normalizedName);
            if (visits.isEmpty()) {
                playerWarpVisits.remove(playerId);
            }
        }
        savePlayerWarpVisits();
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(new com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.PlayerWarpDeletedEvent(playerId, warpName));
        return true;
    }

    public boolean deletePlayerWarp(ServerPlayer player, String warpName) {
        UUID playerId = player.getUUID();
        if (deletePlayerWarp(playerId, warpName)) {
            player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.teleport.warp.playerwarp_deleted", warpName));
            LOGGER.info("Player {} deleted player warp '{}'", player.getName().getString(), warpName);
            return true;
        }
        player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", warpName));
        return false;
    }

    public TeleportLocation getPlayerWarp(ServerPlayer player, String warpName) {
        return getPlayerWarp(player != null ? player.getUUID() : null, warpName);
    }

    public List<String> getPlayerWarpNames(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Map<String, TeleportLocation> warps = playerWarps.get(playerId);
        if (warps == null) return Collections.emptyList();
        return new ArrayList<>(warps.keySet());
    }


    /**
     * Teleport to another player's warp (admin only)
     */
    public boolean teleportToPlayerWarp(ServerPlayer admin, UUID targetPlayerId, String warpName) {
        if (!isAdmin(admin)) {
            admin.sendSystemMessage(MessageUtil.error("You do not have permission to access other players' warps."));
            return false;
        }
        TeleportLocation location = getPlayerWarp(targetPlayerId, warpName);
        if (location == null) {
            admin.sendSystemMessage(MessageUtil.error("Warp not found for target player."));
            return false;
        }
        if (!canUsePlayerWarp(targetPlayerId, location)) {
            admin.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.no_per_warp_permission", warpName));
            return false;
        }
        TeleportUtil.teleportPlayer(admin, location).thenAccept(result -> {
            if (result.isSuccess()) {
                recordPwarpArrival(admin, targetPlayerId);
            }
        });
        admin.sendSystemMessage(MessageUtil.success("Teleported to target player's warp."));
        return true;
    }

    public void teleportToPlayerWarp(ServerPlayer player, UUID ownerUuid, String warpName, boolean countVisit) {
        if (player == null || ownerUuid == null || warpName == null) {
            return;
        }

        TeleportLocation location = getPlayerWarp(ownerUuid, warpName);
        if (location == null) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", warpName));
            return;
        }
        if (!canUsePlayerWarp(ownerUuid, location)) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.no_per_warp_permission", warpName));
            return;
        }

        // Save current location for /back command
        com.pedrodalben.bigbangessentials.teleportation.Misc.MiscTeleportManager.getInstance().saveBackLocation(player);

        int delayTicks = teleportDelay * 20;
        String finalWarpName = warpName;
        TeleportUtil.teleportPlayer(player, location, delayTicks, true).thenAccept(result -> {
            if (result.isSuccess()) {
                recordPwarpArrival(player, ownerUuid);
                if (countVisit) {
                    recordPlayerWarpVisit(ownerUuid, finalWarpName);
                }
                player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.teleport.warp.success", finalWarpName));
                LOGGER.info("Player {} teleported to player warp '{}' owned by {}", player.getName().getString(), finalWarpName, ownerUuid);
            } else {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.failed", finalWarpName, result.getMessage()));
                LOGGER.warn("Failed to teleport player {} to player warp '{}': {}",
                    player.getName().getString(), finalWarpName, result.getMessage());
            }
        });
    }

    private boolean canUsePlayerWarp(UUID ownerUuid, TeleportLocation location) {
        return bigBangRegionsPwarpBridge == null || bigBangRegionsPwarpBridge.canUse(
            ownerUuid, location.getWorldName(), floor(location.getX()), floor(location.getY()), floor(location.getZ()));
    }

    private void recordPwarpArrival(ServerPlayer player, UUID ownerUuid) {
        if (bigBangRegionsPwarpBridge != null) {
            bigBangRegionsPwarpBridge.recordArrival(player.getUUID(), ownerUuid,
                player.level().dimension().location().toString(), floor(player.getX()), floor(player.getY()), floor(player.getZ()));
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    /**
     * List another player's warps (admin only)
     */
    public List<String> listPlayerWarps(UUID targetPlayerId, ServerPlayer admin) {
        if (!isAdmin(admin)) {
            admin.sendSystemMessage(MessageUtil.error("You do not have permission to list other players' warps."));
            return List.of();
        }
        Map<String, TeleportLocation> warps = playerWarps.get(targetPlayerId);
        if (warps == null) return List.of();
        return new ArrayList<>(warps.keySet());
    }

    /**
     * Check if a player is an admin (placeholder, replace with real permission check)
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isAdmin(ServerPlayer player) {
        // Replace with your real admin/permission check
        return player.hasPermissions(4); // Permission level 4 = admin/op
    }

    // --- Persistence for player warps ---
    private static final String PLAYER_WARPS_FILE = "playerwarps.json";
    private static final String PLAYER_WARP_VISITS_FILE = "playerwarp_visits.json";

    private synchronized void savePlayerWarps() {
        try {
            Map<String, Map<String, TeleportLocation>> serializable = new HashMap<>();
            for (Map.Entry<UUID, Map<String, TeleportLocation>> entry : playerWarps.entrySet()) {
                serializable.put(entry.getKey().toString(), entry.getValue());
            }
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(serializable);
            ResourceUtil.ensureDataDirectory();
            java.nio.file.Files.writeString(ResourceUtil.getDataPath(PLAYER_WARPS_FILE), json);
        } catch (Exception e) {
            System.err.println("[WarpManager] Failed to save player warps: " + e);
        }
    }

    private synchronized void savePlayerWarpVisits() {
        try {
            Map<String, Map<String, Integer>> serializable = new HashMap<>();
            for (Map.Entry<UUID, Map<String, Integer>> entry : playerWarpVisits.entrySet()) {
                serializable.put(entry.getKey().toString(), entry.getValue());
            }
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(serializable);
            ResourceUtil.ensureDataDirectory();
            java.nio.file.Files.writeString(ResourceUtil.getDataPath(PLAYER_WARP_VISITS_FILE), json);
        } catch (Exception e) {
            System.err.println("[WarpManager] Failed to save player warp visits: " + e);
        }
    }

    private void loadPlayerWarps() {
        try {
            java.nio.file.Path path = resolveLegacyWarpFile(PLAYER_WARPS_FILE, true);
            if (path == null || !java.nio.file.Files.exists(path)) return;

            String json = java.nio.file.Files.readString(path);
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Map<String, TeleportLocation>>>(){}.getType();
            Map<String, Map<String, TeleportLocation>> loaded = new com.google.gson.Gson().fromJson(json, type);
            if (loaded == null) {
                return;
            }
            playerWarps.clear();
            for (Map.Entry<String, Map<String, TeleportLocation>> entry : loaded.entrySet()) {
                playerWarps.put(UUID.fromString(entry.getKey()), entry.getValue());
            }
            LOGGER.info("Loaded {} player warps from {}", playerWarps.values().stream().mapToInt(Map::size).sum(), path);
        } catch (Exception e) {
            System.err.println("[WarpManager] Failed to load player warps: " + e);
        }
    }

    private void loadPlayerWarpVisits() {
        try {
            java.nio.file.Path path = ResourceUtil.getDataPath(PLAYER_WARP_VISITS_FILE);
            if (!java.nio.file.Files.exists(path)) {
                return;
            }

            String json = java.nio.file.Files.readString(path);
            if (json == null || json.trim().isEmpty()) {
                return;
            }

            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Map<String, Integer>>>(){}.getType();
            Map<String, Map<String, Integer>> loaded = new com.google.gson.Gson().fromJson(json, type);
            if (loaded == null) {
                return;
            }

            playerWarpVisits.clear();
            for (Map.Entry<String, Map<String, Integer>> entry : loaded.entrySet()) {
                playerWarpVisits.put(UUID.fromString(entry.getKey()), new ConcurrentHashMap<>(entry.getValue()));
            }
            LOGGER.info("Loaded player warp visits for {} players from {}", playerWarpVisits.size(), path);
        } catch (Exception e) {
            System.err.println("[WarpManager] Failed to load player warp visits: " + e);
        }
    }
    
    /**
     * Create a new warp
     */
    public boolean createWarp(ServerPlayer creator, String warpName, TeleportLocation location) {
        // Enforce cross-dimension restriction
        if (!allowCrossDimensionWarps && !isOverworld(location)) {
            creator.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.cross_dimension_disabled"));
            return false;
        }
        
        // Normalize warp name
        String normalizedName = caseSensitiveNames ? warpName : warpName.toLowerCase();
        
        // Validate warp name
        if (!isValidWarpName(warpName)) {
            creator.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.invalid_name", warpName));
            return false;
        }
        
        // Check warp limit before attempting creation
        if (warps.size() >= maxWarps) {
            creator.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.limit_reached", maxWarps));
            return false;
        }
        
        // Check world restriction
        if (allowOverworldOnly && !isOverworld(location)) {
            creator.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.overworld_only"));
            return false;
        }
        
        // Check if location is safe - read from config dynamically
        boolean requireSafe = true; // Default to true for safety
        try {
            JsonObject config = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            if (config.has("teleportation")) {
                JsonObject tp = config.getAsJsonObject("teleportation");
                if (tp.has("warpSettings")) {
                    JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
                    if (warpSettings.has("enableWarpSafety")) {
                        requireSafe = warpSettings.get("enableWarpSafety").getAsBoolean();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read warp safety config, defaulting to enabled: {}", e.getMessage());
        }

        if (requireSafe && !location.isSafe()) {
            TeleportLocation safeLocation = location.findSafeLocation();
            if (safeLocation == null) {
                creator.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.unsafe_location"));
                return false;
            }
            location = safeLocation;
            creator.sendSystemMessage(MessageUtil.warning("commands.bigbangessentials.teleport.warp.moved_to_safety"));
        }
        
        // Atomic warp creation using putIfAbsent to prevent duplicate names
        TeleportLocation existing = warps.putIfAbsent(normalizedName, location);
        if (existing != null) {
            creator.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.already_exists", warpName));
            return false;
        }
        
        saveWarps();
        
        creator.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.teleport.warp.created", warpName, location.getLocationString()));
        LOGGER.info("Player {} created warp '{}' at {}", creator.getName().getString(), warpName, location.getLocationString());
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(new com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.WarpCreatedEvent(warpName));
        
        return true;
    }
    
    /**
     * Create warp at player's current location
     */
    public boolean createWarp(ServerPlayer creator, String warpName) {
        TeleportLocation location = new TeleportLocation(creator);
        return createWarp(creator, warpName, location);
    }
    
    /**
     * Create warp at specific coordinates
     */
    public boolean createWarp(ServerPlayer creator, String warpName, ServerLevel level, BlockPos pos) {
        TeleportLocation location = new TeleportLocation(level, pos, 0.0f, 0.0f, creator.getName().getString());
        return createWarp(creator, warpName, location);
    }
    
    /**
     * Check if warp exists
     */
    public boolean hasWarp(String warpName) {
        String normalizedName = caseSensitiveNames ? warpName : warpName.toLowerCase();
        return warps.containsKey(normalizedName);
    }
    
    /**
     * Get warp by name
     */
    public TeleportLocation getWarp(String warpName) {
        String normalizedName = caseSensitiveNames ? warpName : warpName.toLowerCase();
        return warps.get(normalizedName);
    }
    
    /**
     * Get list of all warp names
     */
    public List<String> getWarpNames() {
        return new ArrayList<>(warps.keySet());
    }
    
    /**
     * Delete a warp by name — admin/console variant that doesn't require a ServerPlayer.
     * Essentials: Warps.removeWarp(name)
     */
    public boolean deleteWarpByAdmin(String warpName, String deletedBy) {
        String normalizedName = caseSensitiveNames ? warpName : warpName.toLowerCase();
        TeleportLocation removed = warps.remove(normalizedName);
        if (removed == null) return false;
        saveWarps();
        if (ConfigManager.getInstance().isLogWarpActionsEnabled()) {
            LOGGER.info("Warp '{}' deleted by {}", warpName, deletedBy);
        }
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(new com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.WarpDeletedEvent(warpName));
        return true;
    }

    /**
     * Delete a warp (admin/staff command)
     */
    public boolean deleteWarp(ServerPlayer player, String warpName) {
        String normalizedName = caseSensitiveNames ? warpName : warpName.toLowerCase();
        
        // Atomic removal using remove() which returns null if key doesn't exist
        TeleportLocation removed = warps.remove(normalizedName);
        if (removed == null) {
            return false;
        }
        
        saveWarps();
        
        if (com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isLogWarpActionsEnabled()) {
            LOGGER.info("Player {} deleted warp '{}'", player.getName().getString(), warpName);
        }
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(new com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.WarpDeletedEvent(warpName));
        return true;
    }
    
    /**
     * Teleport player to warp
     */
    public void teleportToWarp(ServerPlayer player, String warpName) {
        TeleportLocation warp = getWarp(warpName);
        
        if (warp == null) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", warpName));
            return;
        }

        // Enforce maxTeleportDistance if set in config
        int maxDistance = com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().getMaxTeleportDistance();
        if (maxDistance > 0) {
            com.pedrodalben.bigbangessentials.teleportation.TeleportLocation fromLoc = new com.pedrodalben.bigbangessentials.teleportation.TeleportLocation(player);
            if (fromLoc.getWorldName().equals(warp.getWorldName())) {
                double dist = fromLoc.distanceTo(warp);
                if (dist > maxDistance) {
                    player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.teleport.warp.distance_exceeded", maxDistance));
                    return;
                }
            }
        }
        
        // Check if warp location is still safe - read from config dynamically
        boolean requireSafe = true; // Default to true for safety
        try {
            JsonObject config = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            if (config.has("teleportation")) {
                JsonObject tp = config.getAsJsonObject("teleportation");
                if (tp.has("warpSettings")) {
                    JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
                    if (warpSettings.has("enableWarpSafety")) {
                        requireSafe = warpSettings.get("enableWarpSafety").getAsBoolean();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read warp safety config, defaulting to enabled: {}", e.getMessage());
        }

        if (requireSafe && !warp.isSafe()) {
            TeleportLocation safeLocation = warp.findSafeLocation();
            if (safeLocation == null) {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.unsafe", warpName));
                return;
            }
            
            // Update warp to safe location
            String normalizedName = caseSensitiveNames ? warpName : warpName.toLowerCase();
            warps.put(normalizedName, safeLocation);
            saveWarps();
            warp = safeLocation;
            
            player.sendSystemMessage(MessageUtil.warning("commands.bigbangessentials.teleport.warp.moved_to_safety", warpName));
        }
        
        // Save current location for /back command
        com.pedrodalben.bigbangessentials.teleportation.Misc.MiscTeleportManager.getInstance().saveBackLocation(player);

        // Perform teleportation — safety already resolved above, so pass findSafe=false
        int delayTicks = teleportDelay * 20;
        TeleportUtil.teleportPlayer(player, warp, delayTicks, false).thenAccept(result -> {
            if (result.isSuccess()) {
                player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.teleport.warp.success", warpName));
                LOGGER.info("Player {} teleported to warp '{}'", player.getName().getString(), warpName);
            } else {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.failed", warpName, result.getMessage()));
                LOGGER.warn("Failed to teleport player {} to warp '{}': {}", 
                          player.getName().getString(), warpName, result.getMessage());
            }
        });
    }
    
    /**
     * Get formatted list of warps for display
     */
    public String getFormattedWarpsList() {
        if (warps.isEmpty()) {
            return MessageUtil.localize("commands.bigbangessentials.teleport.warp.list_empty");
        }
        
        StringBuilder builder = new StringBuilder();
        // Properly format the header with arguments
        builder.append(MessageUtil.localize("commands.bigbangessentials.teleport.warp.list_header", warps.size(), maxWarps));
        
        List<String> sortedNames = new ArrayList<>(warps.keySet());
        Collections.sort(sortedNames);
        
        for (String warpName : sortedNames) {
            TeleportLocation location = warps.get(warpName);
            // Use the proper list entry format from the language file
            builder.append("\n").append(MessageUtil.localize("commands.bigbangessentials.teleport.warp.list_entry", 
                warpName, location.getLocationString(), location.getCreatedBy()));
        }
        
        return builder.toString();
    }
    
    /**
     * Get warp count
     */
    public int getWarpCount() {
        return warps.size();
    }
    
    /**
     * Check if warp name is valid
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isValidWarpName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        // Check length
        if (name.length() > 32) {
            return false;
        }
        
        // Check characters (alphanumeric, underscore, dash)
        return name.matches("^[a-zA-Z0-9_-]+$");
    }
    
    /**
     * Check if location is in overworld
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isOverworld(TeleportLocation location) {
        return location.getWorldName().contains("overworld");
    }
    
    /**
     * Load warps from file
     */
    private void loadWarps() {
        try {
            java.nio.file.Path path = resolveLegacyWarpFile(WARPS_FILE, false);
            if (path == null || !java.nio.file.Files.exists(path)) {
                LOGGER.info("No warps file found, starting with empty warps");
                return;
            }
            
            String content = java.nio.file.Files.readString(path);
            if (content.trim().isEmpty()) {
                return;
            }
            
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            
            // Load warps
            if (root.has("warps")) {
                JsonObject warpsJson = root.getAsJsonObject("warps");
                
                for (String warpName : warpsJson.keySet()) {
                    try {
                        JsonObject warpJson = warpsJson.getAsJsonObject(warpName);
                        TeleportLocation location = TeleportLocation.fromJson(warpJson);
                        if (location != null) {
                            warps.put(warpName, location);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load warp '{}': {}", warpName, e.getMessage());
                    }
                }
            }
            
            // Load configuration
            if (root.has("config")) {
                JsonObject config = root.getAsJsonObject("config");
                
                if (config.has("teleportDelay")) {
                    teleportDelay = config.get("teleportDelay").getAsInt();
                }
                if (config.has("requireSafeLocations")) {
                    requireSafeLocations = config.get("requireSafeLocations").getAsBoolean();
                }
                if (config.has("allowOverworldOnly")) {
                    allowOverworldOnly = config.get("allowOverworldOnly").getAsBoolean();
                }
                if (config.has("maxWarps")) {
                    maxWarps = config.get("maxWarps").getAsInt();
                }
                if (config.has("caseSensitiveNames")) {
                    caseSensitiveNames = config.get("caseSensitiveNames").getAsBoolean();
                }
            }
            
            LOGGER.info("Loaded {} warps from {}", warps.size(), path);
            
        } catch (Exception e) {
            LOGGER.error("Failed to load warps from file", e);
        }
    }
    
    /**
     * Save warps to file
     */
    private void saveWarps() {
        try {
            JsonObject root = new JsonObject();
            
            // Save warps
            JsonObject warpsJson = new JsonObject();
            for (Map.Entry<String, TeleportLocation> entry : warps.entrySet()) {
                warpsJson.add(entry.getKey(), entry.getValue().toJson());
            }
            root.add("warps", warpsJson);
            
            // Save configuration
            JsonObject config = new JsonObject();
            config.addProperty("teleportDelay", teleportDelay);
            config.addProperty("requireSafeLocations", requireSafeLocations);
            config.addProperty("allowOverworldOnly", allowOverworldOnly);
            config.addProperty("maxWarps", maxWarps);
            config.addProperty("caseSensitiveNames", caseSensitiveNames);
            root.add("config", config);
            
            ResourceUtil.ensureDataDirectory();
            java.nio.file.Files.writeString(ResourceUtil.getDataPath(WARPS_FILE), gson.toJson(root));
            
        } catch (Exception e) {
            LOGGER.error("Failed to save warps to file", e);
        }
    }

    private java.nio.file.Path resolveLegacyWarpFile(String fileName, boolean includePlayerWarpAliases) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        candidates.add(ResourceUtil.getDataPath(fileName).toString());
        candidates.add("run/" + ResourceUtil.DATA_DIR + fileName);
        candidates.add("run/" + fileName);
        candidates.add(fileName);
        candidates.add(ResourceUtil.getConfigPath(fileName).toString());

        if (includePlayerWarpAliases) {
            for (File candidate : PlayerDataMigration.getLegacyDataFileCandidates(LEGACY_PLAYER_WARPS_FILE)) {
                candidates.add(candidate.getPath());
            }
        } else {
            for (File candidate : PlayerDataMigration.getLegacyDataFileCandidates(WARPS_FILE)) {
                candidates.add(candidate.getPath());
            }
        }

        for (String candidate : candidates) {
            java.nio.file.Path path = java.nio.file.Path.of(candidate);
            if (java.nio.file.Files.exists(path)) {
                return path;
            }
        }
        return ResourceUtil.getDataPath(fileName);
    }
    
    // Configuration getters/setters
    public int getTeleportDelay() { return teleportDelay; }
    public void setTeleportDelay(int delay) { this.teleportDelay = Math.max(0, delay); }
    
    public boolean isRequireSafeLocations() { return requireSafeLocations; }
    public void setRequireSafeLocations(boolean require) { this.requireSafeLocations = require; }
    
    public boolean isAllowOverworldOnly() { return allowOverworldOnly; }
    public void setAllowOverworldOnly(boolean allow) { this.allowOverworldOnly = allow; }
    
    public int getMaxWarps() { return maxWarps; }
    public void setMaxWarps(int max) { this.maxWarps = Math.max(1, max); }
    
    public boolean isCaseSensitiveNames() { return caseSensitiveNames; }
    public void setCaseSensitiveNames(boolean caseSensitive) { this.caseSensitiveNames = caseSensitive; }
    
    /**
     * Clear all warps (for admin purposes)
     */
    public void clearAllWarps() {
        warps.clear();
        saveWarps();
        LOGGER.info("Cleared all warps");
    }
    
    /**
     * Get warp statistics
     */
    public String getStatistics() {
        return MessageUtil.localize("commands.bigbangessentials.teleport.warp.list_statistics", 
                                   warps.size(), maxWarps, (warps.size() * 100.0 / maxWarps));
    }

    /**
     * Reload warp data from disk
     */
    public void reload() {
        LOGGER.info("Reloading warp system...");
        loadConfig();
        clearMaxPlayerWarpsCache();
        warps.clear();
        playerWarps.clear();
        playerWarpVisits.clear();
        loadWarps();
        loadPlayerWarps();
        loadPlayerWarpVisits();
        LOGGER.info("Warp system reloaded: {} warps, {} player warps loaded", warps.size(),
            playerWarps.values().stream().mapToInt(Map::size).sum());
    }

    public void invalidateMaxPlayerWarpsCache(UUID playerId) {
        if (playerId != null) {
            playerWarpLimitCache.remove(playerId);
        }
    }

    public void clearMaxPlayerWarpsCache() {
        playerWarpLimitCache.clear();
    }

    private int resolvePermissionWarpLimit(UUID playerId) {
        // 1. Check for unlimited (legacy compat)
        if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasExactPermission(
                playerId, "bigbangessentials.warp.limit.unlimited")) {
            return -1;
        }

        // 2. Check new bigbangessentials.pwarp.set.<N> from high to low
        // Use hasPermission to support wildcards like bigbangessentials.pwarp.set.*
        for (int i = 100; i >= 1; i--) {
            String perm = "bigbangessentials.pwarp.set." + i;
            if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(playerId, perm)) {
                return i;
            }
        }

        // 3. Check base bigbangessentials.pwarp.set (no number = 1)
        // Use hasExactPermission to avoid matching bigbangessentials.pwarp as prefix
        if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasExactPermission(
                playerId, "bigbangessentials.pwarp.set")) {
            return 1;
        }

        // 4. Check legacy bigbangessentials.warp.limit.<N>
        for (int i = 100; i >= 1; i--) {
            String perm = "bigbangessentials.warp.limit." + i;
            if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasExactPermission(playerId, perm)) {
                return i;
            }
        }

        return -2; // No permission limit found
    }

    private static final class CachedWarpLimit {
        final int maxWarps;
        final long timestampMs;

        private CachedWarpLimit(int maxWarps, long timestampMs) {
            this.maxWarps = maxWarps;
            this.timestampMs = timestampMs;
        }
    }

    private String normalizePlayerWarpName(String warpName) {
        return caseSensitiveNames ? warpName : warpName.toLowerCase();
    }
}
