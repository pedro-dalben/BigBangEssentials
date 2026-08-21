package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;

/**
 * Represents a milestone reward for reaching a certain number of crate openings.
 */
public class CrateMilestone {
    private final String id;
    private String name;
    private String description;
    private String rewardId; // Reward to give at this milestone
    private int requiredOpenings; // Number of openings needed
    private boolean repeatable; // Whether milestone repeats (every N openings)
    private boolean active;
    private int displayOrder;
    
    public CrateMilestone(String id, String name, String rewardId, int requiredOpenings) {
        this.id = validateId(id);
        this.name = name != null ? name : id;
        this.rewardId = validateId(rewardId);
        this.requiredOpenings = Math.max(1, requiredOpenings);
        this.description = "";
        this.repeatable = false;
        this.active = true;
        this.displayOrder = 0;
    }
    
    private String validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Milestone ID cannot be null or empty");
        }
        String normalized = id.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (!normalized.equals(id.toLowerCase())) {
            throw new IllegalArgumentException("Milestone ID can only contain lowercase letters, numbers, underscore, and hyphen: " + id);
        }
        return normalized;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRewardId() { return rewardId; }
    public int getRequiredOpenings() { return requiredOpenings; }
    public boolean isRepeatable() { return repeatable; }
    public boolean isActive() { return active; }
    public int getDisplayOrder() { return displayOrder; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setRewardId(String rewardId) { this.rewardId = validateId(rewardId); }
    public void setRequiredOpenings(int requiredOpenings) { this.requiredOpenings = Math.max(1, requiredOpenings); }
    public void setRepeatable(boolean repeatable) { this.repeatable = repeatable; }
    public void setActive(boolean active) { this.active = active; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public double getProgressPercent(int currentOpenings) {
        if (requiredOpenings <= 0) return 100.0;
        return Math.min(100.0, (currentOpenings * 100.0) / requiredOpenings);
    }

    public int getOpeningsRemaining(int currentOpenings) {
        return Math.max(0, requiredOpenings - currentOpenings);
    }

    public boolean isReached(int currentOpenings) {
        return currentOpenings >= requiredOpenings;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("description", description);
        json.addProperty("rewardId", rewardId);
        json.addProperty("requiredOpenings", requiredOpenings);
        json.addProperty("repeatable", repeatable);
        json.addProperty("active", active);
        json.addProperty("displayOrder", displayOrder);
        return json;
    }

    public static CrateMilestone fromJson(JsonObject json) {
        String id = json.get("id").getAsString();
        String name = json.has("name") ? json.get("name").getAsString() : id;
        String rewardId = json.has("rewardId") ? json.get("rewardId").getAsString() : "";
        int requiredOpenings = json.has("requiredOpenings") ? json.get("requiredOpenings").getAsInt() : 1;
        
        CrateMilestone milestone = new CrateMilestone(id, name, rewardId, requiredOpenings);
        
        if (json.has("description")) milestone.description = json.get("description").getAsString();
        if (json.has("repeatable")) milestone.repeatable = json.get("repeatable").getAsBoolean();
        if (json.has("active")) milestone.active = json.get("active").getAsBoolean();
        if (json.has("displayOrder")) milestone.displayOrder = json.get("displayOrder").getAsInt();
        
        return milestone;
    }
}