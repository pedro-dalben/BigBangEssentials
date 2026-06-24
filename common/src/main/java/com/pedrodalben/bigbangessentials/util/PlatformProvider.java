package com.pedrodalben.bigbangessentials.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import java.nio.file.Path;
import java.util.Collection;

public interface PlatformProvider {
    MinecraftServer getCurrentServer();
    Path getConfigDir();
    Path getGameDir();
    boolean isModLoaded(String modId);
    String getModName(String modId);
    String getModVersion(String modId);
    String getLoaderName();
    String getLoaderVersion();
    Collection<ModInfo> getMods();
    CompoundTag getPersistentData(Entity entity);

    record ModInfo(String id, String name, String version) {}
}
