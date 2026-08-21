package com.pedrodalben.bigbangessentials.forge;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import com.pedrodalben.bigbangessentials.forge.impl.ForgePlatformProvider;
import com.pedrodalben.bigbangessentials.forge.impl.ForgeTextDisplayMetadataFactory;
import com.pedrodalben.bigbangessentials.forge.listener.ForgeEvents;
import com.pedrodalben.bigbangessentials.holograms.render.TextDisplayMetadata;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("bigbangessentials")
public class ForgeModEntrypoint {

    public ForgeModEntrypoint() {
        this(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public ForgeModEntrypoint(IEventBus modEventBus) {
        // Initialize Platform bridge
        Platform.init(new ForgePlatformProvider());

        // Install Forge hologram metadata factory
        TextDisplayMetadata.install(new ForgeTextDisplayMetadataFactory());

        // Initialize common systems
        BigBangEssentials.init();

        // Register Forge event listeners on MinecraftForge event bus
        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);
    }
}
