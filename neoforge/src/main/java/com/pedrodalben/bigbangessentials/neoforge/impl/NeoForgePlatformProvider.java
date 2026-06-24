package com.pedrodalben.bigbangessentials.neoforge.impl;

import com.pedrodalben.bigbangessentials.util.PlatformProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.Collection;

public class NeoForgePlatformProvider implements PlatformProvider {

    @Override
    public MinecraftServer getCurrentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String getModName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(null);
    }

    @Override
    public String getModVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    @Override
    public String getLoaderName() {
        return "NeoForge";
    }

    @Override
    public String getLoaderVersion() {
        return getModVersion("neoforge");
    }

    @Override
    public Collection<ModInfo> getMods() {
        return ModList.get().getMods().stream()
                .map(info -> new ModInfo(info.getModId(), info.getDisplayName(), info.getVersion().toString()))
                .toList();
    }

    @Override
    public CompoundTag getPersistentData(Entity entity) {
        return entity.getPersistentData();
    }
}
