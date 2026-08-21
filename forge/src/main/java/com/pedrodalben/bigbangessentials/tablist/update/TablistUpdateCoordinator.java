package com.pedrodalben.bigbangessentials.tablist.update;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.feature.TablistFeature;
import com.pedrodalben.bigbangessentials.tablist.packet.TabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.state.RenderedTabState;
import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerStateResolver;
import com.pedrodalben.bigbangessentials.tablist.state.ViewerTargetState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TablistUpdateCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistUpdateCoordinator.class);

    private final Map<UUID, TabPlayerState> playerStates = new ConcurrentHashMap<>();
    private final Map<UUID, RenderedTabState> renderedStates = new ConcurrentHashMap<>();

    private final Map<UUID, Map<UUID, ViewerTargetState>> viewerTargetStates = new ConcurrentHashMap<>();

    private final List<TablistFeature> features = new ArrayList<>();
    private final TabPacketAdapter packetAdapter;
    private final TabAnimationRegistry animationRegistry;

    private int maxUpdatesPerTick = 250;
    TabPlayerStateResolver stateResolver; // package-private for re-hydration access

    private static final long QUEUE_STALE_MS = 5_000L; // discard items queued >5s ago

    private final Queue<QueuedUpdate> pendingQueue = new ConcurrentLinkedQueue<>();
    private int backlogSize = 0;

    public TablistUpdateCoordinator(TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        this.packetAdapter = packetAdapter;
        this.animationRegistry = animationRegistry;
    }

    public void registerFeature(TablistFeature feature) {
        features.add(feature);
    }

    public void loadConfig(TablistConfig config) {
        this.maxUpdatesPerTick = config.tablist.performance.maxPacketUpdatesPerTick;
        this.stateResolver = new TabPlayerStateResolver(config);
        for (TablistFeature feature : features) {
            feature.loadConfig(config);
        }
    }

    public void tick(MinecraftServer server) {
        animationRegistry.tickAll();

        for (TablistFeature feature : features) {
            feature.tick(server, animationRegistry);
        }

        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        int budget = maxUpdatesPerTick;

        // Process pending queue first (preserving priority); discard stale items.
        // Cap at 50 % of budget so slow-viewer backlog doesn't starve current-tick targets.
        long now = System.currentTimeMillis();
        int queueBudget = Math.min(budget / 2, pendingQueue.size());
        while (queueBudget > 0 && budget > 0) {
            QueuedUpdate update = pendingQueue.poll();
            if (update == null) break;
            if (now - update.enqueuedAtMs > QUEUE_STALE_MS) continue; // discard stale
            backlogSize = pendingQueue.size();

            ServerPlayer viewer = server.getPlayerList().getPlayer(update.viewerId);
            ServerPlayer target = server.getPlayerList().getPlayer(update.targetId);
            if (viewer == null || target == null) continue;

            TabPlayerState targetState = playerStates.get(update.targetId);
            if (targetState == null) continue;

            int sent = processViewerTargetPair(viewer, target, targetState, budget);
            budget -= sent;
            queueBudget--;
            if (budget <= 0) break;
        }

        // Collect dirty targets once, before iterating viewers
        List<UUID> dirtyTargetIds = new ArrayList<>();
        for (ServerPlayer p : onlinePlayers) {
            TabPlayerState s = playerStates.get(p.getUUID());
            if (s != null && hasViewerRelevantDirtyFlags(s)) {
                dirtyTargetIds.add(p.getUUID());
            }
        }

        if (dirtyTargetIds.isEmpty()) return;

        // Track which dirty targets were fully processed (to avoid clearing flags for queued ones)
        Set<UUID> processedTargets = new HashSet<>();

        // Process dirty players for each viewer
        outer:
        for (ServerPlayer viewer : onlinePlayers) {
            UUID viewerId = viewer.getUUID();
            TabPlayerState viewerState = playerStates.get(viewerId);
            RenderedTabState renderedState = renderedStates.get(viewerId);

            if (viewerState == null || renderedState == null) continue;

            // 1. Viewer-specific features (header/footer, objectives)
            for (TablistFeature feature : features) {
                feature.updatePlayer(viewer, viewerState, renderedState, packetAdapter, animationRegistry);
            }

            // 2. Viewer -> Target features (only dirty targets)
            Map<UUID, ViewerTargetState> targetsForViewer = viewerTargetStates.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());

            for (UUID targetId : dirtyTargetIds) {
                if (targetId.equals(viewerId)) continue;
                TabPlayerState targetState = playerStates.get(targetId);
                if (targetState == null) continue;

                ServerPlayer target = server.getPlayerList().getPlayer(targetId);
                if (target == null) continue;

                ViewerTargetState viewerTargetState = targetsForViewer.computeIfAbsent(targetId, k -> new ViewerTargetState(viewerId, targetId));
                EnumSet<TabDirtyFlag> targetFlags = targetState.snapshotDirtyFlags();

                int sent = processViewerTargetWithFlags(viewer, target, viewerState, targetState, viewerTargetState, targetFlags, budget);
                budget -= sent;
                processedTargets.add(targetId);

                if (budget <= 0) {
                    // Enqueue REMAINING targets (not processedTargets) for each viewer
                    for (UUID rid : dirtyTargetIds) {
                        if (rid.equals(viewerId) || processedTargets.contains(rid)) continue;
                        pendingQueue.add(new QueuedUpdate(viewerId, rid));
                    }
                    break outer;
                }
            }
        }

        // Only clear flags for targets actually processed (not queued)
        for (UUID pid : processedTargets) {
            TabPlayerState state = playerStates.get(pid);
            if (state != null) {
                state.clearDirtyFlags();
            }
        }

        if (budget <= 0) {
            LOGGER.debug("Tablist packet budget exhausted ({}), {} queued", maxUpdatesPerTick, pendingQueue.size());
        }
    }

    private int processViewerTargetPair(ServerPlayer viewer, ServerPlayer target, TabPlayerState targetState, int budget) {
        UUID viewerId = viewer.getUUID();
        TabPlayerState viewerState = playerStates.get(viewerId);
        if (viewerState == null) return 0;

        Map<UUID, ViewerTargetState> targetsForViewer = viewerTargetStates.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
        ViewerTargetState vts = targetsForViewer.computeIfAbsent(target.getUUID(), k -> new ViewerTargetState(viewerId, target.getUUID()));
        EnumSet<TabDirtyFlag> flags = targetState.snapshotDirtyFlags();

        return processViewerTargetWithFlags(viewer, target, viewerState, targetState, vts, flags, budget);
    }

    private int processViewerTargetWithFlags(ServerPlayer viewer, ServerPlayer target,
                                              TabPlayerState viewerState, TabPlayerState targetState,
                                              ViewerTargetState vts, EnumSet<TabDirtyFlag> flags,
                                              int budget) {
        // One viewer-target pair = ONE unit of work, regardless of how many features run.
        // Budget is max pairs per tick, not max feature calls.
        for (TablistFeature feature : features) {
            feature.updateViewerTarget(viewer, target, viewerState, targetState, vts, packetAdapter, animationRegistry);
        }
        return 1;
    }

    private boolean hasViewerRelevantDirtyFlags(TabPlayerState state) {
        // HEADER_FOOTER is viewer-specific (processed in updatePlayer), not viewer-target.
        // But we must include it so the outer loop runs when only header/footer changed.
        return state.hasDirtyFlag(TabDirtyFlag.HEADER_FOOTER)
                || state.hasDirtyFlag(TabDirtyFlag.PLAYER_LIST_NAME)
                || state.hasDirtyFlag(TabDirtyFlag.NAME_TAG)
                || state.hasDirtyFlag(TabDirtyFlag.SORT_ORDER)
                || state.hasDirtyFlag(TabDirtyFlag.VISIBILITY)
                || state.hasDirtyFlag(TabDirtyFlag.LATENCY)
                || state.hasDirtyFlag(TabDirtyFlag.OBJECTIVE)
                || state.hasDirtyFlag(TabDirtyFlag.FULL);
    }

    public void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        TabPlayerState state = new TabPlayerState(uuid, player.getName().getString());
        if (stateResolver != null) {
            stateResolver.hydrate(player, state);
        }
        playerStates.put(uuid, state);
        renderedStates.put(uuid, new RenderedTabState(uuid));
        viewerTargetStates.put(uuid, new ConcurrentHashMap<>());
    }

    public void onPlayerQuit(ServerPlayer player) {
        UUID uuid = player.getUUID();
        playerStates.remove(uuid);
        renderedStates.remove(uuid);
        viewerTargetStates.remove(uuid);

        for (Map<UUID, ViewerTargetState> map : viewerTargetStates.values()) {
            map.remove(uuid);
        }

        pendingQueue.removeIf(q -> q.viewerId.equals(uuid) || q.targetId.equals(uuid));

        for (TablistFeature feature : features) {
            feature.onQuit(player, packetAdapter);
        }
    }

    public void clearAll() {
        playerStates.clear();
        renderedStates.clear();
        viewerTargetStates.clear();
        pendingQueue.clear();
        backlogSize = 0;
    }

    public TabPlayerState getPlayerState(UUID uuid) {
        return playerStates.get(uuid);
    }

    public TabPlayerStateResolver getStateResolver() {
        return stateResolver;
    }

    public int getPlayerStatesCount() {
        return playerStates.size();
    }

    public int getBacklogSize() {
        return pendingQueue.size();
    }

    private static class QueuedUpdate {
        final UUID viewerId;
        final UUID targetId;
        final long enqueuedAtMs;

        QueuedUpdate(UUID viewerId, UUID targetId) {
            this.viewerId = viewerId;
            this.targetId = targetId;
            this.enqueuedAtMs = System.currentTimeMillis();
        }
    }
}
