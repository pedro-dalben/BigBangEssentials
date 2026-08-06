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
import java.util.concurrent.TimeUnit;

public final class NpcManager implements NpcService {
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
        this.interactionService = new NpcInteractionService(viewerService, renderService);

        for (NpcDefinition def : config.npcs().values()) {
            registerNpc(def);
        }

        hologramService.cleanupOrphans(npcs);

        initialized = true;
        lastReloadMillis = System.currentTimeMillis() - start;
        LOGGER.info("NPC Manager initialized with {} NPC(s) in {}ms", npcs.size(), lastReloadMillis);
    }

    public synchronized void tick() {
        if (!initialized || shuttingDown) return;
        tickCounter++;
        lookUpdatesThisTick = 0;
        lookUpdatesDropped = 0;

        if (tickCounter % config.visibilityScanIntervalTicks() == 0) {
            syncViewers();
        }
        syncLookAtPlayers();

        if (saveScheduled && tickCounter % 20 == 0) {
            saveScheduled = false;
            save();
        }
    }

    public synchronized void onPlayerJoin(ServerPlayer player) {
        if (!initialized) return;
        viewerService.getSession(player);
        syncViewer(player);
    }

    public synchronized void onPlayerLeave(ServerPlayer player) {
        NpcViewerSession session = viewerService.removeSession(player.getUUID());
        if (session == null) return;
        for (String npcId : session.visibleNpcIds()) {
            NpcDefinition npc = npcs.get(npcId);
            if (npc != null) renderService.despawn(player, npc);
        }
        interactionService.clearCooldowns(player.getUUID());
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
            String skinName = definition.skin().playerName();
            skinCache.resolve(skinName).thenAccept(entry -> {
                NpcDefinition withSkin = npcs.get(id).withSkin(
                    new NpcSkin(skinName, entry.uuid(), entry.textureValue(), entry.textureSignature(), entry.model(), entry.fetchedAt()));
                npcs.put(id, withSkin);
                invalidateViewersForNpc(id);
            });
        }

        if (old.location().equals(definition.location()) && old.enabled() == definition.enabled()) {
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

        LOGGER.info("NPC reload complete: {} added, {} removed, {} updated, {} unchanged, {} invalid in {}ms",
            added.size(), removed.size(), updated, unchanged, 0, lastReloadMillis);
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
            lastReloadMillis, lastSaveMillis
        );
    }

    public synchronized void shutdown() {
        shuttingDown = true;
        if (skinCache != null) skinCache.shutdown();
        viewerService.clear();

        MinecraftServer server = Platform.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onPlayerLeave(player);
            }
        }

        configStore.save(config.withNpcs(new LinkedHashMap<>(npcs)));
        initialized = false;
        LOGGER.info("NPC Manager shutdown complete");
    }

    private void registerNpc(NpcDefinition def) {
        npcs.put(def.id(), def);
        renderService.register(def);
        spatialIndex.add(def.id(), def.location());
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
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

        int radius = (int) Math.ceil(Math.max(config.defaultViewDistance(), 64.0));
        Set<String> candidates = spatialIndex.query(
            player.serverLevel().dimension().location(), player.getX(), player.getZ(), radius);

        Set<String> shouldSee = new LinkedHashSet<>();
        for (String id : candidates) {
            NpcDefinition npc = npcs.get(id);
            if (npc == null || !npc.enabled()) continue;
            if (!player.level().dimension().equals(npc.location().dimension())) continue;

            double distSq = player.distanceToSqr(npc.location().x(), npc.location().y(), npc.location().z());
            double viewDist = npc.viewDistance();

            if (session.visibleNpcIds().contains(id)) {
                if (distSq <= npc.despawnDistance() * npc.despawnDistance()) {
                    shouldSee.add(id);
                    continue;
                }
            } else {
                if (distSq <= viewDist * viewDist) {
                    shouldSee.add(id);
                }
            }
        }

        for (String id : new ArrayList<>(session.visibleNpcIds())) {
            if (!shouldSee.contains(id)) {
                NpcDefinition npc = npcs.get(id);
                if (npc != null) renderService.despawn(player, npc);
            }
        }

        int spawns = 0;
        for (String id : shouldSee) {
            if (session.visibleNpcIds().contains(id)) continue;
            if (spawns >= config.maxSpawnsPerTick()) break;
            NpcDefinition npc = npcs.get(id);
            if (npc != null) {
                renderService.spawn(player, npc);
                spawns++;
            }
        }

        session.setLastSyncTick(tickCounter);
    }

    private void syncLookAtPlayers() {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        int updates = 0;
        for (ServerPlayer player : players) {
            NpcViewerSession session = viewerService.getSession(player.getUUID());
            if (session == null || session.visibleNpcIds().isEmpty()) continue;

            for (String npcId : session.visibleNpcIds()) {
                if (updates >= config.maxLookUpdatesPerTick()) {
                    lookUpdatesDropped++;
                    continue;
                }

                NpcDefinition npc = npcs.get(npcId);
                if (npc == null || !npc.lookSettings().enabled()) continue;
                if (!player.level().dimension().equals(npc.location().dimension())) continue;

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
        lookUpdatesThisTick = updates;
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
