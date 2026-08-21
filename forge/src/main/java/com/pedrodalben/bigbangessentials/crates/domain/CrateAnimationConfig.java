package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

/**
 * Configuration for crate opening animation.
 */
public class CrateAnimationConfig {
    private boolean allowSkip;
    private int durationTicks; // Total animation duration in ticks
    private String startSound; // Sound when animation starts
    private String tickSound; // Sound played each tick (optional)
    private String endSound; // Sound when animation ends
    private String rewardSound; // Sound when reward is revealed
    private CrateParticleConfig particleConfig;
    private boolean showRollingItems; // For virtual opening
    private int rollingSpeed; // Ticks between item changes during roll
    private int highlightDurationTicks; // How long to highlight final reward
    
    public CrateAnimationConfig() {
        this.allowSkip = true;
        this.durationTicks = 60; // 3 seconds
        this.startSound = "minecraft:block.chest.open";
        this.tickSound = "";
        this.endSound = "minecraft:entity.player.levelup";
        this.rewardSound = "minecraft:entity.experience_orb.pickup";
        this.particleConfig = new CrateParticleConfig();
        this.showRollingItems = true;
        this.rollingSpeed = 2; // Change item every 2 ticks
        this.highlightDurationTicks = 40; // 2 seconds highlight
    }

    // Getters and Setters
    public boolean isAllowSkip() { return allowSkip; }
    public void setAllowSkip(boolean allowSkip) { this.allowSkip = allowSkip; }
    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int durationTicks) { this.durationTicks = Math.max(1, durationTicks); }
    public String getStartSound() { return startSound; }
    public void setStartSound(String startSound) { this.startSound = startSound; }
    public String getTickSound() { return tickSound; }
    public void setTickSound(String tickSound) { this.tickSound = tickSound; }
    public String getEndSound() { return endSound; }
    public void setEndSound(String endSound) { this.endSound = endSound; }
    public String getRewardSound() { return rewardSound; }
    public void setRewardSound(String rewardSound) { this.rewardSound = rewardSound; }
    public CrateParticleConfig getParticleConfig() { return particleConfig; }
    public void setParticleConfig(CrateParticleConfig particleConfig) { this.particleConfig = particleConfig; }
    public boolean isShowRollingItems() { return showRollingItems; }
    public void setShowRollingItems(boolean showRollingItems) { this.showRollingItems = showRollingItems; }
    public int getRollingSpeed() { return rollingSpeed; }
    public void setRollingSpeed(int rollingSpeed) { this.rollingSpeed = Math.max(1, rollingSpeed); }
    public int getHighlightDurationTicks() { return highlightDurationTicks; }
    public void setHighlightDurationTicks(int highlightDurationTicks) { this.highlightDurationTicks = Math.max(1, highlightDurationTicks); }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("allowSkip", allowSkip);
        json.addProperty("durationTicks", durationTicks);
        json.addProperty("startSound", startSound);
        json.addProperty("tickSound", tickSound);
        json.addProperty("endSound", endSound);
        json.addProperty("rewardSound", rewardSound);
        json.addProperty("showRollingItems", showRollingItems);
        json.addProperty("rollingSpeed", rollingSpeed);
        json.addProperty("highlightDurationTicks", highlightDurationTicks);
        json.add("particleConfig", particleConfig.toJson());
        return json;
    }

    public static CrateAnimationConfig fromJson(JsonObject json) {
        CrateAnimationConfig config = new CrateAnimationConfig();
        if (json.has("allowSkip")) config.allowSkip = json.get("allowSkip").getAsBoolean();
        if (json.has("durationTicks")) config.durationTicks = json.get("durationTicks").getAsInt();
        if (json.has("startSound")) config.startSound = json.get("startSound").getAsString();
        if (json.has("tickSound")) config.tickSound = json.get("tickSound").getAsString();
        if (json.has("endSound")) config.endSound = json.get("endSound").getAsString();
        if (json.has("rewardSound")) config.rewardSound = json.get("rewardSound").getAsString();
        if (json.has("showRollingItems")) config.showRollingItems = json.get("showRollingItems").getAsBoolean();
        if (json.has("rollingSpeed")) config.rollingSpeed = json.get("rollingSpeed").getAsInt();
        if (json.has("highlightDurationTicks")) config.highlightDurationTicks = json.get("highlightDurationTicks").getAsInt();
        if (json.has("particleConfig")) config.particleConfig = CrateParticleConfig.fromJson(json.getAsJsonObject("particleConfig"));
        return config;
    }
}