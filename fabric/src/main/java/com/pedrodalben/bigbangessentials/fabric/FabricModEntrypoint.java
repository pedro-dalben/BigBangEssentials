package com.pedrodalben.bigbangessentials.fabric;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import com.pedrodalben.bigbangessentials.fabric.impl.FabricPlatformProvider;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class FabricModEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        // Initialize Platform bridge
        Platform.init(new FabricPlatformProvider());

        // Initialize common systems
        BigBangEssentials.init();

        // Register Server Lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            FabricPlatformProvider.setServer(server);
            BigBangEssentials.GameEvents.onServerStarting(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BigBangEssentials.GameEvents.onServerStarted(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BigBangEssentials.GameEvents.onServerStopping(server);
            FabricPlatformProvider.setServer(null);
        });

        // Register Player Join event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            BigBangEssentials.GameEvents.onPlayerLoggedIn(handler.getPlayer());
        });

        // Register Command Registration event
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            BigBangEssentials.GameEvents.onRegisterCommands(dispatcher);
        });
    }
}
