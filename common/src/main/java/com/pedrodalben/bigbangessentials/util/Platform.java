package com.pedrodalben.bigbangessentials.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import java.nio.file.Path;
import java.util.Collection;

public class Platform {
    private static PlatformProvider provider;

    public static void init(PlatformProvider provider) {
        if (Platform.provider != null) {
            throw new IllegalStateException("Platform already initialized!");
        }
        Platform.provider = provider;
    }

    public static MinecraftServer getCurrentServer() {
        return provider == null ? null : provider.getCurrentServer();
    }

    public static Path getConfigDir() {
        return provider.getConfigDir();
    }

    public static Path getGameDir() {
        return provider.getGameDir();
    }

    public static boolean isModLoaded(String modId) {
        return provider != null && provider.isModLoaded(modId);
    }

    public static String getModName(String modId) {
        return provider.getModName(modId);
    }

    public static String getModVersion(String modId) {
        return provider.getModVersion(modId);
    }

    public static String getLoaderName() {
        return provider.getLoaderName();
    }

    public static String getLoaderVersion() {
        return provider.getLoaderVersion();
    }

    public static Collection<PlatformProvider.ModInfo> getMods() {
        return provider.getMods();
    }

    public static CompoundTag getPersistentData(Entity entity) {
        return provider.getPersistentData(entity);
    }

    public static void postEvent(Object event) {
        provider.postEvent(event);
    }

    public static void registerEventListener(Object listener) {
        provider.registerEventListener(listener);
    }
}
