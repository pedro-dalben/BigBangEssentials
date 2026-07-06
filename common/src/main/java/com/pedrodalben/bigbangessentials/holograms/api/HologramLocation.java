package com.pedrodalben.bigbangessentials.holograms.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record HologramLocation(ResourceKey<Level> dimension, double x, double y, double z) {
    public HologramLocation {
        if (dimension == null) {
            throw new IllegalArgumentException("Hologram dimension cannot be null");
        }
    }

    public ResourceLocation dimensionId() {
        return dimension.location();
    }
}
