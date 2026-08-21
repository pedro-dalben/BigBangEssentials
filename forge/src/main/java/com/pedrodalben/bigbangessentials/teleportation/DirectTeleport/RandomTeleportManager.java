package com.pedrodalben.bigbangessentials.teleportation.DirectTeleport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.teleportation.TeleportLocation;
import com.pedrodalben.bigbangessentials.teleportation.TeleportUtil;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RandomTeleportManager — Essentials-inspired random teleport system for NeoForge.
 *
 * Ported concepts from com.earth2me.essentials.RandomTeleport:
 *  - Named locations with centre, min/max range configuration
 *  - Equally-distributed random offset (4-rotation rectangle method)
 *  - Pre-computation cache (filled asynchronously, drained on demand)
 *  - Nether-aware Y detection (scan up from y=32 to below bedrock ceiling)
 *  - World-border awareness
 *  - Excluded biome list
 *  - Configurable find-attempts and cache-threshold
 */
public class RandomTeleportManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RandomTeleportManager.class);
    private static final Random RANDOM = new Random();
    private static final int MAX_SEARCHES_PER_TICK = 2;
    private static final ExecutorService CHUNK_REQUEST_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "BigBangEssentials-RTP-ChunkRequests");
        thread.setDaemon(true);
        return thread;
    });

    // Default location name used when no argument is supplied
    private static final String DEFAULT_LOCATION_KEY = "default";

    // Singleton
    private static class Holder {
        static final RandomTeleportManager INSTANCE = new RandomTeleportManager();
    }
    public static RandomTeleportManager getInstance() { return Holder.INSTANCE; }

    // Cache: world + location-name -> queue of ready TeleportLocations
    private final Map<String, ConcurrentLinkedQueue<TeleportLocation>> locationCache = new ConcurrentHashMap<>();

    // Per-player cooldown tracking (ms timestamps)
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // All search state is advanced on the server thread; completions re-enter it through server.execute().
    private final Queue<SearchRequest> searchQueue = new ConcurrentLinkedQueue<>();
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<TeleportLocation>> pendingSearches = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> pendingCacheFills = new ConcurrentHashMap<>();

    private RandomTeleportManager() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Execute a /tpr for a player, using either the default location or a named one.
     * Returns a future that completes with true on success.
     */
    public CompletableFuture<Boolean> randomTeleport(ServerPlayer player, String locationName) {
        // Cooldown check
        int cooldownSecs = getTprCooldown();
        if (cooldownSecs > 0) {
            long last = cooldowns.getOrDefault(player.getUUID(), 0L);
            long remaining = (last + cooldownSecs * 1000L) - System.currentTimeMillis();
            if (remaining > 0) {
                long secs = (remaining / 1000) + 1;
                player.sendSystemMessage(MessageUtil.error(
                        "commands.bigbangessentials.teleport.misc.tpr_cooldown",
                        String.valueOf(secs)));
                return CompletableFuture.completedFuture(false);
            }
        }

        ServerLevel targetLevel = resolveTargetLevel(player);
        if (targetLevel == null) {
            player.sendSystemMessage(MessageUtil.error(
                    "commands.bigbangessentials.teleport.misc.tpr_no_configured_world"));
            return CompletableFuture.completedFuture(false);
        }

        String name = (locationName == null || locationName.isEmpty()) ? resolveDefaultName(targetLevel) : locationName;
        if (!activePlayers.add(player.getUUID())) {
            player.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.teleport.misc.tpr_searching"));
            return CompletableFuture.completedFuture(false);
        }
        player.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.teleport.misc.tpr_searching"));

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        getRandomLocation(targetLevel, name)
                .thenAccept(loc -> {
                    if (loc == null) {
                        activePlayers.remove(player.getUUID());
                        player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.misc.tpr_no_safe_location"));
                        result.complete(false);
                        return;
                    }

                    if (player.hasDisconnected()) {
                        activePlayers.remove(player.getUUID());
                        result.complete(false);
                        return;
                    }

                    // Save back-location before teleporting
                    com.pedrodalben.bigbangessentials.teleportation.Misc.MiscTeleportManager.getInstance()
                            .saveBackLocation(player);

                    int delayTicks = getTeleportDelaySecs() * 20;
                    TeleportUtil.teleportPlayer(player, loc, delayTicks, false /* we already ensured safety */)
                            .whenComplete((tpResult, error) -> {
                                if (error != null || tpResult == null) {
                                    player.sendSystemMessage(MessageUtil.error(
                                            "commands.bigbangessentials.teleport.misc.tpr_failed",
                                            error == null ? "Teleport failed" : error.getMessage()));
                                    result.complete(false);
                                    activePlayers.remove(player.getUUID());
                                    return;
                                }
                                if (tpResult.isSuccess()) {
                                    cooldowns.put(player.getUUID(), System.currentTimeMillis());
                                    player.sendSystemMessage(MessageUtil.success(
                                            "commands.bigbangessentials.teleport.misc.tpr_success",
                                            String.valueOf((int) loc.getX()),
                                            String.valueOf((int) loc.getY()),
                                            String.valueOf((int) loc.getZ())));
                                    LOGGER.info("Player {} randomly teleported to ({}, {}, {}) in {}",
                                            player.getName().getString(),
                                            (int) loc.getX(), (int) loc.getY(), (int) loc.getZ(),
                                            loc.getWorldName());
                                    result.complete(true);

                                    // Pre-warm cache in background
                                    prewarmCache(targetLevel, name);
                                } else {
                                    player.sendSystemMessage(MessageUtil.error(
                                            "commands.bigbangessentials.teleport.misc.tpr_failed",
                                            tpResult.getMessage()));
                                    result.complete(false);
                                }
                                activePlayers.remove(player.getUUID());
                            });
                })
                .exceptionally(ex -> {
                    activePlayers.remove(player.getUUID());
                    LOGGER.error("RandomTeleport error for {}: {}", player.getName().getString(), ex.getMessage(), ex);
                    player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.misc.tpr_failed", ex.getMessage()));
                    result.complete(false);
                    return null;
                });

        return result;
    }

    // -----------------------------------------------------------------------
    // Core location finder
    // -----------------------------------------------------------------------

    /**
     * Returns a random safe TeleportLocation for the given named config slot.
     * Uses cache if available, else queues a bounded asynchronous search.
     */
    public CompletableFuture<TeleportLocation> getRandomLocation(ServerLevel level, String name) {
        Queue<TeleportLocation> cache = getCache(level, name);
        if (!cache.isEmpty()) {
            return CompletableFuture.completedFuture(cache.poll());
        }
        // Cache miss — enqueue a bounded, asynchronous search.
        double[] center = getCenter(level, name);
        double minRange = getMinRange(name);
        double maxRange = getMaxRange(level, name);
        int attempts = getFindAttempts();
        return enqueueSearch(level, center[0], center[1], center[2], minRange, maxRange, name, attempts, null);
    }

    /**
     * Find with explicit parameters (no named config slot).
     */
    public CompletableFuture<TeleportLocation> getRandomLocation(ServerLevel level,
                                                                  double cx, double cy, double cz,
                                                                  double minRange, double maxRange) {
        return enqueueSearch(level, cx, cy, cz, minRange, maxRange, null, getFindAttempts(), null);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private CompletableFuture<TeleportLocation> enqueueSearch(ServerLevel level,
                                                               double cx, double cy, double cz,
                                                               double minRange, double maxRange,
                                                               String locationName, int attemptsLeft,
                                                               String cacheKey) {
        CompletableFuture<TeleportLocation> result = new CompletableFuture<>();
        pendingSearches.add(result);
        searchQueue.add(new SearchRequest(result, level, cx, cy, cz, minRange, maxRange,
                locationName, Math.max(0, attemptsLeft), cacheKey));
        return result;
    }

    /** Called once per server tick by both loader integrations. */
    public void onServerTick(MinecraftServer server) {
        for (int started = 0; started < MAX_SEARCHES_PER_TICK; started++) {
            SearchRequest request = searchQueue.poll();
            if (request == null) return;
            if (request.result().isDone()) continue;
            if (request.attemptsLeft() <= 0) {
                completeSearch(request, null);
                continue;
            }
            startSearchAttempt(server, request);
        }
    }

    public void onServerStop() {
        SearchRequest request;
        while ((request = searchQueue.poll()) != null) {
            completeSearch(request, null);
        }
        pendingSearches.forEach(result -> result.complete(null));
        pendingSearches.clear();
        activePlayers.clear();
        pendingCacheFills.clear();
    }

    private void startSearchAttempt(MinecraftServer server, SearchRequest request) {
        double[] offset = randomOffset(request.minRange(), request.maxRange());
        double x = clampToWorldBorder(request.level(), request.cx() + offset[0], true);
        double z = clampToWorldBorder(request.level(), request.cz() + offset[1], false);
        int chunkX = (int) x >> 4;
        int chunkZ = (int) z >> 4;

        CompletableFuture<Boolean> generated = isOnlyPreGeneratedChunks()
                ? isChunkGenerated(request.level(), chunkX, chunkZ)
                : CompletableFuture.completedFuture(true);
        generated.whenComplete((available, error) -> server.execute(() -> {
            if (request.result().isDone()) return;
            if (error != null || !Boolean.TRUE.equals(available)) {
                retrySearch(server, request);
                return;
            }

            requestChunkAsync(request.level(), chunkX, chunkZ)
                    .whenComplete((chunkResult, chunkError) -> server.execute(() -> {
                        if (request.result().isDone()) return;
                        ChunkAccess chunk = chunkResult == null ? null : chunkResult.left().orElse(null);
                        if (chunkError != null || !(chunk instanceof LevelChunk)) {
                            retrySearch(server, request);
                            return;
                        }

                        TeleportLocation location = findSafeY(request.level(), x, z, request.locationName());
                        if (location != null && isValid(location, request.locationName())) {
                            completeSearch(request, location);
                        } else {
                            retrySearch(server, request);
                        }
                    }));
        }));
    }

    private void retrySearch(MinecraftServer server, SearchRequest request) {
        if (request.attemptsLeft() <= 1) {
            completeSearch(request, null);
            return;
        }
        searchQueue.add(request.withAttemptsLeft(request.attemptsLeft() - 1));
    }

    private void completeSearch(SearchRequest request, TeleportLocation location) {
        pendingSearches.remove(request.result());
        if (request.cacheKey() != null) {
            if (location != null) getCache(request.level(), request.locationName()).add(location);
            pendingCacheFills.computeIfPresent(request.cacheKey(), (key, count) -> count <= 1 ? null : count - 1);
        }
        request.result().complete(location);
    }

    private CompletableFuture<com.mojang.datafixers.util.Either<ChunkAccess, net.minecraft.server.level.ChunkHolder.ChunkLoadingFailure>> requestChunkAsync(ServerLevel level, int chunkX, int chunkZ) {
        boolean allowGeneration = !isOnlyPreGeneratedChunks();
        return CompletableFuture.supplyAsync(
                () -> level.getChunkSource().getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, allowGeneration),
                CHUNK_REQUEST_EXECUTOR).thenCompose(future -> future);
    }

    /**
     * Finds a safe Y coordinate for the given X,Z and returns a TeleportLocation,
     * or null if none is found.
     */
    private TeleportLocation findSafeY(ServerLevel level, double x, double z, String locationName) {
        try {
            boolean isNether = level.dimensionType().ultraWarm(); // ultrawarm == nether-like

            int ix = (int) Math.floor(x);
            int iz = (int) Math.floor(z);
            int y;

            if (isNether) {
                y = findNetherY(level, ix, iz);
            } else {
                y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        ix, iz);
            }

            if (y <= level.getMinBuildHeight()) {
                return null;
            }

            // Verify 2-block clearance (feet + head)
            BlockPos feet = new BlockPos(ix, y, iz);
            if (!isSafeSpot(level, feet)) {
                // Try scanning up a few blocks in case heightmap was a leaf/carpet
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos candidate = feet.above(dy);
                    if (isSafeSpot(level, candidate)) {
                        y = candidate.getY();
                        feet = candidate;
                        break;
                    }
                }
                if (!isSafeSpot(level, feet)) return null;
            }

            return new TeleportLocation(level, feet,
                    RANDOM.nextFloat() * 360f - 180f,
                    0f,
                    "RandomTeleport");

        } catch (Exception e) {
            LOGGER.debug("findSafeY error at ({},{}): {}", x, z, e.getMessage());
            return null;
        }
    }

    /**
     * Nether Y scan: scan up from y=32 to find an air gap below the bedrock ceiling.
     */
    private int findNetherY(ServerLevel level, int x, int z) {
        int maxScan = level.getMaxBuildHeight() - 1;
        for (int y = 32; y < maxScan; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            // Stop at bedrock (approaching ceiling)
            if (state.is(net.minecraft.world.level.block.Blocks.BEDROCK)) break;
            if (isSafeSpot(level, pos)) return y;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * A spot is safe if: solid block below, air at feet, air at head (no lava/fire etc.)
     */
    private boolean isSafeSpot(ServerLevel level, BlockPos feet) {
        BlockPos ground = feet.below();
        BlockPos head = feet.above();

        BlockState groundState = level.getBlockState(ground);
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);

        if (!groundState.isSolid()) return false;
        if (!feetState.isAir()) return false;
        if (!headState.isAir()) return false;

        // Reject dangerous ground types
        if (isDangerous(groundState)) return false;
        if (isDangerous(feetState)) return false;

        return true;
    }

    private boolean isDangerous(BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        return block == net.minecraft.world.level.block.Blocks.LAVA
                || block == net.minecraft.world.level.block.Blocks.WATER
                || block == net.minecraft.world.level.block.Blocks.FIRE
                || block == net.minecraft.world.level.block.Blocks.SOUL_FIRE
                || block == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK
                || block == net.minecraft.world.level.block.Blocks.CACTUS
                || block == net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH
                || block == net.minecraft.world.level.block.Blocks.WITHER_ROSE
                || block == net.minecraft.world.level.block.Blocks.NETHER_PORTAL;
    }

    /**
     * Equally distributed offset using the 4-rotation rectangle method (from Essentials).
     */
    private double[] randomOffset(double minRange, double maxRange) {
        double rectX = RANDOM.nextDouble() * (maxRange - minRange) + minRange;
        double rectZ = RANDOM.nextDouble() * (maxRange + minRange) - minRange;

        int transform = RANDOM.nextInt(4);
        double offX, offZ;
        switch (transform) {
            case 0: offX = rectX;  offZ = rectZ;  break;
            case 1: offX = -rectZ; offZ = rectX;  break;
            case 2: offX = -rectX; offZ = -rectZ; break;
            default: offX = rectZ;  offZ = -rectX; break;
        }
        return new double[]{offX, offZ};
    }

    private double clampToWorldBorder(ServerLevel level, double coord, boolean isX) {
        var border = level.getWorldBorder();
        double cx = border.getCenterX();
        double cz = border.getCenterZ();
        double half = border.getSize() / 2.0;
        if (isX) {
            return Math.max(cx - half, Math.min(cx + half, coord));
        } else {
            return Math.max(cz - half, Math.min(cz + half, coord));
        }
    }

    private boolean isValid(TeleportLocation loc, String locationName) {
        ServerLevel level = loc.getLevel();
        if (level == null) return false;
        if (loc.getY() <= level.getMinBuildHeight()) return false;

        // Excluded biomes check
        if (locationName != null) {
            List<String> excluded = getExcludedBiomes(locationName);
            if (!excluded.isEmpty()) {
                BlockPos pos = new BlockPos((int) loc.getX(), (int) loc.getY(), (int) loc.getZ());
                String biomeName = getBiomeName(level, pos);
                if (biomeName != null && excluded.contains(biomeName.toLowerCase())) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getBiomeName(ServerLevel level, BlockPos pos) {
        try {
            var biomeHolder = level.getBiome(pos);
            return biomeHolder.unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Cache
    // -----------------------------------------------------------------------

    private ConcurrentLinkedQueue<TeleportLocation> getCache(ServerLevel level, String name) {
        return locationCache.computeIfAbsent(cacheKey(level.dimension().location().toString(), name),
                k -> new ConcurrentLinkedQueue<>());
    }

    private void prewarmCache(ServerLevel level, String name) {
        String key = cacheKey(level.dimension().location().toString(), name);
        int threshold = getCacheThreshold();
        int current = getCache(level, name).size();
        int pending = pendingCacheFills.getOrDefault(key, 0);
        if (current + pending >= threshold) return;

        int toFill = threshold - current - pending;
        double[] center = getCenter(level, name);
        double minRange = getMinRange(name);
        double maxRange = getMaxRange(level, name);
        pendingCacheFills.merge(key, toFill, Integer::sum);

        for (int i = 0; i < toFill; i++) {
            enqueueSearch(level, center[0], center[1], center[2], minRange, maxRange,
                    name, getFindAttempts(), key);
        }
    }

    public void clearCache() {
        locationCache.clear();
        pendingCacheFills.clear();
        searchQueue.removeIf(request -> request.cacheKey() != null);
    }

    public void clearCache(String name) {
        String suffix = "\u0000" + name;
        locationCache.keySet().removeIf(key -> key.endsWith(suffix));
        pendingCacheFills.keySet().removeIf(key -> key.endsWith(suffix));
        searchQueue.removeIf(request -> request.cacheKey() != null && request.cacheKey().endsWith(suffix));
    }

    // -----------------------------------------------------------------------
    // Config helpers
    // -----------------------------------------------------------------------

    private String resolveDefaultName(ServerLevel level) {
        String def = getConfigString("defaultLocation", "{world}");
        return def.replace("{world}", level.dimension().location().toString());
    }

    private ServerLevel resolveTargetLevel(ServerPlayer player) {
        ServerLevel currentLevel = player.serverLevel();
        List<String> configuredWorlds = getConfiguredWorlds();
        if (configuredWorlds == null) return currentLevel;

        MinecraftServer server = player.getServer();
        if (server == null) return null;

        Set<String> loadedWorlds = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            loadedWorlds.add(level.dimension().location().toString());
        }

        String targetWorld = selectConfiguredWorld(
                currentLevel.dimension().location().toString(), configuredWorlds, loadedWorlds);
        if (targetWorld == null) return null;
        if (targetWorld.equals(currentLevel.dimension().location().toString())) return currentLevel;

        ResourceLocation worldId = ResourceLocation.tryParse(targetWorld);
        return worldId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, worldId));
    }

    static String normalizeWorldId(String value) {
        if (value == null) return null;
        String world = value.trim().toLowerCase(Locale.ROOT);
        if (world.isEmpty()) return null;
        world = switch (world) {
            case "overworld" -> "minecraft:overworld";
            case "nether" -> "minecraft:the_nether";
            case "end" -> "minecraft:the_end";
            default -> world;
        };
        ResourceLocation id = ResourceLocation.tryParse(world);
        return id == null ? null : id.toString();
    }

    static String selectConfiguredWorld(String currentWorld, List<String> configuredWorlds,
                                        Set<String> loadedWorlds) {
        if (configuredWorlds == null) return currentWorld;
        if (loadedWorlds.contains(currentWorld) && configuredWorlds.contains(currentWorld)) {
            return currentWorld;
        }
        for (String world : configuredWorlds) {
            if (loadedWorlds.contains(world)) return world;
        }
        return null;
    }

    static String cacheKey(String worldId, String name) {
        return worldId + "\u0000" + name;
    }

    static List<String> normalizeConfiguredWorlds(JsonArray worlds) {
        if (worlds == null || worlds.isEmpty()) return null;

        List<String> result = new ArrayList<>();
        for (var element : worlds) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
            String normalized = normalizeWorldId(element.getAsString());
            if (normalized != null && !result.contains(normalized)) result.add(normalized);
        }
        return result;
    }

    private List<String> getConfiguredWorlds() {
        JsonObject tpr = getTprConfig();
        if (tpr == null || !tpr.has("world") || !tpr.get("world").isJsonArray()
                || tpr.getAsJsonArray("world").isEmpty()) {
            return null;
        }
        return normalizeConfiguredWorlds(tpr.getAsJsonArray("world"));
    }

    private double[] getCenter(ServerLevel level, String name) {
        JsonObject tpr = getTprConfig();
        if (tpr != null && tpr.has("locations")) {
            JsonObject locs = tpr.getAsJsonObject("locations");
            if (locs.has(name) && locs.getAsJsonObject(name).has("center")) {
                JsonObject c = locs.getAsJsonObject(name).getAsJsonObject("center");
                double x = c.has("x") ? c.get("x").getAsDouble() : 0;
                double y = c.has("y") ? c.get("y").getAsDouble() : 64;
                double z = c.has("z") ? c.get("z").getAsDouble() : 0;
                return new double[]{x, y, z};
            }
        }
        // Fallback: world border center
        var border = level.getWorldBorder();
        return new double[]{border.getCenterX(), level.getSeaLevel(), border.getCenterZ()};
    }

    private double getMinRange(String name) {
        JsonObject tpr = getTprConfig();
        if (tpr != null && tpr.has("locations")) {
            JsonObject locs = tpr.getAsJsonObject("locations");
            if (locs.has(name) && locs.getAsJsonObject(name).has("minRange")) {
                return locs.getAsJsonObject(name).get("minRange").getAsDouble();
            }
        }
        double def = getConfigDouble("defaultMinRange", 0);
        return def;
    }

    private double getMaxRange(ServerLevel level, String name) {
        JsonObject tpr = getTprConfig();
        if (tpr != null && tpr.has("locations")) {
            JsonObject locs = tpr.getAsJsonObject("locations");
            if (locs.has(name) && locs.getAsJsonObject(name).has("maxRange")) {
                return locs.getAsJsonObject(name).get("maxRange").getAsDouble();
            }
        }
        double def = getConfigDouble("defaultMaxRange", -1);
        if (def <= 0) {
            // Use half the world border size
            return level.getWorldBorder().getSize() / 2.0;
        }
        return def;
    }

    private int getFindAttempts() {
        return (int) getConfigDouble("findAttempts", 10);
    }

    private int getCacheThreshold() {
        return (int) getConfigDouble("cacheThreshold", 10);
    }

    private int getTprCooldown() {
        return (int) getConfigDouble("cooldown", 60);
    }

    private int getTeleportDelaySecs() {
        try {
            JsonObject config = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            if (config.has("teleportation")) {
                JsonObject tp = config.getAsJsonObject("teleportation");
                if (tp.has("generalSettings")) {
                    JsonObject gs = tp.getAsJsonObject("generalSettings");
                    if (gs.has("teleportDelay")) return gs.get("teleportDelay").getAsInt();
                }
            }
        } catch (Exception ignored) {}
        return 3;
    }

    private List<String> getExcludedBiomes(String name) {
        List<String> result = new ArrayList<>();
        JsonObject tpr = getTprConfig();
        if (tpr == null) return result;

        // Per-location excluded biomes
        if (tpr.has("locations")) {
            JsonObject locs = tpr.getAsJsonObject("locations");
            if (locs.has(name) && locs.getAsJsonObject(name).has("excludedBiomes")) {
                JsonArray arr = locs.getAsJsonObject(name).getAsJsonArray("excludedBiomes");
                arr.forEach(e -> result.add(e.getAsString().toLowerCase()));
                return result;
            }
        }

        // Global excluded biomes
        if (tpr.has("excludedBiomes")) {
            JsonArray arr = tpr.getAsJsonArray("excludedBiomes");
            arr.forEach(e -> result.add(e.getAsString().toLowerCase()));
        }
        return result;
    }

    private JsonObject getTprConfig() {
        try {
            JsonObject config = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            if (config.has("teleportation")) {
                JsonObject tp = config.getAsJsonObject("teleportation");
                if (tp.has("randomTeleportSettings")) {
                    return tp.getAsJsonObject("randomTeleportSettings");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private double getConfigDouble(String key, double def) {
        JsonObject tpr = getTprConfig();
        if (tpr != null && tpr.has(key)) {
            return tpr.get(key).getAsDouble();
        }
        return def;
    }

    private String getConfigString(String key, String def) {
        JsonObject tpr = getTprConfig();
        if (tpr != null && tpr.has(key)) {
            return tpr.get(key).getAsString();
        }
        return def;
    }

    private boolean isOnlyPreGeneratedChunks() {
        JsonObject tpr = getTprConfig();
        if (tpr != null && tpr.has("onlyPreGeneratedChunks")) {
            return tpr.get("onlyPreGeneratedChunks").getAsBoolean();
        }
        return true;
    }

    private CompletableFuture<Boolean> isChunkGenerated(ServerLevel level, int chunkX, int chunkZ) {
        if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return CompletableFuture.completedFuture(true);
        }
        net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
        return level.getChunkSource().chunkMap.read(chunkPos)
                .thenApply(opt -> opt.isPresent());
    }

    private record SearchRequest(CompletableFuture<TeleportLocation> result,
                                 ServerLevel level,
                                 double cx, double cy, double cz,
                                 double minRange, double maxRange,
                                 String locationName,
                                 int attemptsLeft,
                                 String cacheKey) {
        private SearchRequest withAttemptsLeft(int attempts) {
            return new SearchRequest(result, level, cx, cy, cz, minRange, maxRange,
                    locationName, attempts, cacheKey);
        }
    }
}
