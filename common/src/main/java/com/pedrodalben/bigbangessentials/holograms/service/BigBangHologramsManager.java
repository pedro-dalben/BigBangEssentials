package com.pedrodalben.bigbangessentials.holograms.service;

import com.pedrodalben.bigbangessentials.holograms.api.*;
import com.pedrodalben.bigbangessentials.holograms.config.HologramConfig;
import com.pedrodalben.bigbangessentials.holograms.config.HologramConfigStore;
import com.pedrodalben.bigbangessentials.holograms.migration.LegacyCrateHologramCleaner;
import com.pedrodalben.bigbangessentials.holograms.placeholder.PlaceholderEngine;
import com.pedrodalben.bigbangessentials.holograms.render.ClientOnlyTextDisplayRenderer;
import com.pedrodalben.bigbangessentials.holograms.render.HologramRenderer;
import com.pedrodalben.bigbangessentials.holograms.render.RenderSnapshot;
import com.pedrodalben.bigbangessentials.holograms.storage.HologramRepository;
import com.pedrodalben.bigbangessentials.holograms.storage.JsonHologramRepository;
import com.pedrodalben.bigbangessentials.holograms.visibility.ChunkSpatialIndex;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

public final class BigBangHologramsManager implements HologramService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BigBangHologramsManager.class);
    private static final byte TEXT_FLAG_SHADOW = 0x01;
    private static final byte TEXT_FLAG_SEE_THROUGH = 0x02;
    private static final BigBangHologramsManager INSTANCE = new BigBangHologramsManager();

    private final Map<String, ManagedHologram> holograms = new LinkedHashMap<>();
    private final Map<UUID, ViewerSession> viewerSessions = new HashMap<>();
    private final ChunkSpatialIndex spatialIndex = new ChunkSpatialIndex();
    private final HologramConfigStore configStore = new HologramConfigStore();
    private final HologramRepository repository = new JsonHologramRepository();
    private final PlaceholderEngine placeholderEngine = new PlaceholderEngine();
    private final LegacyCrateHologramCleaner legacyCleaner = new LegacyCrateHologramCleaner();
    private final HologramRenderer renderer = new ClientOnlyTextDisplayRenderer();
    private final List<HologramLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final PriorityQueue<ScheduledContentUpdate> scheduledUpdates = new PriorityQueue<>(Comparator.comparingLong(ScheduledContentUpdate::tick));
    private final AtomicInteger nextEntityId = new AtomicInteger(1_500_000_000);

    private HologramConfig config = HologramConfig.defaults();
    private long tickCounter;
    private int viewerRoundRobinIndex;
    private long spawnPackets;
    private long updatePackets;
    private long destroyPackets;
    private long totalUpdateNanos;
    private long totalUpdates;
    private boolean initialized;

    private BigBangHologramsManager() {
        registerPlaceholderResolver(new PlayerPlaceholderResolver());
    }

    public static BigBangHologramsManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        this.config = configStore.load();
        this.initialized = true;

        if (config.persistenceEnabled()) {
            for (HologramDefinition definition : repository.loadAll()) {
                upsert(definition, false);
            }
        }

        if (config.cleanupEnabled() && config.cleanupOnServerStart()) {
            legacyCleaner.cleanupLoadedLevels();
        }

        LOGGER.info("BigBangHolograms initialized with {} persistent hologram(s)", holograms.size());
    }

    public synchronized void tick() {
        if (!initialized || !config.enabled()) {
            return;
        }
        tickCounter++;
        syncViewers();
        processScheduledUpdates();
    }

    public synchronized void onPlayerDisconnect(ServerPlayer player) {
        ViewerSession session = viewerSessions.remove(player.getUUID());
        if (session == null) {
            return;
        }
        for (String hologramId : session.visibleIds) {
            ManagedHologram hologram = holograms.get(hologramId);
            if (hologram != null) {
                renderer.hide(player, hologram.entityId);
                destroyPackets++;
                lifecycleListeners.forEach(listener -> listener.onHidden(hologram.definition, player));
            }
        }
    }

    public synchronized void onPlayerStateInvalidated(ServerPlayer player) {
        ViewerSession session = viewerSessions.computeIfAbsent(player.getUUID(), ignored -> new ViewerSession());
        for (String hologramId : new ArrayList<>(session.visibleIds)) {
            ManagedHologram hologram = holograms.get(hologramId);
            if (hologram != null) {
                renderer.hide(player, hologram.entityId);
                destroyPackets++;
                lifecycleListeners.forEach(listener -> listener.onHidden(hologram.definition, player));
            }
        }
        session.visibleIds.clear();
    }

    public synchronized void syncPlayerNow(ServerPlayer player) {
        ensureInitialized();
        syncViewer(player);
    }

    public LegacyCrateHologramCleaner getLegacyCleaner() {
        return legacyCleaner;
    }

    @Override
    public synchronized Optional<HologramHandle> find(String id) {
        String normalized = HologramDefinition.normalizeId(id);
        return holograms.containsKey(normalized)
            ? Optional.of(new HologramHandle(this, normalized))
            : Optional.empty();
    }

    @Override
    public synchronized Optional<HologramDefinition> findDefinition(String id) {
        String normalized = HologramDefinition.normalizeId(id);
        ManagedHologram hologram = holograms.get(normalized);
        return hologram == null ? Optional.empty() : Optional.of(hologram.definition);
    }

    @Override
    public synchronized boolean exists(String id) {
        return holograms.containsKey(HologramDefinition.normalizeId(id));
    }

    @Override
    public synchronized HologramHandle create(HologramDefinition definition) {
        String id = HologramDefinition.normalizeId(definition.id());
        if (holograms.containsKey(id)) {
            throw new IllegalArgumentException("Hologram already exists: " + id);
        }
        return createOrUpdate(definition);
    }

    @Override
    public synchronized HologramHandle createOrUpdate(HologramDefinition definition) {
        ensureInitialized();
        ensureMainThread();
        return new HologramHandle(this, upsert(definition, true).definition.id());
    }

    @Override
    public synchronized Optional<HologramHandle> update(String id, UnaryOperator<HologramDefinitionBuilder> mutator) {
        String normalized = HologramDefinition.normalizeId(id);
        ManagedHologram existing = holograms.get(normalized);
        if (existing == null) {
            return Optional.empty();
        }
        HologramDefinition mutated = mutator.apply(existing.definition.toBuilder()).build();
        return Optional.of(createOrUpdate(mutated));
    }

    @Override
    public synchronized boolean delete(String id) {
        ensureInitialized();
        ensureMainThread();
        String normalized = HologramDefinition.normalizeId(id);
        ManagedHologram removed = holograms.remove(normalized);
        if (removed == null) {
            return false;
        }
        spatialIndex.remove(normalized);
        for (ServerPlayer player : onlinePlayers()) {
            ViewerSession session = viewerSessions.computeIfAbsent(player.getUUID(), ignored -> new ViewerSession());
            if (session.visibleIds.remove(normalized)) {
                renderer.hide(player, removed.entityId);
                destroyPackets++;
                lifecycleListeners.forEach(listener -> listener.onHidden(removed.definition, player));
            }
            session.forcedShown.remove(normalized);
            session.forcedHidden.remove(normalized);
        }
        persistIfNeeded();
        lifecycleListeners.forEach(listener -> listener.onDeleted(normalized));
        LOGGER.debug("Deleted hologram {}", normalized);
        return true;
    }

    @Override
    public synchronized int deleteByOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return 0;
        }
        List<String> ids = new ArrayList<>();
        for (ManagedHologram hologram : holograms.values()) {
            if (ownerId.equalsIgnoreCase(hologram.definition.ownerId())) {
                ids.add(hologram.definition.id());
            }
        }
        for (String id : ids) {
            delete(id);
        }
        return ids.size();
    }

    @Override
    public synchronized void showTo(ServerPlayer player, String id) {
        String normalized = HologramDefinition.normalizeId(id);
        ManagedHologram hologram = holograms.get(normalized);
        if (hologram == null) {
            throw new IllegalArgumentException("Unknown hologram: " + normalized);
        }
        ViewerSession session = viewerSessions.computeIfAbsent(player.getUUID(), ignored -> new ViewerSession());
        session.forcedHidden.remove(normalized);
        session.forcedShown.add(normalized);
        syncViewer(player);
    }

    @Override
    public synchronized void hideFrom(ServerPlayer player, String id) {
        String normalized = HologramDefinition.normalizeId(id);
        ViewerSession session = viewerSessions.computeIfAbsent(player.getUUID(), ignored -> new ViewerSession());
        session.forcedShown.remove(normalized);
        session.forcedHidden.add(normalized);
        ManagedHologram hologram = holograms.get(normalized);
        if (hologram != null && session.visibleIds.remove(normalized)) {
            renderer.hide(player, hologram.entityId);
            destroyPackets++;
            lifecycleListeners.forEach(listener -> listener.onHidden(hologram.definition, player));
        }
    }

    @Override
    public synchronized void reload() {
        ensureInitialized();
        ensureMainThread();
        this.config = configStore.load();
        List<HologramDefinition> persistent = config.persistenceEnabled() ? repository.loadAll() : List.of();
        Set<String> desiredPersistentIds = new HashSet<>();
        for (HologramDefinition definition : persistent) {
            desiredPersistentIds.add(definition.id());
            upsert(definition, false);
        }
        List<String> toDelete = new ArrayList<>();
        for (ManagedHologram hologram : holograms.values()) {
            if (hologram.definition.persistent() && !desiredPersistentIds.contains(hologram.definition.id())) {
                toDelete.add(hologram.definition.id());
            }
        }
        for (String id : toDelete) {
            delete(id);
        }
        if (config.cleanupEnabled() && config.cleanupOnServerStart()) {
            legacyCleaner.cleanupLoadedLevels();
        }
    }

    @Override
    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }
        for (ServerPlayer player : onlinePlayers()) {
            onPlayerDisconnect(player);
        }
        viewerSessions.clear();
        holograms.clear();
        spatialIndex.clear();
        scheduledUpdates.clear();
        initialized = false;
    }

    @Override
    public synchronized Collection<HologramDefinition> getDefinitions() {
        List<HologramDefinition> definitions = new ArrayList<>();
        for (ManagedHologram hologram : holograms.values()) {
            definitions.add(hologram.definition);
        }
        return definitions;
    }

    @Override
    public synchronized HologramStats getStats() {
        int persistent = 0;
        int crates = 0;
        int viewerEntries = 0;
        for (ManagedHologram hologram : holograms.values()) {
            if (hologram.definition.persistent()) {
                persistent++;
            }
            if (hologram.definition.id().startsWith("bigbangessentials:crate/")) {
                crates++;
            }
        }
        for (ViewerSession session : viewerSessions.values()) {
            viewerEntries += session.visibleIds.size();
        }
        double avgVisible = viewerSessions.isEmpty() ? 0.0D : (double) viewerEntries / (double) viewerSessions.size();
        Instant lastCleanup = legacyCleaner.getLastCleanup();
        return new HologramStats(
            holograms.size(),
            persistent,
            crates,
            viewerSessions.size(),
            viewerEntries,
            avgVisible,
            scheduledUpdates.size(),
            spawnPackets,
            updatePackets,
            destroyPackets,
            totalUpdates == 0L ? 0.0D : (double) totalUpdateNanos / (double) totalUpdates,
            legacyCleaner.getRemovedThisSession(),
            lastCleanup
        );
    }

    @Override
    public void registerPlaceholderResolver(HologramPlaceholderResolver resolver) {
        placeholderEngine.register(resolver);
    }

    @Override
    public void registerLifecycleListener(HologramLifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    private ManagedHologram upsert(HologramDefinition requested, boolean persist) {
        HologramDefinition sanitized = sanitize(requested);
        ManagedHologram existing = holograms.get(sanitized.id());
        PlaceholderEngine.PlaceholderSummary placeholderSummary = placeholderEngine.inspect(sanitized);
        ManagedHologram hologram = new ManagedHologram(
            existing == null ? nextEntityId.getAndIncrement() : existing.entityId,
            existing == null ? UUID.nameUUIDFromBytes(("bigbang-hologram:" + sanitized.id()).getBytes(StandardCharsets.UTF_8)) : existing.entityUuid,
            sanitized,
            placeholderSummary.hasPlaceholders(),
            placeholderSummary.playerScoped()
        );
        holograms.put(sanitized.id(), hologram);
        spatialIndex.add(sanitized.id(), sanitized.location());
        scheduleIfDynamic(hologram);
        if (persist) {
            persistIfNeeded();
        }
        for (ServerPlayer player : onlinePlayers()) {
            syncViewer(player);
        }
        if (existing == null) {
            lifecycleListeners.forEach(listener -> listener.onCreated(sanitized));
            LOGGER.debug("Created hologram {}", sanitized.id());
        } else {
            lifecycleListeners.forEach(listener -> listener.onUpdated(sanitized));
            LOGGER.debug("Updated hologram {}", sanitized.id());
        }
        return hologram;
    }

    private void scheduleIfDynamic(ManagedHologram hologram) {
        if (!isDynamic(hologram)) {
            hologram.nextUpdateTick = Long.MAX_VALUE;
            return;
        }
        long nextTick = tickCounter + Math.max(1, Math.max(config.dynamicUpdateMinIntervalTicks(), hologram.definition.refreshIntervalTicks()));
        if (hologram.definition.pageSwitchIntervalTicks() > 0) {
            nextTick = tickCounter + Math.max(1, hologram.definition.pageSwitchIntervalTicks());
        }
        hologram.nextUpdateTick = nextTick;
        scheduledUpdates.add(new ScheduledContentUpdate(hologram.definition.id(), nextTick));
    }

    private boolean isDynamic(ManagedHologram hologram) {
        return hologram.definition.updatePolicy() == HologramUpdatePolicy.DYNAMIC
            || hologram.definition.pageSwitchIntervalTicks() > 0
            || hologram.hasAnyPlaceholders;
    }

    private void syncViewers() {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null || tickCounter % Math.max(1, config.viewerSyncIntervalTicks()) != 0) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        int budget = Math.min(players.size(), Math.max(1, config.maxViewerSyncsPerTick()));
        for (int i = 0; i < budget; i++) {
            ServerPlayer player = players.get((viewerRoundRobinIndex + i) % players.size());
            syncViewer(player);
        }
        viewerRoundRobinIndex = (viewerRoundRobinIndex + budget) % players.size();
    }

    private void syncViewer(ServerPlayer player) {
        ViewerSession session = viewerSessions.computeIfAbsent(player.getUUID(), ignored -> new ViewerSession());
        Set<String> candidates = spatialIndex.query(
            player.serverLevel().dimension().location(),
            player.getX(),
            player.getZ(),
            config.maxViewDistance()
        );
        for (ManagedHologram hologram : holograms.values()) {
            if (hologram.definition.visibilityPolicy() == HologramVisibilityPolicy.GLOBAL
                && hologram.definition.location().dimension().equals(player.serverLevel().dimension())) {
                candidates.add(hologram.definition.id());
            }
        }

        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(id -> distanceSq(holograms.get(id), player)));

        Set<String> shouldSee = new LinkedHashSet<>();
        for (String id : sorted) {
            ManagedHologram hologram = holograms.get(id);
            if (hologram != null && shouldRenderTo(player, session, hologram)) {
                shouldSee.add(id);
                if (shouldSee.size() >= config.maxHologramsPerPlayer()) {
                    break;
                }
            }
        }

        for (String forced : session.forcedShown) {
            ManagedHologram hologram = holograms.get(forced);
            if (hologram != null && !session.forcedHidden.contains(forced)) {
                shouldSee.add(forced);
            }
        }

        for (String id : new ArrayList<>(session.visibleIds)) {
            if (!shouldSee.contains(id)) {
                ManagedHologram hologram = holograms.get(id);
                if (hologram != null) {
                    renderer.hide(player, hologram.entityId);
                    destroyPackets++;
                    lifecycleListeners.forEach(listener -> listener.onHidden(hologram.definition, player));
                }
                session.visibleIds.remove(id);
            }
        }

        for (String id : shouldSee) {
            ManagedHologram hologram = holograms.get(id);
            if (hologram == null || session.visibleIds.contains(id)) {
                continue;
            }
            RenderSnapshot snapshot = buildSnapshot(hologram, player);
            renderer.show(player, snapshot);
            spawnPackets++;
            session.visibleIds.add(id);
            lifecycleListeners.forEach(listener -> listener.onShown(hologram.definition, player));
        }
    }

    private boolean shouldRenderTo(ServerPlayer player, ViewerSession session, ManagedHologram hologram) {
        if (session.forcedHidden.contains(hologram.definition.id())) {
            return false;
        }
        if (!player.serverLevel().dimension().equals(hologram.definition.location().dimension())) {
            return false;
        }
        if (hologram.definition.hideInSpectator() && player.isSpectator()) {
            return false;
        }
        if (!hologram.definition.requiredPermission().isBlank() && !player.hasPermissions(2)) {
            return false;
        }
        return switch (hologram.definition.visibilityPolicy()) {
            case GLOBAL -> true;
            case MANUAL -> session.forcedShown.contains(hologram.definition.id());
            case NEARBY_PLAYERS -> distanceSq(hologram, player) <= (double) hologram.definition.viewDistance() * hologram.definition.viewDistance();
        };
    }

    private void processScheduledUpdates() {
        int budget = Math.max(1, config.maxContentUpdatesPerTick());
        int processed = 0;
        while (!scheduledUpdates.isEmpty() && processed < budget) {
            ScheduledContentUpdate scheduled = scheduledUpdates.peek();
            if (scheduled.tick() > tickCounter) {
                break;
            }
            scheduledUpdates.poll();
            ManagedHologram hologram = holograms.get(scheduled.hologramId());
            if (hologram == null || hologram.nextUpdateTick != scheduled.tick()) {
                continue;
            }

            long started = System.nanoTime();
            if (hologram.definition.pageSwitchIntervalTicks() > 0 && hologram.definition.pages().size() > 1) {
                hologram.activePage = (hologram.activePage + 1) % hologram.definition.pages().size();
                hologram.globalCache = null;
                hologram.viewerCache.clear();
            } else if (hologram.hasAnyPlaceholders) {
                hologram.globalCache = null;
                hologram.viewerCache.clear();
            }

            for (ServerPlayer player : onlinePlayers()) {
                ViewerSession session = viewerSessions.get(player.getUUID());
                if (session == null || !session.visibleIds.contains(hologram.definition.id())) {
                    continue;
                }
                renderer.update(player, buildSnapshot(hologram, player));
                updatePackets++;
            }

            processed++;
            totalUpdateNanos += System.nanoTime() - started;
            totalUpdates++;
            scheduleIfDynamic(hologram);
        }
    }

    private RenderSnapshot buildSnapshot(ManagedHologram hologram, ServerPlayer player) {
        Component text = resolveText(hologram, player);
        return new RenderSnapshot(
            hologram.entityId,
            hologram.entityUuid,
            hologram.definition.location(),
            hologram.definition.offsetX(),
            hologram.definition.offsetY(),
            hologram.definition.offsetZ(),
            text,
            hologram.definition.lineWidth(),
            hologram.definition.textOpacity(),
            hologram.definition.backgroundColor(),
            textFlags(hologram.definition),
            hologram.definition.viewDistance(),
            hologram.definition.billboard(),
            hologram.definition.scale()
        );
    }

    private Component resolveText(ManagedHologram hologram, ServerPlayer player) {
        int page = Math.min(Math.max(hologram.activePage, 0), hologram.definition.pages().size() - 1);
        if (hologram.playerScopedPlaceholders) {
            CachedComponent cached = hologram.viewerCache.get(player.getUUID());
            if (cached != null && cached.page == page && cached.expiresAtTick >= tickCounter) {
                return cached.component;
            }
            PlaceholderEngine.ResolvedContent resolved = placeholderEngine.resolve(hologram.definition, page, player);
            hologram.viewerCache.put(player.getUUID(), new CachedComponent(resolved.component(), tickCounter + Math.max(1, config.dynamicUpdateMinIntervalTicks()), page));
            return resolved.component();
        }

        if (hologram.globalCache != null && hologram.globalCache.page == page && hologram.globalCache.expiresAtTick >= tickCounter) {
            return hologram.globalCache.component;
        }
        PlaceholderEngine.ResolvedContent resolved = placeholderEngine.resolve(hologram.definition, page, player);
        hologram.globalCache = new CachedComponent(resolved.component(), tickCounter + Math.max(1, config.dynamicUpdateMinIntervalTicks()), page);
        return resolved.component();
    }

    private byte textFlags(HologramDefinition definition) {
        byte flags = 0;
        if (definition.shadow()) {
            flags |= TEXT_FLAG_SHADOW;
        }
        if (definition.seeThrough()) {
            flags |= TEXT_FLAG_SEE_THROUGH;
        }
        return flags;
    }

    private HologramDefinition sanitize(HologramDefinition definition) {
        List<HologramPage> sanitizedPages = new ArrayList<>();
        for (HologramPage page : definition.pages()) {
            if (page.lines().size() > config.maxLinesPerHologram()) {
                throw new IllegalArgumentException("Hologram exceeds max lines: " + definition.id());
            }
            for (HologramLine line : page.lines()) {
                if (!line.isComponent() && line.text() != null && line.text().length() > config.maxCharactersPerLine()) {
                    throw new IllegalArgumentException("Hologram line exceeds max characters: " + definition.id());
                }
            }
            sanitizedPages.add(page);
        }

        HologramDefinitionBuilder builder = definition.toBuilder()
            .pages(sanitizedPages)
            .viewDistance(Math.min(definition.viewDistance(), config.maxViewDistance()))
            .refreshIntervalTicks(definition.refreshIntervalTicks() <= 0
                ? 0
                : Math.max(definition.refreshIntervalTicks(), config.dynamicUpdateMinIntervalTicks()))
            .shadow(definition.shadow())
            .seeThrough(definition.seeThrough())
            .billboard(definition.billboard() == null ? config.billboard() : definition.billboard());
        return builder.build();
    }

    private void persistIfNeeded() {
        if (!config.persistenceEnabled()) {
            return;
        }
        List<HologramDefinition> persistent = new ArrayList<>();
        for (ManagedHologram hologram : holograms.values()) {
            if (hologram.definition.persistent()) {
                persistent.add(hologram.definition);
            }
        }
        repository.saveAll(persistent);
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private void ensureMainThread() {
        MinecraftServer server = Platform.getCurrentServer();
        if (server != null && !server.isSameThread()) {
            throw new IllegalStateException("Hologram mutations must run on the server thread");
        }
    }

    private List<ServerPlayer> onlinePlayers() {
        MinecraftServer server = Platform.getCurrentServer();
        return server == null ? List.of() : server.getPlayerList().getPlayers();
    }

    private static double distanceSq(ManagedHologram hologram, ServerPlayer player) {
        return player.distanceToSqr(
            hologram.definition.location().x() + hologram.definition.offsetX(),
            hologram.definition.location().y() + hologram.definition.offsetY(),
            hologram.definition.location().z() + hologram.definition.offsetZ()
        );
    }

    private static final class ManagedHologram {
        private final int entityId;
        private final UUID entityUuid;
        private final HologramDefinition definition;
        private final boolean hasAnyPlaceholders;
        private final boolean playerScopedPlaceholders;
        private int activePage;
        private long nextUpdateTick = Long.MAX_VALUE;
        private CachedComponent globalCache;
        private final Map<UUID, CachedComponent> viewerCache = new HashMap<>();

        private ManagedHologram(int entityId, UUID entityUuid, HologramDefinition definition, boolean hasAnyPlaceholders, boolean playerScopedPlaceholders) {
            this.entityId = entityId;
            this.entityUuid = entityUuid;
            this.definition = definition;
            this.hasAnyPlaceholders = hasAnyPlaceholders;
            this.playerScopedPlaceholders = playerScopedPlaceholders;
        }
    }

    private static final class ViewerSession {
        private final Set<String> visibleIds = new LinkedHashSet<>();
        private final Set<String> forcedShown = new LinkedHashSet<>();
        private final Set<String> forcedHidden = new LinkedHashSet<>();
    }

    private record ScheduledContentUpdate(String hologramId, long tick) {
    }

    private record CachedComponent(Component component, long expiresAtTick, int page) {
    }

    private static final class PlayerPlaceholderResolver implements HologramPlaceholderResolver {
        @Override
        public boolean supports(String placeholder) {
            return "player".equalsIgnoreCase(placeholder);
        }

        @Override
        public boolean isPlayerScoped() {
            return true;
        }

        @Override
        public String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
            return viewer == null ? "{player}" : viewer.getName().getString();
        }
    }
}
