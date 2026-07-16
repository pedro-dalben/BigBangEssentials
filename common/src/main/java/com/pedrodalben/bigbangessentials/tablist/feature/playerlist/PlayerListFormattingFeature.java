package com.pedrodalben.bigbangessentials.tablist.feature.playerlist;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.feature.TablistFeature;
import com.pedrodalben.bigbangessentials.tablist.packet.TabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.CompiledTabTemplate;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.render.TabTemplateCompiler;
import com.pedrodalben.bigbangessentials.tablist.state.RenderedTabState;
import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import com.pedrodalben.bigbangessentials.tablist.state.ViewerTargetState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListFormattingFeature implements TablistFeature {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private boolean enabled = true;
    private CompiledTabTemplate defaultFormat;
    private final Map<String, CompiledTabTemplate> groupFormats = new HashMap<>();
    private final Map<String, CompiledTabTemplate> playerFormats = new HashMap<>();
    private String afkFormat = " \u00a77[AFK]";
    private String nameSource = "NICK_OR_REAL";
    
    // Internal cache for target's formatted string per tick to avoid re-rendering for every viewer
    private final Map<UUID, Component> currentTickDisplayNames = new HashMap<>();
    // Last self-view display name per player to avoid duplicate packets
    private final Map<UUID, Component> lastSelfDisplayNames = new HashMap<>();

    @Override
    public void loadConfig(TablistConfig config) {
        enabled = config.tablist.playerList.enabled;
        defaultFormat = TabTemplateCompiler.compile(config.tablist.playerList.defaultFormat);
        afkFormat = config.tablist.afk.enabled ? config.tablist.afk.format : "";
        nameSource = config.tablist.playerList.nameSource;
        
        groupFormats.clear();
        for (Map.Entry<String, TablistConfig.GroupFormatSection> entry : config.tablist.playerList.groups.entrySet()) {
            groupFormats.put(entry.getKey(), TabTemplateCompiler.compile(entry.getValue().format));
        }
        
        playerFormats.clear();
        for (Map.Entry<String, TablistConfig.GroupFormatSection> entry : config.tablist.playerList.players.entrySet()) {
            playerFormats.put(entry.getKey(), TabTemplateCompiler.compile(entry.getValue().format));
        }
    }

    @Override
    public void tick(MinecraftServer server, TabAnimationRegistry animationRegistry) {
        currentTickDisplayNames.clear();
    }

    @Override
    public void updatePlayer(ServerPlayer player, TabPlayerState state, RenderedTabState renderedState, 
                             TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        // Self-view: player must see their own display name update in the tablist.
        // The viewer-target loop skips self (targetId.equals(viewerId)), so handle it here.
        if (!enabled) return;
        if (!state.hasDirtyFlag(TabDirtyFlag.PLAYER_LIST_NAME)) return;

        Component displayName = buildDisplayName(player, state);
        Component last = lastSelfDisplayNames.get(player.getUUID());
        if (last == null || !last.getString().equals(displayName.getString())) {
            packetAdapter.updateDisplayName(player, player.getUUID(), displayName);
            lastSelfDisplayNames.put(player.getUUID(), displayName);
        }
    }

    @Override
    public void updateViewerTarget(ServerPlayer viewer, ServerPlayer target, 
                                   TabPlayerState viewerState, TabPlayerState targetState, 
                                   ViewerTargetState viewerTargetState, 
                                   TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        if (!enabled) return;
        if (!targetState.hasDirtyFlag(TabDirtyFlag.PLAYER_LIST_NAME)) return;
        
        Component displayName = currentTickDisplayNames.computeIfAbsent(target.getUUID(), uuid -> {
            return buildDisplayName(target, targetState);
        });

        if (viewerTargetState.hasDisplayNameChanged(displayName)) {
            packetAdapter.updateDisplayName(viewer, target.getUUID(), displayName);
            viewerTargetState.setLastDisplayName(displayName);
        }
    }

    /** Resolve template, replace placeholders, return Component. Shared by self-view and viewer-target. */
    private Component buildDisplayName(ServerPlayer player, TabPlayerState state) {
        CompiledTabTemplate template = resolveTemplate(player, state);
        String text = template.render(player, null); // animations already resolved per-tick elsewhere

        String prefix = state.getPrefix() != null ? state.getPrefix() : "";
        String suffix = state.getSuffix() != null ? state.getSuffix() : "";
        String tag = state.getTag() != null ? state.getTag() : "";
        boolean useNick = "NICK".equalsIgnoreCase(nameSource)
            || ("NICK_OR_REAL".equalsIgnoreCase(nameSource) && state.getNick() != null && !state.getNick().isEmpty());
        String name = state.getDisplayNameSource(useNick);
        String afk = state.isAfk() ? afkFormat : "";

        text = text.replace("{prefix}", prefix)
                   .replace("{suffix}", suffix)
                   .replace("{tag}", tag)
                   .replace("{name}", name)
                   .replace("{afk}", afk)
                   .replace("{x}", String.valueOf(player.getBlockX()))
                   .replace("{y}", String.valueOf(player.getBlockY()))
                   .replace("{z}", String.valueOf(player.getBlockZ()))
                   .replace("{time}", LocalTime.now().format(TIME_FORMAT))
                   .replace("{bar}", "§8§m                              §r");
        // Convert color codes in dynamic values (prefix, tag, suffix from config/API)
        // Template literals already have &→§ conversion applied at compile time.
        text = text.replace("&", "§");

        if (text.contains("{balance}")) {
            double bal;
            if (!state.isBalanceFetched()) {
                // Fetch once per state lifecycle (lazy) — sync because
                // EconomyManager caches internally.
                try {
                    bal = com.pedrodalben.bigbangessentials.economy.managers.EconomyManager.getInstance()
                        .getBalance(player.getUUID()).doubleValue();
                } catch (Exception ignored) {
                    bal = 0.0;
                }
                state.setCachedBalance(bal);
            } else {
                bal = state.getCachedBalance();
            }
            text = text.replace("{balance}", String.format("%.2f", bal));
        }

        if (text.contains("{server_name}")) {
            String motd = player.getServer() != null ? player.getServer().getMotd() : "";
            text = text.replace("{server_name}", motd);
        }

        return Component.literal(text);
    }

    private CompiledTabTemplate resolveTemplate(ServerPlayer player, TabPlayerState state) {
        CompiledTabTemplate template = playerFormats.get(player.getName().getString());
        if (template == null) {
            template = groupFormats.get(state.getPrimaryGroup());
        }
        if (template == null) {
            template = defaultFormat;
        }
        return template;
    }
}
