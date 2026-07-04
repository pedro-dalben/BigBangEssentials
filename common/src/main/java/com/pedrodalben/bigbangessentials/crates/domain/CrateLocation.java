package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.time.Instant;
import java.util.UUID;

public class CrateLocation {
    private final UUID id;
    private final String crateId;
    private final ResourceKey<Level> dimension;
    private final BlockPos position;
    private String hologramTemplate;
    private double hologramOffsetY;
    private boolean hologramEnabled;
    private boolean particleEnabled;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public CrateLocation(UUID id, String crateId, ResourceKey<Level> dimension, BlockPos position) {
        this.id = id != null ? id : UUID.randomUUID();
        this.crateId = crateId;
        this.dimension = dimension;
        this.position = position;
        this.hologramEnabled = true;
        this.hologramOffsetY = 1.0;
        this.particleEnabled = true;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCrateId() { return crateId; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public BlockPos getPosition() { return position; }
    public String getHologramTemplate() { return hologramTemplate; }
    public double getHologramOffsetY() { return hologramOffsetY; }
    public boolean isHologramEnabled() { return hologramEnabled; }
    public boolean isParticleEnabled() { return particleEnabled; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setHologramTemplate(String template) { this.hologramTemplate = template; touch(); }
    public void setHologramOffsetY(double offset) { this.hologramOffsetY = offset; touch(); }
    public void setHologramEnabled(boolean enabled) { this.hologramEnabled = enabled; touch(); }
    public void setParticleEnabled(boolean enabled) { this.particleEnabled = enabled; touch(); }
    public void setActive(boolean active) { this.active = active; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }

    public String getWorldName() {
        return dimension.location().toString();
    }

    public int getX() { return position.getX(); }
    public int getY() { return position.getY(); }
    public int getZ() { return position.getZ(); }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("crateId", crateId);
        json.addProperty("world", dimension.location().toString());
        json.addProperty("x", position.getX());
        json.addProperty("y", position.getY());
        json.addProperty("z", position.getZ());
        json.addProperty("hologramTemplate", hologramTemplate);
        json.addProperty("hologramOffsetY", hologramOffsetY);
        json.addProperty("hologramEnabled", hologramEnabled);
        json.addProperty("particleEnabled", particleEnabled);
        json.addProperty("active", active);
        json.addProperty("createdAt", createdAt.toString());
        json.addProperty("updatedAt", updatedAt.toString());
        return json;
    }

    public static CrateLocation fromJson(JsonObject json) {
        UUID id = UUID.fromString(json.get("id").getAsString());
        String crateId = json.get("crateId").getAsString();
        ResourceKey<Level> dimension = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse(json.get("world").getAsString())
        );
        BlockPos pos = new BlockPos(
            json.get("x").getAsInt(),
            json.get("y").getAsInt(),
            json.get("z").getAsInt()
        );

        CrateLocation loc = new CrateLocation(id, crateId, dimension, pos);

        if (json.has("hologramTemplate")) loc.hologramTemplate = json.get("hologramTemplate").getAsString();
        if (json.has("hologramOffsetY")) loc.hologramOffsetY = json.get("hologramOffsetY").getAsDouble();
        if (json.has("hologramEnabled")) loc.hologramEnabled = json.get("hologramEnabled").getAsBoolean();
        if (json.has("particleEnabled")) loc.particleEnabled = json.get("particleEnabled").getAsBoolean();
        if (json.has("active")) loc.active = json.get("active").getAsBoolean();

        return loc;
    }
}
