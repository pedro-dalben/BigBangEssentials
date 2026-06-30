package com.pedrodalben.bigbangessentials.tablist;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
/**
 * Event handler that drives the TablistManager tick, join, and quit updates.
 */
@EventBusSubscriber(modid = "bigbangessentials")
public class TablistEventHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
        if (server == null) return;
        TablistManager.getInstance().onTick(server);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Load custom nickname if it exists
        String nickname = com.pedrodalben.bigbangessentials.util.commands.NickCommand.getNickname(player.getUUID());
        if (nickname != null && !nickname.isEmpty()) {
            String formattedNick = nickname.replace("&", "§");
            player.setCustomName(com.pedrodalben.bigbangessentials.util.MessageUtil.coloredText(formattedNick));
            player.setCustomNameVisible(true);
            TablistManager.getInstance().setCustomName(player.getUUID(), formattedNick);
        }

        TablistManager.getInstance().onPlayerJoin(player, server);
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        TablistManager.getInstance().clearCustomName(player.getUUID());
        TablistManager.getInstance().onPlayerQuit(server);
    }

    @SubscribeEvent
    public static void onPlayerNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String nickname = com.pedrodalben.bigbangessentials.util.commands.NickCommand.getNickname(player.getUUID());
            String name = (nickname != null && !nickname.isEmpty()) ? nickname : player.getName().getString();

            String prefix = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getPrefix(player.getUUID());
            String suffix = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getSuffix(player.getUUID());
            String tag = com.pedrodalben.bigbangessentials.tags.TagManager.getInstance().getSelectedChatTag(player);

            StringBuilder fullFormat = new StringBuilder();
            if (prefix != null && !prefix.isEmpty()) {
                fullFormat.append(prefix);
            }
            if (tag != null && !tag.isEmpty() && (prefix == null || !prefix.contains(tag.trim()))) {
                fullFormat.append(tag);
            }
            fullFormat.append(name);
            if (suffix != null && !suffix.isEmpty()) {
                fullFormat.append(suffix);
            }

            String formatted = fullFormat.toString().replace("&", "§");
            event.setDisplayname(com.pedrodalben.bigbangessentials.util.MessageUtil.coloredText(formatted));
        }
    }

    @SubscribeEvent
    public static void onPlayerTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String nickname = com.pedrodalben.bigbangessentials.util.commands.NickCommand.getNickname(player.getUUID());
            String name = (nickname != null && !nickname.isEmpty()) ? nickname : player.getName().getString();

            String prefix = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getPrefix(player.getUUID());
            String suffix = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getSuffix(player.getUUID());
            String tag = com.pedrodalben.bigbangessentials.tags.TagManager.getInstance().getSelectedChatTag(player);

            StringBuilder fullFormat = new StringBuilder();
            if (prefix != null && !prefix.isEmpty()) {
                fullFormat.append(prefix);
            }
            if (tag != null && !tag.isEmpty() && (prefix == null || !prefix.contains(tag.trim()))) {
                fullFormat.append(tag);
            }
            fullFormat.append(name);
            if (suffix != null && !suffix.isEmpty()) {
                fullFormat.append(suffix);
            }

            String formatted = fullFormat.toString().replace("&", "§");
            event.setDisplayName(com.pedrodalben.bigbangessentials.util.MessageUtil.coloredText(formatted));
        }
    }
}

