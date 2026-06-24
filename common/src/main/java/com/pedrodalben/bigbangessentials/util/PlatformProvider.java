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
    Collection<String> getLoadedMods();
    CompoundTag getPersistentData(Entity entity);
}
