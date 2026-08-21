package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

/**
 * Configuration for particle effects during crate animation.
 */
public class CrateParticleConfig {
    private String particleType;
    private ParticleShape shape; // CIRCLE, SPIRAL, COLUMN, AURA, NONE
    private int frequencyTicks; // Particles every N ticks
    private int particleCount; // Particles per spawn
    private double radius; // Radius for circle/spiral
    private double height; // Height offset
    private double speed; // Particle speed
    private int maxDistance; // Max render distance
    private boolean onlyNearbyPlayers; // Only spawn when players nearby
    
    public CrateParticleConfig() {
        this.particleType = "minecraft:enchant";
        this.shape = ParticleShape.CIRCLE;
        this.frequencyTicks = 1;
        this.particleCount = 5;
        this.radius = 1.0;
        this.height = 1.5;
        this.speed = 0.1;
        this.maxDistance = 32;
        this.onlyNearbyPlayers = true;
    }

    // Getters and Setters
    public String getParticleType() { return particleType; }
    public void setParticleType(String particleType) { this.particleType = particleType; }
    public ParticleShape getShape() { return shape; }
    public void setShape(ParticleShape shape) { this.shape = shape; }
    public int getFrequencyTicks() { return frequencyTicks; }
    public void setFrequencyTicks(int frequencyTicks) { this.frequencyTicks = Math.max(1, frequencyTicks); }
    public int getParticleCount() { return particleCount; }
    public void setParticleCount(int particleCount) { this.particleCount = Math.max(1, particleCount); }
    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = Math.max(0, radius); }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = Math.max(0, speed); }
    public int getMaxDistance() { return maxDistance; }
    public void setMaxDistance(int maxDistance) { this.maxDistance = Math.max(1, maxDistance); }
    public boolean isOnlyNearbyPlayers() { return onlyNearbyPlayers; }
    public void setOnlyNearbyPlayers(boolean onlyNearbyPlayers) { this.onlyNearbyPlayers = onlyNearbyPlayers; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("particleType", particleType);
        json.addProperty("shape", shape.name());
        json.addProperty("frequencyTicks", frequencyTicks);
        json.addProperty("particleCount", particleCount);
        json.addProperty("radius", radius);
        json.addProperty("height", height);
        json.addProperty("speed", speed);
        json.addProperty("maxDistance", maxDistance);
        json.addProperty("onlyNearbyPlayers", onlyNearbyPlayers);
        return json;
    }

    public static CrateParticleConfig fromJson(JsonObject json) {
        CrateParticleConfig config = new CrateParticleConfig();
        if (json.has("particleType")) config.particleType = json.get("particleType").getAsString();
        if (json.has("shape")) config.shape = ParticleShape.valueOf(json.get("shape").getAsString());
        if (json.has("frequencyTicks")) config.frequencyTicks = json.get("frequencyTicks").getAsInt();
        if (json.has("particleCount")) config.particleCount = json.get("particleCount").getAsInt();
        if (json.has("radius")) config.radius = json.get("radius").getAsDouble();
        if (json.has("height")) config.height = json.get("height").getAsDouble();
        if (json.has("speed")) config.speed = json.get("speed").getAsDouble();
        if (json.has("maxDistance")) config.maxDistance = json.get("maxDistance").getAsInt();
        if (json.has("onlyNearbyPlayers")) config.onlyNearbyPlayers = json.get("onlyNearbyPlayers").getAsBoolean();
        return config;
    }
}