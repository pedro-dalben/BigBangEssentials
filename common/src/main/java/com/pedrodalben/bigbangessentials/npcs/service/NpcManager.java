package com.pedrodalben.bigbangessentials.npcs.service;

import com.pedrodalben.bigbangessentials.npcs.api.*;
import com.pedrodalben.bigbangessentials.npcs.config.NpcConfig;
import com.pedrodalben.bigbangessentials.npcs.config.NpcConfigStore;
import com.pedrodalben.bigbangessentials.npcs.hologram.NpcHologramService;
import com.pedrodalben.bigbangessentials.npcs.interaction.NpcInteractionService;
import com.pedrodalben.bigbangessentials.npcs.render.NpcRenderService;
import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerService;
import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerSession;
import com.pedrodalben.bigbangessentials.npcs.skin.SkinCache;
import com.pedrodalben.bigbangessentials.npcs.spatial.NpcSpatialIndex;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class NpcManager implements NpcService, NpcRenderService.NpcRenderGate {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcManager.class);
    private static final NpcManager INSTANCE = new NpcManager();

    private final NpcConfigStore configStore;
    private final NpcViewerService viewerService;
    private final NpcSpatialIndex spatialIndex;
    private final NpcHologramService hologramService;
    private NpcRenderService renderService;
    private NpcInteractionService interactionService;
    private SkinCache skinCache;

    private NpcConfig config;
    private final Map<String, NpcDefinition> npcs = new LinkedHashMap<>();
    private volatile boolean initialized;

    private int viewerRoundRobinIndex;
    private int lookRoundRobinIndex;
    private long tickCounter;
    private int spawnsThisCycle;
    private int despawnsThisCycle;
    private int lookUpdatesThisTick;
    private int lookUpdatesDropped;
    private long lastReloadMillis;
    private long lastSaveMillis;
    private volatile boolean saveScheduled;
    private volatile boolean shuttingDown;

    private NpcManager() {
        this.configStore = new NpcConfigStore();
        this.viewerService = new NpcViewerService();
        this.spatialIndex = new NpcSpatialIndex();
        this.hologramService = new NpcHologramService();
    }

    public static NpcManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized) return;
        long start = System.currentTimeMillis();

        config = configStore.load();
        configStore.createExample();

        this.skinCache = new SkinCache(
            config.freshTtlHours(), config.staleTtlDays(), config.negativeCacheMinutes(),
            config.maxConcurrentRequests(), config.connectTimeoutMillis(), config.requestTimeoutMillis());
        this.renderService = new NpcRenderService(viewerService, skinCache);
        this.renderService.setGate(this);
        this.interactionService = new NpcInteractionService(viewerService, renderService);

        for (NpcDefinition def : config.npcs().values()) {
            registerNpc(def);
        }

        hologramService.cleanupOrphans(npcs);

        initialized = true;
        lastReloadMillis = System.currentTimeMillis() - start;
        LOGGER.info("NPC Module RUNNING with {} NPC(s) in {}ms", npcs.size(), lastReloadMillis);
    }

    public synchronized void tick() {
        if (!initialized || shuttingDown) return;
        tickCounter++;
        lookUpdatesThisTick = 0;
        lookUpdatesDropped = 0;
        spawnsThisCycle = 0;
        despawnsThisCycle = 0;

        if (tickCounter % config.visibilityScanIntervalTicks() == 0) {
            syncViewers();
        }
        syncLookAtPlayers();

        if (skinCache != null) {
            skinCache.persistIfDirtyDebounced();
        }

        if (saveScheduled && tickCounter % 20 == 0) {
            saveScheduled = false;
            save();
        }
    }

    public synchronized void onPlayerJoin(ServerPlayer player) {
        if (!initialized || shuttingDown) return;
        viewerService.getSession(player);
        syncViewer(player);
    }

    public synchronized void onPlayerLeave(ServerPlayer player) {
        NpcViewerSession session = viewerService.removeSession(player.getUUID());
        if (session == null) return;
        interactionService.clearCooldowns(player.getUUID());
        // The client is gone — no despawn packets are required.
    }

    public synchronized void onPlayerDimensionChange(ServerPlayer player) {
        NpcViewerSession session = viewerService.getSession(player.getUUID());
        if (session == null) return;
        for (String npcId : new ArrayList<>(session.visibleNpcIds())) {
            NpcDefinition npc = npcs.get(npcId);
            if (npc != null) renderService.despawn(player, npc);
        }
        session.clear();
        syncViewer(player);
    }

    public NpcInteractionService getInteractionService() {
        return interactionService;
    }

    /** Per-name skin cache status for /npc info diagnostics. */
    public String skinStatus(String playerName) {
        return skinCache == null ? "UNAVAILABLE" : skinCache.describeCacheStatus(playerName);
    }

    /** Render diagnostics for /npc info, scoped to the requesting viewer. */
    public NpcRenderDiagnostics renderDiagnostics(String id, UUID viewerUuid) {
        NpcViewerSession session = viewerService.getSession(viewerUuid);
        NpcRenderService.NpcRenderState state = renderService != null ? renderService.getState(id) : null;
        int viewers = 0;
        for (NpcViewerSession s : viewerService.allSessions()) {
            if (s.visibleNpcIds().contains(id)) viewers++;
        }
        if (state == null) return new NpcRenderDiagnostics("UNREGISTERED", 0, null, viewers);
        String renderState = "NOT_VISIBLE";
        String lastError = null;
        if (session != null) {
            NpcViewerSession.NpcViewState vs = session.getStateIfPresent(id);
            if (vs != null) {
                renderState = vs.renderState().name();
                lastError = vs.lastError();
            }
        }
        return new NpcRenderDiagnostics(renderState, state.entityId(), lastError, viewers);
    }

    public record NpcRenderDiagnostics(String renderState, int entityId, String lastError, int viewers) {}

    public int lookUpdatesThisTick() {
        return lookUpdatesThisTick;
    }

    // ---- NpcRenderGate ----

    @Override
    public boolean npcModuleActive() {
        return initialized && !shuttingDown;
    }

    @Override
    public synchronized Optional<NpcDefinition> find(String id) {
        return Optional.ofNullable(npcs.get(NpcDefinition.normalizeId(id)));
    }

    @Override
    public synchronized Collection<NpcDefinition> list() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    @Override
    public synchronized NpcDefinition create(NpcDefinition definition) {
        NpcDefinition normalized = new NpcDefinition(
            definition.id(), definition.enabled(), definition.displayName(),
            definition.location(), definition.skin(), definition.action(),
            definition.hologram(), definition.lookSettings(),
            definition.viewDistance(), definition.despawnDistance(),
            definition.interaction());
        registerNpc(normalized);
        hologramService.createOrUpdate(normalized);
        scheduleSave();
        LOGGER.info("NPC '{}' created", normalized.id());
        return normalized;
    }

    @Override
    public synchronized NpcDefinition update(NpcDefinition definition) {
        String id = NpcDefinition.normalizeId(definition.id());
        if (!npcs.containsKey(id)) throw new IllegalArgumentException("NPC does not exist: " + id);
        NpcDefinition old = npcs.get(id);
        registerNpc(definition);
        hologramService.createOrUpdate(definition);

        if (!old.skin().playerName().equals(definition.skin().playerName())) {
            refreshSkinResolution(id, definition.skin().playerName());
        }

        if (old.location().equals(definition.location()) && old.enabled() == definition.enabled()
            && old.skin().playerName().equals(definition.skin().playerName())) {
            scheduleSave();
            return definition;
        }

        invalidateViewersForNpc(id);
        scheduleSave();
        LOGGER.info("NPC '{}' updated", id);
        return definition;
    }

    @Override
    public synchronized boolean delete(String id) {
        String normalized = NpcDefinition.normalizeId(id);
        NpcDefinition removed = npcs.remove(normalized);
        if (removed == null) return false;

        renderService.unregister(normalized);
        spatialIndex.remove(normalized);
        hologramService.remove(normalized);

        MinecraftServer server = Platform.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                NpcViewerSession session = viewerService.getSession(player.getUUID());
                if (session != null && session.visibleNpcIds().remove(normalized)) {
                    renderService.despawn(player, removed);
                }
            }
        }
        scheduleSave();
        LOGGER.info("NPC '{}' deleted", normalized);
        return true;
    }

    @Override
    public synchronized void reload() {
        long start = System.currentTimeMillis();
        NpcConfig newConfig = configStore.load();

        skinCache.configure(newConfig.freshTtlHours(), newConfig.staleTtlDays(), newConfig.negativeCacheMinutes(),
            newConfig.maxConcurrentRequests(), newConfig.connectTimeoutMillis(), newConfig.requestTimeoutMillis());

        this.config = newConfig;

        Map<String, NpcDefinition> newNpcs = newConfig.npcs();
        Set<String> added = new HashSet<>(newNpcs.keySet());
        added.removeAll(npcs.keySet());
        Set<String> removed = new HashSet<>(npcs.keySet());
        removed.removeAll(newNpcs.keySet());
        int updated = 0;
        int unchanged = 0;

        for (String id : removed) {
            NpcDefinition def = npcs.remove(id);
            renderService.unregister(id);
            spatialIndex.remove(id);
            hologramService.remove(id);
            MinecraftServer server = Platform.getCurrentServer();
            if (server != null && def != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    NpcViewerSession session = viewerService.getSession(player.getUUID());
                    if (session != null && session.visibleNpcIds().remove(id)) {
                        renderService.despawn(player, def);
                    }
                }
            }
        }

        for (var entry : newNpcs.entrySet()) {
            if (!npcs.containsKey(entry.getKey())) {
                registerNpc(entry.getValue());
                hologramService.createOrUpdate(entry.getValue());
                continue;
            }
            NpcDefinition old = npcs.get(entry.getKey());
            if (!entry.getValue().equals(old)) {
                registerNpc(entry.getValue());
                hologramService.createOrUpdate(entry.getValue());
                if (skinChanged(old, entry.getValue()) || locChanged(old, entry.getValue())) {
                    invalidateViewersForNpc(entry.getKey());
                }
                updated++;
            } else {
                unchanged++;
            }
        }

        hologramService.cleanupOrphans(npcs);
        lastReloadMillis = System.currentTimeMillis() - start;

        LOGGER.info("NPC reload complete: {} added, {} removed, {} updated, {} unchanged in {}ms",
            added.size(), removed.size(), updated, unchanged, lastReloadMillis);
    }

    @Override
    public synchronized void save() {
        long start = System.currentTimeMillis();
        configStore.save(config.withNpcs(new LinkedHashMap<>(npcs)));
        skinCache.persist();
        lastSaveMillis = System.currentTimeMillis() - start;
        saveScheduled = false;
    }

    @Override
    public synchronized NpcStats stats() {
        int visibleTotal = 0;
        MinecraftServer server = Platform.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                visibleTotal += renderService.visibleNpcCount(player);
            }
        }

        int enabled = 0;
        int invalid = 0;
        for (NpcDefinition def : npcs.values()) {
            if (def.enabled()) enabled++;
            else invalid++;
        }

        return new NpcStats(
            npcs.size(), enabled, invalid,
            viewerService.sessionCount(), visibleTotal,
            spatialIndex.size(), 0, 0, 0,
            lookUpdatesThisTick, lookUpdatesDropped,
            skinCache != null ? skinCache.memorySize() : 0,
            skinCache != null ? skinCache.hits() : 0,
            skinCache != null ? skinCache.misses() : 0,
            skinCache != null ? skinCache.staleHits() : 0,
            skinCache != null ? skinCache.negativeHits() : 0,
            skinCache != null ? skinCache.inflightCount() : 0,
            skinCache != null ? skinCache.failures() : 0,
            hologramService.countActive(),
            renderService.pendingSpawnCount(),
            renderService.failedSpawns(),
            renderService.packetFailures(),
            renderService.reskinsApplied(),
            lastReloadMillis, lastSaveMillis
        );
    }

    public synchronized void shutdown() {
        if (!initialized) return;
        shuttingDown = true;
        renderService.shutdown();

        MinecraftServer server = Platform.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onPlayerLeave(player);
            }
        } else {
            viewerService.clear();
        }

        skinCache.persist();
        configStore.save(config.withNpcs(new LinkedHashMap<>(npcs)));
        skinCache.shutdown();
        initialized = false;
        LOGGER.info("NPC Manager shutdown complete");
    }

    // ---- internal ----

    private void registerNpc(NpcDefinition def) {
        npcs.put(def.id(), def);
        renderService.register(def);
        spatialIndex.add(def.id(), def.location());
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
    }

    /**
     * Resolves a skin in the background and, on the server thread, stores the
     * resolved state on the definition and refreshes viewers — but only if the
     * NPC still exists and its skin name did not change again meanwhile.
     */
    private void refreshSkinResolution(String npcId, String skinName) {
        if (skinCache == null) return;
        skinCache.resolve(skinName).thenAccept(entry -> {
            MinecraftServer server = Platform.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                synchronized (NpcManager.this) {
                    if (!initialized || shuttingDown) return;
                    NpcDefinition current = npcs.get(npcId);
                    if (current == null || !current.skin().playerName().equals(skinName)) return;
                    NpcDefinition withSkin = current.withSkin(new NpcSkin(skinName, entry.uuid(),
                        entry.textureValue(), entry.textureSignature(), entry.model(), entry.fetchedAt()));
                    npcs.put(npcId, withSkin);
                    invalidateViewersForNpc(npcId);
                }
            });
        }).exceptionally(e -> {
            LOGGER.debug("Skin resolution callback failed for '{}': {}", skinName, e.getMessage());
            return null;
        });
    }

    private void syncViewers() {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        int budget = Math.min(players.size(), config.maxViewerSyncsPerTick());
        for (int i = 0; i < budget; i++) {
            ServerPlayer player = players.get((viewerRoundRobinIndex + i) % players.size());
            syncViewer(player);
        }
        viewerRoundRobinIndex = (viewerRoundRobinIndex + budget) % players.size();
    }

    private void syncViewer(ServerPlayer player) {
        NpcViewerSession session = viewerService.getSession(player);
        if (session == null) return;

        int radius = (int) Math.ceil(scanRadius());
        Set<String> candidates = spatialIndex.query(
            player.serverLevel().dimension().location(), player.getX(), player.getZ(), radius);

        Set<String> shouldSee = new LinkedHashSet<>();
        for (String id : candidates) {
            NpcDefinition npc = npcs.get(id);
            if (npc == null || !npc.enabled()) continue;
            if (!player.level().dimension().location().equals(npc.location().dimension())) continue;

            double distSq = player.distanceToSqr(npc.location().x(), npc.location().y(), npc.location().z());
            double viewDist = npc.viewDistance();

            if (session.visibleNpcIds().contains(id)) {
                if (distSq <= npc.despawnDistance() * npc.despawnDistance()) {
                    shouldSee.add(id);
                }
            } else if (distSq <= viewDist * viewDist) {
                shouldSee.add(id);
            }
        }

        for (String id : new ArrayList<>(session.visibleNpcIds())) {
            if (!shouldSee.contains(id)) {
                if (despawnsThisCycle >= config.maxDespawnsPerTick()) break;
                NpcDefinition npc = npcs.get(id);
                if (npc != null) {
                    renderService.despawn(player, npc);
                    despawnsThisCycle++;
                }
            }
        }

        for (String id : shouldSee) {
            if (session.visibleNpcIds().contains(id)) {
                // Already visible: if it spawned with the fallback skin, try to
                // apply the real skin now that it may be available.
                NpcDefinition npc = npcs.get(id);
                if (npc != null) {
                    renderService.tryReskin(player, npc, session.getState(id));
                }
                continue;
            }
            if (spawnsThisCycle >= config.maxSpawnsPerTick()) break;
            NpcDefinition npc = npcs.get(id);
            if (npc != null) {
                renderService.spawn(player, npc);
                spawnsThisCycle++;
            }
        }

        session.setLastSyncTick(tickCounter);
    }

    /** Query radius covering every NPC's individual view distance (bounded). */
    private double scanRadius() {
        double max = config.defaultViewDistance();
        for (NpcDefinition npc : npcs.values()) {
            if (npc.enabled() && npc.viewDistance() > max) max = npc.viewDistance();
        }
        return Math.max(32.0, Math.min(max, 192.0));
    }

    private void syncLookAtPlayers() {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        int budget = config.maxLookUpdatesPerTick();
        int updates = 0;
        int dropped = 0;
        int start = players.size() == 0 ? 0 : (int) (lookRoundRobinIndex % players.size());

        outer:
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get((start + i) % players.size());
            NpcViewerSession session = viewerService.getSession(player.getUUID());
            if (session == null || session.visibleNpcIds().isEmpty()) continue;

            for (String npcId : session.visibleNpcIds()) {
                if (updates >= budget) break outer;

                NpcDefinition npc = npcs.get(npcId);
                if (npc == null || !npc.lookSettings().enabled()) continue;
                if (!player.level().dimension().location().equals(npc.location().dimension())) continue;

                NpcLookSettings look = npc.lookSettings();
                if (tickCounter % look.updateIntervalTicks() != 0) continue;

                NpcViewerSession.NpcViewState vs = session.getState(npcId);
                double distSq = player.distanceToSqr(npc.location().x(), npc.location().y(), npc.location().z());
                double lookRangeSq = look.range() * look.range();

                if (distSq > lookRangeSq) {
                    if (look.resetWhenOutOfRange()) {
                        renderService.resetLook(player, npc, vs);
                        updates++;
                    }
                    continue;
                }

                double eyeY = player.getY() + player.getEyeHeight();
                double dx = player.getX() - npc.location().x();
                double dy = eyeY - (npc.location().y() + 1.62);
                double dz = player.getZ() - npc.location().z();
                double hDist = Math.sqrt(dx * dx + dz * dz);

                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));

                float baseYaw = npc.location().yaw();
                float deltaYaw = targetYaw - baseYaw;
                while (deltaYaw > 180.0f) deltaYaw -= 360.0f;
                while (deltaYaw < -180.0f) deltaYaw += 360.0f;
                deltaYaw = Math.max(-(float) look.maxYawFromBase(), Math.min((float) look.maxYawFromBase(), deltaYaw));
                float clampedYaw = baseYaw + deltaYaw;

                float clampedPitch = Math.max(-(float) look.maxPitchDown(), Math.min((float) look.maxPitchUp(), targetPitch));

                if (Math.abs(clampedYaw - vs.lastHeadYaw()) < look.minimumAngleChange()
                    && Math.abs(clampedPitch - vs.lastPitch()) < look.minimumAngleChange()) {
                    continue;
                }

                float bodyYaw = look.rotateBody() ? clampedYaw : vs.lastYaw();
                renderService.sendLookUpdate(player, npc, bodyYaw, clampedYaw, clampedPitch, vs);
                updates++;
            }
        }
        lookRoundRobinIndex = (lookRoundRobinIndex + 1) % Math.max(1, players.size());
        lookUpdatesThisTick = updates;
        lookUpdatesDropped = dropped;
    }

    private void invalidateViewersForNpc(String npcId) {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NpcViewerSession session = viewerService.getSession(player.getUUID());
            if (session == null) continue;
            NpcDefinition npc = npcs.get(npcId);
            if (session.visibleNpcIds().remove(npcId) && npc != null) {
                renderService.despawn(player, npc);
            }
        }
    }

    private static boolean skinChanged(NpcDefinition old, NpcDefinition new_) {
        return !old.skin().playerName().equals(new_.skin().playerName());
    }

    private static boolean locChanged(NpcDefinition old, NpcDefinition new_) {
        return !old.location().equals(new_.location());
    }
}
