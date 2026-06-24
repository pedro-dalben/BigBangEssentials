package com.pedrodalben.bigbangessentials.fabric.impl;

import com.pedrodalben.bigbangessentials.fabric.accessor.FabricEntityDataAccessor;
import com.pedrodalben.bigbangessentials.util.PlatformProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.nio.file.Path;
import java.util.Collection;

public class FabricPlatformProvider implements PlatformProvider {
    private static MinecraftServer activeServer;

    public static void setServer(MinecraftServer server) {
        activeServer = server;
    }

    @Override
    public MinecraftServer getCurrentServer() {
        return activeServer;
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Collection<String> getLoadedMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(mod -> mod.getMetadata().getId())
                .toList();
    }

    @Override
    public CompoundTag getPersistentData(Entity entity) {
        return ((FabricEntityDataAccessor) entity).bbEssentials$getPersistentData();
    }
}
