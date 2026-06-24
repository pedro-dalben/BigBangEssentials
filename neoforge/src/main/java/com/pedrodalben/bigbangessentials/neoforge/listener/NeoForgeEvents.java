package com.pedrodalben.bigbangessentials.neoforge.listener;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class NeoForgeEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        BigBangEssentials.GameEvents.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        BigBangEssentials.GameEvents.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BigBangEssentials.GameEvents.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BigBangEssentials.GameEvents.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BigBangEssentials.GameEvents.onRegisterCommands(event.getDispatcher());
    }
}
