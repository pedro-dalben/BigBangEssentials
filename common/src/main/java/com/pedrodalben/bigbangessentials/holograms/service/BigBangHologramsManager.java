package com.pedrodalben.bigbangessentials.holograms.service;

import com.pedrodalben.bigbangessentials.holograms.action.ActionEngine;
import com.pedrodalben.bigbangessentials.holograms.action.InteractionHandler;
import com.pedrodalben.bigbangessentials.holograms.animation.AnimationConfig;
import com.pedrodalben.bigbangessentials.holograms.animation.AnimationEngine;
import com.pedrodalben.bigbangessentials.holograms.api.*;
import com.pedrodalben.bigbangessentials.holograms.config.HologramConfig;
import com.pedrodalben.bigbangessentials.holograms.config.HologramConfigStore;
import com.pedrodalben.bigbangessentials.holograms.event.*;
import com.pedrodalben.bigbangessentials.holograms.metrics.MetricsService;
import com.pedrodalben.bigbangessentials.holograms.migration.LegacyCrateHologramCleaner;
import com.pedrodalben.bigbangessentials.holograms.persistence.HologramPersistenceService;
import com.pedrodalben.bigbangessentials.holograms.placeholder.BuiltInPlaceholders;
import com.pedrodalben.bigbangessentials.holograms.placeholder.PlaceholderEngine;
import com.pedrodalben.bigbangessentials.holograms.render.RenderService;
import com.pedrodalben.bigbangessentials.holograms.viewer.ViewerService;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

public final class BigBangHologramsManager implements HologramService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BigBangHologramsManager.class);
    private static final BigBangHologramsManager INSTANCE = new BigBangHologramsManager();

    private final HologramRegistry registry;
    private final ViewerService viewerService;
    private final RenderService renderService;
    private final MetricsService metrics;
    private final PlaceholderEngine placeholderEngine;
    private final AnimationEngine animationEngine;
    private final ActionEngine actionEngine;
    private final InteractionHandler interactionHandler;
    private final HologramPersistenceService persistenceService;
    private final HologramConfigStore configStore;
    private final LegacyCrateHologramCleaner legacyCleaner;
    private final List<HologramLifecycleListener> lifecycleListeners;
    private final PriorityQueue<ScheduledContentUpdate> scheduledUpdates;

    private HologramConfig config = HologramConfig.defaults();
    private long tickCounter;
    private int viewerRoundRobinIndex;
    private boolean initialized;

    private BigBangHologramsManager() {
        this.registry = new HologramRegistry();
        this.viewerService = new ViewerService();
        this.metrics = MetricsService.getInstance();
        this.placeholderEngine = new PlaceholderEngine();
        this.animationEngine = new AnimationEngine(AnimationConfig.defaults().minimumIntervalTicks());
        this.configStore = new HologramConfigStore();
        this.legacyCleaner = new LegacyCrateHologramCleaner();
        this.lifecycleListeners = new CopyOnWriteArrayList<>();
        this.scheduledUpdates = new PriorityQueue<>(Comparator.comparingLong(ScheduledContentUpdate::tick));

        this.renderService = new RenderService(registry, viewerService, metrics, placeholderEngine, animationEngine);
        this.actionEngine = new ActionEngine(this::switchPageForPlayer);

        this.interactionHandler = new InteractionHandler(actionEngine);
        this.persistenceService = new HologramPersistenceService();

        registerPlaceholderResolver(new PlayerPlaceholderResolver());
        BuiltInPlaceholders.registerAll(placeholderEngine);
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
            for (HologramDefinition definition : persistenceService.loadAll()) {
                upsert(definition, false);
            }
        }

        if (config.cleanupEnabled() && config.cleanupOnServerStart()) {
            legacyCleaner.cleanupLoadedLevels();
        }

        LOGGER.info("BigBangHolograms initialized with {} hologram(s)", registry.size());
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
        ViewerService.ViewerSession session = viewerService.removeSession(player.getUUID());
        if (session == null) {
            return;
        }
        for (String hologramId : session.visibleIds()) {
            HologramRegistry.ManagedHologram hologram = registry.get(hologramId);
            if (hologram != null) {
                renderService.hideHologram(player, hologram.entityId());
                interactionHandler.unregister(hologram.entityId());
                lifecycleListeners.forEach(listener -> listener.onHidden(hologram.definition(), player));
            }
        }
    }

    public synchronized void onPlayerStateInvalidated(ServerPlayer player) {
        ViewerService.ViewerSession session = viewerService.getSession(player);
        for (String hologramId : new ArrayList<>(session.visibleIds())) {
            HologramRegistry.ManagedHologram hologram = registry.get(hologramId);
            if (hologram != null) {
                renderService.hideHologram(player, hologram.entityId());
                interactionHandler.unregister(hologram.entityId());
                lifecycleListeners.forEach(listener -> listener.onHidden(hologram.definition(), player));
                HologramEventBus.get().post(new HologramHideEvent(hologram.definition(), player));
            }
        }
        viewerService.invalidate(player);
        interactionHandler.clear();
    }

    public synchronized void syncPlayerNow(ServerPlayer player) {
        ensureInitialized();
        syncViewer(player);
    }

    public void fireOnShown(HologramDefinition definition, ServerPlayer player) {
        lifecycleListeners.forEach(listener -> listener.onShown(definition, player));
        HologramEventBus.get().post(new HologramShowEvent(definition, player));
    }

    public LegacyCrateHologramCleaner getLegacyCleaner() {
        return legacyCleaner;
    }

    public HologramRegistry getRegistry() {
        return registry;
    }

    public ViewerService getViewerService() {
        return viewerService;
    }

    public RenderService getRenderService() {
        return renderService;
    }

    public MetricsService getMetrics() {
        return metrics;
    }

    public PlaceholderEngine getPlaceholderEngine() {
        return placeholderEngine;
    }

    public AnimationEngine getAnimationEngine() {
        return animationEngine;
    }

    public ActionEngine getActionEngine() {
        return actionEngine;
    }

    public InteractionHandler getInteractionHandler() {
        return interactionHandler;
    }

    private void switchPageForPlayer(ServerPlayer player, String hologramId, int pageIndex) {
        HologramRegistry.ManagedHologram hologram = registry.get(hologramId);
        if (hologram == null) {
            return;
        }
        viewerService.setCurrentPage(player, hologramId, pageIndex);
        viewerService.invalidate(player);
        syncViewer(player);
    }

    @Override
    public synchronized Optional<HologramHandle> find(String id) {
        String normalized = HologramDefinition.normalizeId(id);
        return registry.contains(normalized)
            ? Optional.of(new HologramHandle(this, normalized))
            : Optional.empty();
    }

    @Override
    public synchronized Optional<HologramDefinition> findDefinition(String id) {
        String normalized = HologramDefinition.normalizeId(id);
        HologramRegistry.ManagedHologram hologram = registry.get(normalized);
        return hologram == null ? Optional.empty() : Optional.of(hologram.definition());
    }

    @Override
    public synchronized boolean exists(String id) {
        return registry.contains(HologramDefinition.normalizeId(id));
    }

    @Override
    public synchronized HologramHandle create(HologramDefinition definition) {
        String id = HologramDefinition.normalizeId(definition.id());
        if (registry.contains(id)) {
            throw new IllegalArgumentException("Hologram already exists: " + id);
        }
        return createOrUpdate(definition);
    }

    @Override
    public synchronized HologramHandle createOrUpdate(HologramDefinition definition) {
        ensureInitialized();
        ensureMainThread();
        return new HologramHandle(this, upsert(definition, true).definition().id());
    }

    @Override
    public synchronized Optional<HologramHandle> update(String id, UnaryOperator<HologramDefinitionBuilder> mutator) {
        String normalized = HologramDefinition.normalizeId(id);
        HologramRegistry.ManagedHologram existing = registry.get(normalized);
        if (existing == null) {
            return Optional.empty();
        }
        HologramDefinition mutated = mutator.apply(existing.definition().toBuilder()).build();
        return Optional.of(createOrUpdate(mutated));
    }

    @Override
    public synchronized boolean delete(String id) {
        ensureInitialized();
        ensureMainThread();
        String normalized = HologramDefinition.normalizeId(id);
        HologramRegistry.ManagedHologram removed = registry.remove(normalized);
        if (removed == null) {
            return false;
        }
        for (ServerPlayer player : onlinePlayers()) {
            ViewerService.ViewerSession session = viewerService.getSession(player);
            if (session.visibleIds().remove(normalized)) {
                renderService.hideHologram(player, removed.entityId());
                interactionHandler.unregister(removed.entityId());
                lifecycleListeners.forEach(listener -> listener.onHidden(removed.definition(), player));
            }
            session.forcedShown().remove(normalized);
            session.forcedHidden().remove(normalized);
        }
        persistenceService.delete(normalized);
        lifecycleListeners.forEach(listener -> listener.onDeleted(normalized));
        HologramEventBus.get().post(new HologramDeleteEvent(removed.definition()));
        LOGGER.debug("Deleted hologram {}", normalized);
        return true;
    }

    @Override
    public synchronized int deleteByOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return 0;
        }
        List<String> ids = new ArrayList<>();
        for (HologramRegistry.ManagedHologram hologram : registry.getAllManaged()) {
            if (ownerId.equalsIgnoreCase(hologram.definition().ownerId())) {
                ids.add(hologram.definition().id());
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
        HologramRegistry.ManagedHologram hologram = registry.get(normalized);
        if (hologram == null) {
            throw new IllegalArgumentException("Unknown hologram: " + normalized);
        }
        ViewerService.ViewerSession session = viewerService.getSession(player);
        session.forcedHidden().remove(normalized);
        session.forcedShown().add(normalized);
        syncViewer(player);
    }

    @Override
    public synchronized void hideFrom(ServerPlayer player, String id) {
        String normalized = HologramDefinition.normalizeId(id);
        ViewerService.ViewerSession session = viewerService.getSession(player);
        session.forcedShown().remove(normalized);
        session.forcedHidden().add(normalized);
        HologramRegistry.ManagedHologram hologram = registry.get(normalized);
        if (hologram != null && session.visibleIds().remove(normalized)) {
            renderService.hideHologram(player, hologram.entityId());
            interactionHandler.unregister(hologram.entityId());
            lifecycleListeners.forEach(listener -> listener.onHidden(hologram.definition(), player));
        }
    }

    @Override
    public synchronized void reload() {
        ensureInitialized();
        ensureMainThread();
        this.config = configStore.load();

        if (config.persistenceEnabled()) {
            persistenceService.flush();
        }

        List<HologramDefinition> persistent = config.persistenceEnabled() ? persistenceService.loadAll() : List.of();
        Set<String> desiredPersistentIds = new HashSet<>();
        for (HologramDefinition definition : persistent) {
            desiredPersistentIds.add(definition.id());
            upsert(definition, false);
        }
        List<String> toDelete = new ArrayList<>();
        for (HologramRegistry.ManagedHologram hologram : registry.getAllManaged()) {
            if (hologram.definition().persistent() && !desiredPersistentIds.contains(hologram.definition().id())) {
                toDelete.add(hologram.definition().id());
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
        persistenceService.shutdown();
        for (ServerPlayer player : onlinePlayers()) {
            onPlayerDisconnect(player);
        }
        viewerService.clear();
        registry.clear();
        scheduledUpdates.clear();
        initialized = false;
    }

    @Override
    public synchronized Collection<HologramDefinition> getDefinitions() {
        return registry.getAll();
    }

    @Override
    public synchronized HologramStats getStats() {
        int persistent = 0;
        int crates = 0;
        int viewerEntries = 0;
        for (HologramRegistry.ManagedHologram hologram : registry.getAllManaged()) {
            if (hologram.definition().persistent()) {
                persistent++;
            }
            if (hologram.definition().id().startsWith("bigbangessentials:crate/")) {
                crates++;
            }
        }
        int sessionCount = viewerService.getSessionCount();
        return metrics.buildStats(registry.size(), persistent, crates, sessionCount, viewerEntries, 0.0, scheduledUpdates.size());
    }

    @Override
    public void registerPlaceholderResolver(HologramPlaceholderResolver resolver) {
        placeholderEngine.register(resolver);
    }

    @Override
    public void registerLifecycleListener(HologramLifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    public synchronized void enableHologram(String id) {
        HologramRegistry.ManagedHologram hologram = registry.get(HologramDefinition.normalizeId(id));
        if (hologram != null) {
            HologramDefinition enabled = hologram.definition().toBuilder().enabled(true).build();
            upsert(enabled, true);
            HologramEventBus.get().post(new HologramEnableEvent(enabled));
        }
    }

    public synchronized void disableHologram(String id) {
        HologramRegistry.ManagedHologram hologram = registry.get(HologramDefinition.normalizeId(id));
        if (hologram != null) {
            HologramDefinition disabled = hologram.definition().toBuilder().enabled(false).build();
            upsert(disabled, true);
            HologramEventBus.get().post(new HologramDisableEvent(disabled));
        }
    }

    private HologramRegistry.ManagedHologram upsert(HologramDefinition requested, boolean persist) {
        HologramDefinition sanitized = sanitize(requested);
        PlaceholderEngine.PlaceholderSummary placeholderSummary = placeholderEngine.inspect(sanitized);
        boolean isNew = !registry.contains(sanitized.id());
        HologramRegistry.ManagedHologram hologram = registry.add(sanitized, placeholderSummary.hasPlaceholders(), placeholderSummary.playerScoped());
        scheduleIfDynamic(hologram);
        if (persist && sanitized.persistent()) {
            persistenceService.save(sanitized);
        }
        for (ServerPlayer player : onlinePlayers()) {
            ViewerService.ViewerSession session = viewerService.getSession(player);
            if (session != null && session.visibleIds().contains(sanitized.id())) {
                // Force re-render for already-visible viewers instead of skipping them
                hologram.setGlobalCache(null);
                hologram.viewerCache().clear();
                session.fingerprints().remove(sanitized.id());
                renderService.updateHologram(player, hologram, tickCounter);
            }
            syncViewer(player);
        }
        if (isNew) {
            lifecycleListeners.forEach(listener -> listener.onCreated(sanitized));
            HologramEventBus.get().post(new HologramCreateEvent(sanitized));
            LOGGER.debug("Created hologram {}", sanitized.id());
        } else {
            lifecycleListeners.forEach(listener -> listener.onUpdated(sanitized));
            LOGGER.debug("Updated hologram {}", sanitized.id());
        }
        return hologram;
    }

    private void scheduleIfDynamic(HologramRegistry.ManagedHologram hologram) {
        if (!isDynamic(hologram)) {
            hologram.setNextUpdateTick(Long.MAX_VALUE);
            return;
        }
        long nextTick = tickCounter + Math.max(1, Math.max(config.dynamicUpdateMinIntervalTicks(), hologram.definition().refreshIntervalTicks()));
        if (hologram.definition().pageSwitchIntervalTicks() > 0) {
            nextTick = tickCounter + Math.max(1, hologram.definition().pageSwitchIntervalTicks());
        }
        hologram.setNextUpdateTick(nextTick);
        scheduledUpdates.add(new ScheduledContentUpdate(hologram.definition().id(), nextTick));
    }

    private boolean isDynamic(HologramRegistry.ManagedHologram hologram) {
        if (hologram.definition().flags().contains(HologramFlag.DISABLE_UPDATING)) {
            return false;
        }
        return hologram.definition().updatePolicy() == HologramUpdatePolicy.DYNAMIC
            || hologram.definition().pageSwitchIntervalTicks() > 0
            || hologram.hasAnyPlaceholders();
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
        ViewerService.ViewerSession session = viewerService.getSession(player);
        int displayRadius = config.maxViewDistance();

        Set<String> candidates = registry.queryNearby(
            player.serverLevel().dimension().location(),
            player.getX(),
            player.getZ(),
            displayRadius
        );

        for (HologramRegistry.ManagedHologram hologram : registry.getAllManaged()) {
            if (hologram.definition().visibilityPolicy() == HologramVisibilityPolicy.GLOBAL
                && hologram.definition().location().dimension().equals(player.serverLevel().dimension())) {
                candidates.add(hologram.definition().id());
            }
        }

        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(id -> {
            HologramRegistry.ManagedHologram h = registry.get(id);
            return h == null ? Double.MAX_VALUE : distanceSq(h, player);
        }));

        Set<String> shouldSee = new LinkedHashSet<>();
        for (String id : sorted) {
            HologramRegistry.ManagedHologram hologram = registry.get(id);
            if (hologram != null && shouldRenderTo(player, session, hologram)) {
                shouldSee.add(id);
                if (shouldSee.size() >= config.maxHologramsPerPlayer()) {
                    break;
                }
            }
        }

        for (String forced : session.forcedShown()) {
            HologramRegistry.ManagedHologram hologram = registry.get(forced);
            if (hologram != null && !session.forcedHidden().contains(forced)) {
                shouldSee.add(forced);
            }
        }

        for (String id : new ArrayList<>(session.visibleIds())) {
            if (!shouldSee.contains(id)) {
                HologramRegistry.ManagedHologram hologram = registry.get(id);
                if (hologram != null) {
                    renderService.hideHologram(player, hologram.entityId());
                    interactionHandler.unregister(hologram.entityId());
                    lifecycleListeners.forEach(listener -> listener.onHidden(hologram.definition(), player));
                }
                session.visibleIds().remove(id);
            }
        }

        for (String id : shouldSee) {
            HologramRegistry.ManagedHologram hologram = registry.get(id);
            if (hologram == null || session.visibleIds().contains(id)) {
                continue;
            }
            renderService.showHologram(player, hologram);
            interactionHandler.register(hologram.entityId(), hologram.definition().id(), hologram.activePage());
            session.visibleIds().add(id);
        }
    }

    private boolean shouldRenderTo(ServerPlayer player, ViewerService.ViewerSession session, HologramRegistry.ManagedHologram hologram) {
        HologramDefinition def = hologram.definition();

        if (!def.enabled()) {
            return false;
        }
        if (session.forcedHidden().contains(def.id())) {
            return false;
        }
        if (def.flags().contains(HologramFlag.MANUAL_VISIBILITY)) {
            return session.forcedShown().contains(def.id());
        }
        if (!player.serverLevel().dimension().equals(def.location().dimension())) {
            return false;
        }
        if ((def.hideInSpectator() || def.flags().contains(HologramFlag.IGNORE_SPECTATORS)) && player.isSpectator()) {
            return false;
        }
        if (!def.requiredPermission().isBlank() && !player.hasPermissions(2) && !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), def.requiredPermission())) {
            return false;
        }
        int displayDistance = def.displayDistance() > 0 ? def.displayDistance() : def.viewDistance();
        return switch (def.visibilityPolicy()) {
            case GLOBAL -> true;
            case MANUAL -> session.forcedShown().contains(def.id());
            case NEARBY_PLAYERS -> distanceSq(hologram, player) <= (double) displayDistance * displayDistance;
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
            HologramRegistry.ManagedHologram hologram = registry.get(scheduled.hologramId());
            if (hologram == null || hologram.nextUpdateTick() != scheduled.tick()) {
                continue;
            }
            if (hologram.definition().flags().contains(HologramFlag.DISABLE_UPDATING)) {
                scheduleIfDynamic(hologram);
                continue;
            }

            long started = System.nanoTime();

            if (hologram.definition().pageSwitchIntervalTicks() > 0 && hologram.definition().pages().size() > 1) {
                hologram.setActivePage((hologram.activePage() + 1) % hologram.definition().pages().size());
                hologram.setGlobalCache(null);
                hologram.viewerCache().clear();
            } else if (hologram.hasAnyPlaceholders()) {
                hologram.setGlobalCache(null);
                hologram.viewerCache().clear();
            }

            int updateDistance = hologram.definition().updateDistance() > 0
                ? hologram.definition().updateDistance()
                : hologram.definition().displayDistance() > 0 ? hologram.definition().displayDistance() : config.maxViewDistance();

            for (ServerPlayer player : onlinePlayers()) {
                ViewerService.ViewerSession session = viewerService.getSession(player);
                if (session == null || !session.visibleIds().contains(hologram.definition().id())) {
                    continue;
                }
                if (distanceSq(hologram, player) > (double) updateDistance * updateDistance) {
                    continue;
                }
                renderService.updateHologram(player, hologram, tickCounter);
            }

            processed++;
            metrics.addUpdateNanos(System.nanoTime() - started);
            scheduleIfDynamic(hologram);
        }
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

        return definition.toBuilder()
            .pages(sanitizedPages)
            .viewDistance(Math.min(definition.viewDistance(), config.maxViewDistance()))
            .refreshIntervalTicks(definition.refreshIntervalTicks() <= 0
                ? 0
                : Math.max(definition.refreshIntervalTicks(), config.dynamicUpdateMinIntervalTicks()))
            .shadow(definition.shadow())
            .seeThrough(definition.seeThrough())
            .billboard(definition.billboard() == null ? config.billboard() : definition.billboard())
            .build();
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

    private static double distanceSq(HologramRegistry.ManagedHologram hologram, ServerPlayer player) {
        HologramDefinition def = hologram.definition();
        return player.distanceToSqr(
            def.location().x() + def.offsetX(),
            def.location().y() + def.offsetY(),
            def.location().z() + def.offsetZ()
        );
    }

    private record ScheduledContentUpdate(String hologramId, long tick) {
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
