package com.pedrodalben.bigbangessentials.fabric;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import com.pedrodalben.bigbangessentials.fabric.impl.FabricPlatformProvider;
import com.pedrodalben.bigbangessentials.fabric.impl.FabricTextDisplayMetadataFactory;
import com.pedrodalben.bigbangessentials.holograms.render.TextDisplayMetadata;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class FabricModEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        // Initialize Platform bridge
        Platform.init(new FabricPlatformProvider());

        // Install Fabric hologram metadata factory (relies on @Accessor mixin)
        TextDisplayMetadata.install(new FabricTextDisplayMetadataFactory());

        // Initialize common systems
        BigBangEssentials.init();

        // Register Fabric-specific events
        com.pedrodalben.bigbangessentials.fabric.listener.FabricEvents.register();

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
            com.pedrodalben.bigbangessentials.tablist.TablistEventHandler.onServerStop(server);
            FabricPlatformProvider.setServer(null);
        });

        // Register Command Registration event
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            BigBangEssentials.GameEvents.onRegisterCommands(dispatcher);
        });
    }
}
