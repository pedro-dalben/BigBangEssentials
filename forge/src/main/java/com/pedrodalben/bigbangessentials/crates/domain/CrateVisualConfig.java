package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Visual configuration for crate (holograms, particles at block location).
 */
public class CrateVisualConfig {
    private boolean hologramEnabled;
    private String hologramTemplate; // Template with placeholders
    private List<String> hologramLines; // Individual lines
    private double hologramOffsetY; // Vertical offset from block
    private int hologramUpdateIntervalTicks; // How often to update (0 = static)
    private int hologramViewDistance; // Max distance to show
    private CrateParticleConfig idleParticleConfig; // Particles when crate is idle
    private CrateParticleConfig openParticleConfig; // Particles during opening
    private String approachSound; // Sound when player approaches
    private String openSound; // Sound when crate is opened
    private int approachSoundRadius; // Radius to play approach sound
    
    public CrateVisualConfig() {
        this.hologramEnabled = true;
        this.hologramTemplate = "";
        this.hologramLines = Arrays.asList(
            "§6§l{name}",
            "§7{description}",
            "§7Clique para abrir"
        );
        this.hologramOffsetY = 1.0;
        this.hologramUpdateIntervalTicks = 20; // Update every second
        this.hologramViewDistance = 16;
        this.idleParticleConfig = new CrateParticleConfig();
        this.idleParticleConfig.setShape(ParticleShape.AURA);
        this.idleParticleConfig.setFrequencyTicks(10);
        this.idleParticleConfig.setParticleCount(3);
        this.openParticleConfig = new CrateParticleConfig();
        this.approachSound = "minecraft:block.note_block.harp";
        this.openSound = "minecraft:block.chest.open";
        this.approachSoundRadius = 8;
    }

    // Getters and Setters
    public boolean isHologramEnabled() { return hologramEnabled; }
    public void setHologramEnabled(boolean hologramEnabled) { this.hologramEnabled = hologramEnabled; }
    public String getHologramTemplate() { return hologramTemplate; }
    public void setHologramTemplate(String hologramTemplate) { this.hologramTemplate = hologramTemplate; }
    public List<String> getHologramLines() { return new ArrayList<>(hologramLines); }
    public void setHologramLines(List<String> hologramLines) { this.hologramLines = hologramLines != null ? new ArrayList<>(hologramLines) : new ArrayList<>(); }
    public double getHologramOffsetY() { return hologramOffsetY; }
    public void setHologramOffsetY(double hologramOffsetY) { this.hologramOffsetY = hologramOffsetY; }
    public int getHologramUpdateIntervalTicks() { return hologramUpdateIntervalTicks; }
    public void setHologramUpdateIntervalTicks(int hologramUpdateIntervalTicks) { this.hologramUpdateIntervalTicks = Math.max(0, hologramUpdateIntervalTicks); }
    public int getHologramViewDistance() { return hologramViewDistance; }
    public void setHologramViewDistance(int hologramViewDistance) { this.hologramViewDistance = Math.max(1, hologramViewDistance); }
    public CrateParticleConfig getIdleParticleConfig() { return idleParticleConfig; }
    public void setIdleParticleConfig(CrateParticleConfig idleParticleConfig) { this.idleParticleConfig = idleParticleConfig; }
    public CrateParticleConfig getOpenParticleConfig() { return openParticleConfig; }
    public void setOpenParticleConfig(CrateParticleConfig openParticleConfig) { this.openParticleConfig = openParticleConfig; }
    public String getApproachSound() { return approachSound; }
    public void setApproachSound(String approachSound) { this.approachSound = approachSound; }
    public String getOpenSound() { return openSound; }
    public void setOpenSound(String openSound) { this.openSound = openSound; }
    public int getApproachSoundRadius() { return approachSoundRadius; }
    public void setApproachSoundRadius(int approachSoundRadius) { this.approachSoundRadius = Math.max(1, approachSoundRadius); }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("hologramEnabled", hologramEnabled);
        json.addProperty("hologramTemplate", hologramTemplate);
        json.addProperty("hologramOffsetY", hologramOffsetY);
        json.addProperty("hologramUpdateIntervalTicks", hologramUpdateIntervalTicks);
        json.addProperty("hologramViewDistance", hologramViewDistance);
        json.addProperty("approachSound", approachSound);
        json.addProperty("openSound", openSound);
        json.addProperty("approachSoundRadius", approachSoundRadius);
        
        JsonArray linesArray = new JsonArray();
        for (String line : hologramLines) linesArray.add(line);
        json.add("hologramLines", linesArray);
        
        json.add("idleParticleConfig", idleParticleConfig.toJson());
        json.add("openParticleConfig", openParticleConfig.toJson());
        
        return json;
    }

    public static CrateVisualConfig fromJson(JsonObject json) {
        CrateVisualConfig config = new CrateVisualConfig();
        if (json.has("hologramEnabled")) config.hologramEnabled = json.get("hologramEnabled").getAsBoolean();
        if (json.has("hologramTemplate")) config.hologramTemplate = json.get("hologramTemplate").getAsString();
        if (json.has("hologramOffsetY")) config.hologramOffsetY = json.get("hologramOffsetY").getAsDouble();
        if (json.has("hologramUpdateIntervalTicks")) config.hologramUpdateIntervalTicks = json.get("hologramUpdateIntervalTicks").getAsInt();
        if (json.has("hologramViewDistance")) config.hologramViewDistance = json.get("hologramViewDistance").getAsInt();
        if (json.has("approachSound")) config.approachSound = json.get("approachSound").getAsString();
        if (json.has("openSound")) config.openSound = json.get("openSound").getAsString();
        if (json.has("approachSoundRadius")) config.approachSoundRadius = json.get("approachSoundRadius").getAsInt();
        if (json.has("hologramLines")) {
            JsonArray linesArray = json.getAsJsonArray("hologramLines");
            config.hologramLines = new ArrayList<>();
            for (JsonElement e : linesArray) config.hologramLines.add(e.getAsString());
        }
        if (json.has("idleParticleConfig")) config.idleParticleConfig = CrateParticleConfig.fromJson(json.getAsJsonObject("idleParticleConfig"));
        if (json.has("openParticleConfig")) config.openParticleConfig = CrateParticleConfig.fromJson(json.getAsJsonObject("openParticleConfig"));
        return config;
    }
}