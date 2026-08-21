package com.pedrodalben.bigbangessentials.tablist;

import com.pedrodalben.bigbangessentials.tablist.api.TablistAPI;
import com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason;
import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.config.TablistConfigLoader;
import com.pedrodalben.bigbangessentials.tablist.feature.header.HeaderFooterFeature;
import com.pedrodalben.bigbangessentials.tablist.feature.nametag.NameTagFeature;
import com.pedrodalben.bigbangessentials.tablist.feature.objective.PlayerListObjectiveFeature;
import com.pedrodalben.bigbangessentials.tablist.feature.playerlist.PlayerListFormattingFeature;
import com.pedrodalben.bigbangessentials.tablist.feature.sorting.SortingFeature;
import com.pedrodalben.bigbangessentials.tablist.feature.visibility.VisibilityFeature;
import com.pedrodalben.bigbangessentials.tablist.packet.NeoForgeTabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerStateResolver;
import com.pedrodalben.bigbangessentials.tablist.update.TablistUpdateCoordinator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class TablistModule implements TablistAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistModule.class);

    private static TablistModule instance;

    private TablistUpdateCoordinator coordinator;
    private TabAnimationRegistry animationRegistry;
    private NeoForgeTabPacketAdapter packetAdapter;
    private TabPlayerStateResolver stateResolver;

    private MinecraftServer serverRef;
    private int tickCounter = 0;
    private int fallbackRefreshTicks = 100;
    private int permissionRefreshTicks = 100;
    private boolean enabled = false;

    public TablistModule() {
        instance = this;
    }

    public static TablistModule getInstance() {
        return instance;
    }

    public static TablistAPI getApi() {
        return instance;
    }

    public void onEnable(MinecraftServer server) {
        this.serverRef = server;
        LOGGER.info("Starting Tablist Module V2...");

        // Full cleanup before reload (safe if already enabled, prevents duplicate features)
        if (coordinator != null) {
            coordinator.clearAll();
        }
        enabled = false;
        coordinator = null;
        animationRegistry = null;
        packetAdapter = null;
        tickCounter = 0;

        if (!TablistConfigLoader.load()) {
            LOGGER.error("Failed to load Tablist config!");
            return;
        }

        TablistConfig config = TablistConfigLoader.getConfig();
        if (!config.tablist.enabled) {
            LOGGER.info("Tablist Module is disabled in config.");
            return;
        }

        // Always recreate coordinator and features (handles first-load and reload)
        animationRegistry = new TabAnimationRegistry();
        packetAdapter = new NeoForgeTabPacketAdapter();

        SortingFeature sortingFeature = new SortingFeature();
        coordinator = new TablistUpdateCoordinator(packetAdapter, animationRegistry);
        coordinator.registerFeature(new HeaderFooterFeature());
        coordinator.registerFeature(new PlayerListFormattingFeature());
        coordinator.registerFeature(sortingFeature);
        coordinator.registerFeature(new NameTagFeature(sortingFeature));
        coordinator.registerFeature(new PlayerListObjectiveFeature());
        coordinator.registerFeature(new VisibilityFeature());

        fallbackRefreshTicks = config.tablist.performance.fallbackRefreshTicks;
        permissionRefreshTicks = config.tablist.performance.permissionRefreshTicks;

        animationRegistry.loadFromConfig(config.tablist.animations);
        coordinator.loadConfig(config);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            coordinator.onPlayerJoin(player);
            // Re-hydrate existing player state after config reload
            TabPlayerState existingState = coordinator.getPlayerState(player.getUUID());
            if (existingState != null && coordinator.getStateResolver() != null) {
                coordinator.getStateResolver().hydrate(player, existingState);
            }
            invalidatePlayer(player.getUUID(), TablistInvalidationReason.JOIN);
        }

        enabled = true;
        LOGGER.info("Tablist Module V2 enabled");
    }

    public void onDisable() {
        LOGGER.info("Disabling Tablist Module V2...");
        enabled = false;

        if (coordinator != null) {
            coordinator.clearAll();
        }

        // Keep instance reference so API consumers don't NPE after disable.
        // instance=null would break TablistAPI.getInstance() callers.
        coordinator = null;
        animationRegistry = null;
        packetAdapter = null;
        stateResolver = null;
        tickCounter = 0;
        serverRef = null;
    }

    public void onServerTick(MinecraftServer server) {
        if (!enabled || coordinator == null) return;

        tickCounter++;

        if (tickCounter % permissionRefreshTicks == 0) {
            invalidateAll(TablistInvalidationReason.GROUP_CHANGED);
        }
        if (tickCounter % fallbackRefreshTicks == 0) {
            invalidateAll(TablistInvalidationReason.PERIODIC_REFRESH);
        }

        coordinator.tick(server);
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (!enabled || coordinator == null) return;
        coordinator.onPlayerJoin(player);
        invalidatePlayer(player.getUUID(), TablistInvalidationReason.JOIN);
    }

    public void onPlayerQuit(ServerPlayer player) {
        if (!enabled || coordinator == null) return;
        coordinator.onPlayerQuit(player);
    }

    @Override
    public void invalidatePlayer(UUID playerId, TablistInvalidationReason reason) {
        if (coordinator == null) return;
        TabPlayerState state = coordinator.getPlayerState(playerId);
        if (state == null) return;

        switch (reason) {
            case JOIN:
            case RELOAD:
                state.markDirty(TabDirtyFlag.FULL);
                break;
            case NICK_CHANGED:
            case PREFIX_SUFFIX_CHANGED:
            case TAG_CHANGED:
                state.markDirty(TabDirtyFlag.PLAYER_LIST_NAME);
                state.markDirty(TabDirtyFlag.NAME_TAG);
                break;
            case AFK_CHANGED:
                state.markDirty(TabDirtyFlag.PLAYER_LIST_NAME);
                state.markDirty(TabDirtyFlag.SORT_ORDER);
                state.markDirty(TabDirtyFlag.NAME_TAG);
                break;
            case VANISH_CHANGED:
                state.markDirty(TabDirtyFlag.VISIBILITY);
                break;
            case PING_CHANGED:
                state.markDirty(TabDirtyFlag.LATENCY);
                break;
            case GROUP_CHANGED:
                state.markDirty(TabDirtyFlag.SORT_ORDER);
                state.markDirty(TabDirtyFlag.PLAYER_LIST_NAME);
                state.markDirty(TabDirtyFlag.NAME_TAG);
                state.markDirty(TabDirtyFlag.VISIBILITY);
                break;
            case WORLD_CHANGED:
                state.markDirty(TabDirtyFlag.HEADER_FOOTER);
                break;
            case PERIODIC_REFRESH:
                state.markDirty(TabDirtyFlag.HEADER_FOOTER);
                state.markDirty(TabDirtyFlag.PLAYER_LIST_NAME);
                state.markDirty(TabDirtyFlag.OBJECTIVE);
                break;
        }
    }

    @Override
    public void invalidateAll(TablistInvalidationReason reason) {
        MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                invalidatePlayer(p.getUUID(), reason);
            }
        }
    }

    public TablistUpdateCoordinator getCoordinator() {
        return coordinator;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
