package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.*;

/**
 * Requirements for opening a crate.
 * Supports complex logic: AND/OR combinations of keys, permissions, and costs.
 */
public class CrateRequirements {
    private List<String> acceptedKeyIds; // Key IDs that can open this crate
    private boolean requirePhysicalKey; // At least one physical key
    private boolean requireVirtualKey; // At least one virtual key
    private String requiredPermission; // Permission node required
    private double requiredCost; // Economic cost (uses EconomyService)
    private long cooldownMillis; // Cooldown between openings
    private boolean oneTimeUse; // If true, crate can only be opened once per player (cooldown = infinite)
    private RequirementLogic logic; // AND or OR for combining requirements
    private List<AlternativeCost> alternativeCosts; // Alternative ways to pay
    
    public CrateRequirements() {
        this.acceptedKeyIds = new ArrayList<>();
        this.requirePhysicalKey = false;
        this.requireVirtualKey = false;
        this.requiredPermission = "";
        this.requiredCost = 0.0;
        this.cooldownMillis = 0;
        this.oneTimeUse = false;
        this.logic = RequirementLogic.AND;
        this.alternativeCosts = new ArrayList<>();
    }

    // Getters and Setters
    public List<String> getAcceptedKeyIds() { return new ArrayList<>(acceptedKeyIds); }
    public void setAcceptedKeyIds(List<String> acceptedKeyIds) { this.acceptedKeyIds = acceptedKeyIds != null ? new ArrayList<>(acceptedKeyIds) : new ArrayList<>(); }
    public void addAcceptedKeyId(String keyId) { if (keyId != null && !acceptedKeyIds.contains(keyId)) acceptedKeyIds.add(keyId); }
    public void removeAcceptedKeyId(String keyId) { acceptedKeyIds.remove(keyId); }
    public boolean isRequirePhysicalKey() { return requirePhysicalKey; }
    public void setRequirePhysicalKey(boolean requirePhysicalKey) { this.requirePhysicalKey = requirePhysicalKey; }
    public boolean isRequireVirtualKey() { return requireVirtualKey; }
    public void setRequireVirtualKey(boolean requireVirtualKey) { this.requireVirtualKey = requireVirtualKey; }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission; }
    public double getRequiredCost() { return requiredCost; }
    public void setRequiredCost(double requiredCost) { this.requiredCost = Math.max(0, requiredCost); }
    public long getCooldownMillis() { return cooldownMillis; }
    public void setCooldownMillis(long cooldownMillis) { this.cooldownMillis = Math.max(0, cooldownMillis); }
    public boolean isOneTimeUse() { return oneTimeUse; }
    public void setOneTimeUse(boolean oneTimeUse) { this.oneTimeUse = oneTimeUse; }
    public RequirementLogic getLogic() { return logic; }
    public void setLogic(RequirementLogic logic) { this.logic = logic; }
    public List<AlternativeCost> getAlternativeCosts() { return new ArrayList<>(alternativeCosts); }
    public void setAlternativeCosts(List<AlternativeCost> alternativeCosts) { this.alternativeCosts = alternativeCosts != null ? new ArrayList<>(alternativeCosts) : new ArrayList<>(); }
    public void addAlternativeCost(AlternativeCost cost) { if (cost != null) alternativeCosts.add(cost); }

    public boolean hasKeyRequirement() { return !acceptedKeyIds.isEmpty(); }
    public boolean hasPermissionRequirement() { return requiredPermission != null && !requiredPermission.isBlank(); }
    public boolean hasCostRequirement() { return requiredCost > 0; }
    public boolean hasCooldown() { return cooldownMillis > 0 || oneTimeUse; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        
        JsonArray keysArray = new JsonArray();
        for (String k : acceptedKeyIds) keysArray.add(k);
        json.add("acceptedKeyIds", keysArray);
        
        json.addProperty("requirePhysicalKey", requirePhysicalKey);
        json.addProperty("requireVirtualKey", requireVirtualKey);
        json.addProperty("requiredPermission", requiredPermission);
        json.addProperty("requiredCost", requiredCost);
        json.addProperty("cooldownMillis", cooldownMillis);
        json.addProperty("oneTimeUse", oneTimeUse);
        json.addProperty("logic", logic.name());
        
        JsonArray altCostsArray = new JsonArray();
        for (AlternativeCost c : alternativeCosts) altCostsArray.add(c.toJson());
        json.add("alternativeCosts", altCostsArray);
        
        return json;
    }

    public static CrateRequirements fromJson(JsonObject json) {
        CrateRequirements req = new CrateRequirements();
        
        if (json.has("acceptedKeyIds")) {
            JsonArray keysArray = json.getAsJsonArray("acceptedKeyIds");
            for (JsonElement e : keysArray) req.acceptedKeyIds.add(e.getAsString());
        }
        if (json.has("requirePhysicalKey")) req.requirePhysicalKey = json.get("requirePhysicalKey").getAsBoolean();
        if (json.has("requireVirtualKey")) req.requireVirtualKey = json.get("requireVirtualKey").getAsBoolean();
        if (json.has("requiredPermission")) req.requiredPermission = json.get("requiredPermission").getAsString();
        if (json.has("requiredCost")) req.requiredCost = json.get("requiredCost").getAsDouble();
        if (json.has("cooldownMillis")) req.cooldownMillis = json.get("cooldownMillis").getAsLong();
        if (json.has("oneTimeUse")) req.oneTimeUse = json.get("oneTimeUse").getAsBoolean();
        if (json.has("logic")) req.logic = RequirementLogic.valueOf(json.get("logic").getAsString());
        if (json.has("alternativeCosts")) {
            JsonArray altArray = json.getAsJsonArray("alternativeCosts");
            for (JsonElement e : altArray) req.alternativeCosts.add(AlternativeCost.fromJson(e.getAsJsonObject()));
        }
        return req;
    }
}

/**
 * Logic for combining multiple requirements.
 */
enum RequirementLogic {
    AND, // All requirements must be met
    OR   // At least one requirement must be met
}

/**
 * Alternative cost option (e.g., Key OR Money).
 */
class AlternativeCost {
    private String type; // "KEY", "PERMISSION", "ECONOMY"
    private String value; // keyId, permission node, or amount
    private String description;
    
    public AlternativeCost() {}
    
    public AlternativeCost(String type, String value, String description) {
        this.type = type;
        this.value = value;
        this.description = description;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("value", value);
        json.addProperty("description", description);
        return json;
    }

    public static AlternativeCost fromJson(JsonObject json) {
        AlternativeCost cost = new AlternativeCost();
        if (json.has("type")) cost.type = json.get("type").getAsString();
        if (json.has("value")) cost.value = json.get("value").getAsString();
        if (json.has("description")) cost.description = json.get("description").getAsString();
        return cost;
    }
}