package com.pedrodalben.bigbangessentials.neoforge;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import com.pedrodalben.bigbangessentials.holograms.render.TextDisplayMetadata;
import com.pedrodalben.bigbangessentials.neoforge.impl.NeoForgePlatformProvider;
import com.pedrodalben.bigbangessentials.neoforge.impl.NeoForgeTextDisplayMetadataFactory;
import com.pedrodalben.bigbangessentials.neoforge.listener.NeoForgeEvents;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod("bigbangessentials")
public class NeoForgeModEntrypoint {

    public NeoForgeModEntrypoint(IEventBus modEventBus) {
        // Initialize Platform bridge
        Platform.init(new NeoForgePlatformProvider());

        // Install NeoForge hologram metadata factory
        TextDisplayMetadata.install(new NeoForgeTextDisplayMetadataFactory());

        // Initialize common systems
        BigBangEssentials.init();

        // Register NeoForge event listeners
        NeoForge.EVENT_BUS.register(NeoForgeEvents.class);
    }
}
