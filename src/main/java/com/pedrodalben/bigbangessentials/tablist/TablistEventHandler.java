package com.pedrodalben.bigbangessentials.tablist;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Event handler that drives the TablistManager tick, join, and quit updates.
 */
@EventBusSubscriber(modid = "bigbangessentials")
public class TablistEventHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
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
            if (nickname != null && !nickname.isEmpty()) {
                String formattedNick = nickname.replace("&", "§");
                event.setDisplayname(com.pedrodalben.bigbangessentials.util.MessageUtil.coloredText(formattedNick));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String nickname = com.pedrodalben.bigbangessentials.util.commands.NickCommand.getNickname(player.getUUID());
            if (nickname != null && !nickname.isEmpty()) {
                String formattedNick = nickname.replace("&", "§");
                event.setDisplayName(com.pedrodalben.bigbangessentials.util.MessageUtil.coloredText(formattedNick));
            }
        }
    }
}

