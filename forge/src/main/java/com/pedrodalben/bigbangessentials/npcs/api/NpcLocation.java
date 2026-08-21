package com.pedrodalben.bigbangessentials.npcs.api;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public final class NpcLocation {
    private final ResourceLocation dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public NpcLocation(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public ResourceLocation dimension() { return dimension; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public NpcLocation withYawPitch(float yaw, float pitch) {
        return new NpcLocation(dimension, x, y, z, yaw, pitch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NpcLocation that)) return false;
        return Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0
            && Double.compare(that.z, z) == 0 && Float.compare(that.yaw, yaw) == 0
            && Float.compare(that.pitch, pitch) == 0 && dimension.equals(that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, x, y, z, yaw, pitch);
    }
}
