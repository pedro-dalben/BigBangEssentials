package com.pedrodalben.bigbangessentials.tablist;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class TablistEventHandler {

    public static void onServerTick(MinecraftServer server) {
        if (TablistModule.getInstance() != null && TablistModule.getInstance().isEnabled()) {
            TablistModule.getInstance().onServerTick(server);
        }
    }

    public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        if (TablistModule.getInstance() != null && TablistModule.getInstance().isEnabled()) {
            TablistModule.getInstance().onPlayerJoin(player);
        }
    }

    public static void onPlayerQuit(ServerPlayer player, MinecraftServer server) {
        if (TablistModule.getInstance() != null && TablistModule.getInstance().isEnabled()) {
            TablistModule.getInstance().onPlayerQuit(player);
        }
    }

    public static void onServerStop(MinecraftServer server) {
        if (TablistModule.getInstance() != null) {
            TablistModule.getInstance().onDisable();
        }
    }
}
