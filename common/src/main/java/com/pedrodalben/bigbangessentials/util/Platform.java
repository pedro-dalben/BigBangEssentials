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
        return provider.getCurrentServer();
    }

    public static Path getConfigDir() {
        return provider.getConfigDir();
    }

    public static Path getGameDir() {
        return provider.getGameDir();
    }

    public static boolean isModLoaded(String modId) {
        return provider.isModLoaded(modId);
    }

    public static Collection<String> getLoadedMods() {
        return provider.getLoadedMods();
    }

    public static CompoundTag getPersistentData(Entity entity) {
        return provider.getPersistentData(entity);
    }
}
