package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a crate definition with all its configuration.
 * This is the core entity for the crates system.
 */
public class CrateDefinition {
    private final UUID id;
    private final String key; // Technical ID: lowercase, numbers, underscore, hyphen
    private String displayName;
    private String description;
    private ItemStack displayItem;
    private List<String> lore;
    private boolean enabled;
    private CrateOpeningType openingType;
    private CratePreviewConfig previewConfig;
    private CrateAnimationConfig animationConfig;
    private CrateRequirements requirements;
    private long cooldownMillis;
    private double cost; // Economic cost (optional)
    private List<CrateRarity> rarities;
    private List<CrateReward> rewards;
    private List<CrateMilestone> milestones;
    private CrateVisualConfig visualConfig;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID lastEditedBy;
    private String lastEditReason;
    
    // Computed/cache fields
    private transient Map<String, CrateRarity> rarityById;
    private transient Map<String, CrateReward> rewardById;

    public CrateDefinition(UUID id, String key, String displayName) {
        this.id = id;
        this.key = validateKey(key);
        this.displayName = displayName != null ? displayName : key;
        this.description = "";
        this.enabled = true;
        this.openingType = CrateOpeningType.VIRTUAL;
        this.previewConfig = new CratePreviewConfig();
        this.animationConfig = new CrateAnimationConfig();
        this.requirements = new CrateRequirements();
        this.cooldownMillis = 0;
        this.cost = 0.0;
        this.rarities = new ArrayList<>();
        this.rewards = new ArrayList<>();
        this.milestones = new ArrayList<>();
        this.visualConfig = new CrateVisualConfig();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.lore = new ArrayList<>();
    }

    private String validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Crate key cannot be null or empty");
        }
        String normalized = key.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (!normalized.equals(key.toLowerCase())) {
            throw new IllegalArgumentException("Crate key can only contain lowercase letters, numbers, underscore, and hyphen: " + key);
        }
        return normalized;
    }

    // Getters
    public UUID getId() { return id; }
    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public ItemStack getDisplayItem() { return displayItem; }
    public List<String> getLore() { return new ArrayList<>(lore); }
    public boolean isEnabled() { return enabled; }
    public CrateOpeningType getOpeningType() { return openingType; }
    public CratePreviewConfig getPreviewConfig() { return previewConfig; }
    public CrateAnimationConfig getAnimationConfig() { return animationConfig; }
    public CrateRequirements getRequirements() { return requirements; }
    public long getCooldownMillis() { return cooldownMillis; }
    public double getCost() { return cost; }
    public List<CrateRarity> getRarities() { return new ArrayList<>(rarities); }
    public List<CrateReward> getRewards() { return new ArrayList<>(rewards); }
    public List<CrateMilestone> getMilestones() { return new ArrayList<>(milestones); }
    public CrateVisualConfig getVisualConfig() { return visualConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getLastEditedBy() { return lastEditedBy; }
    public String getLastEditReason() { return lastEditReason; }

    // Setters with timestamp update
    public void setDisplayName(String displayName) { this.displayName = displayName; touch(); }
    public void setDescription(String description) { this.description = description; touch(); }
    public void setDisplayItem(ItemStack displayItem) { this.displayItem = displayItem; touch(); }
    public void setLore(List<String> lore) { this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>(); touch(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; touch(); }
    public void setOpeningType(CrateOpeningType openingType) { this.openingType = openingType; touch(); }
    public void setPreviewConfig(CratePreviewConfig previewConfig) { this.previewConfig = previewConfig; touch(); }
    public void setAnimationConfig(CrateAnimationConfig animationConfig) { this.animationConfig = animationConfig; touch(); }
    public void setRequirements(CrateRequirements requirements) { this.requirements = requirements; touch(); }
    public void setCooldownMillis(long cooldownMillis) { this.cooldownMillis = Math.max(0, cooldownMillis); touch(); }
    public void setCost(double cost) { this.cost = Math.max(0, cost); touch(); }
    public void setRarities(List<CrateRarity> rarities) { this.rarities = rarities != null ? new ArrayList<>(rarities) : new ArrayList<>(); invalidateRarityCache(); touch(); }
    public void setRewards(List<CrateReward> rewards) { this.rewards = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>(); invalidateRewardCache(); touch(); }
    public void setMilestones(List<CrateMilestone> milestones) { this.milestones = milestones != null ? new ArrayList<>(milestones) : new ArrayList<>(); touch(); }
    public void setVisualConfig(CrateVisualConfig visualConfig) { this.visualConfig = visualConfig; touch(); }
    public void setLastEditedBy(UUID lastEditedBy) { this.lastEditedBy = lastEditedBy; touch(); }
    public void setLastEditReason(String lastEditReason) { this.lastEditReason = lastEditReason; touch(); }

    // Helper methods
    public CrateRarity getRarity(String rarityId) {
        if (rarityById == null) buildRarityCache();
        return rarityById.get(rarityId);
    }

    public CrateReward getReward(String rewardId) {
        if (rewardById == null) buildRewardCache();
        return rewardById.get(rewardId);
    }

    public List<CrateReward> getRewardsByRarity(String rarityId) {
        return rewards.stream()
            .filter(r -> rarityId.equals(r.getRarityId()))
            .toList();
    }

    public List<CrateReward> getEligibleRewards(Set<String> playerPermissions, Map<String, Integer> playerRewardCounts, Map<String, Integer> globalRewardCounts) {
        return rewards.stream()
            .filter(CrateReward::isActive)
            .filter(r -> {
                CrateRarity rarity = getRarity(r.getRarityId());
                return rarity != null && rarity.isActive();
            })
            .filter(r -> r.isEligible(playerPermissions, playerRewardCounts, globalRewardCounts))
            .toList();
    }

    public boolean hasValidRewards() {
        return rewards.stream().anyMatch(CrateReward::isActive);
    }

    public boolean hasRewardsForRarity(String rarityId) {
        return rewards.stream().anyMatch(r -> rarityId.equals(r.getRarityId()) && r.isActive());
    }

    public double calculateRarityChance(String rarityId) {
        CrateRarity rarity = getRarity(rarityId);
        if (rarity == null || !rarity.isActive()) return 0.0;
        
        double totalWeight = rarities.stream()
            .filter(CrateRarity::isActive)
            .filter(r -> hasRewardsForRarity(r.getId()))
            .mapToDouble(CrateRarity::getWeight)
            .sum();
        
        if (totalWeight <= 0) return 0.0;
        return (rarity.getWeight() / totalWeight) * 100.0;
    }

    public double calculateRewardChance(String rewardId) {
        CrateReward reward = getReward(rewardId);
        if (reward == null || !reward.isActive()) return 0.0;
        
        CrateRarity rarity = getRarity(reward.getRarityId());
        if (rarity == null || !rarity.isActive()) return 0.0;
        
        double rarityChance = calculateRarityChance(rarity.getId());
        if (rarityChance <= 0) return 0.0;
        
        double totalRewardWeight = getRewardsByRarity(rarity.getId()).stream()
            .filter(CrateReward::isActive)
            .mapToDouble(CrateReward::getWeight)
            .sum();
        
        if (totalRewardWeight <= 0) return 0.0;
        return (rarityChance / 100.0) * (reward.getWeight() / totalRewardWeight) * 100.0;
    }

    private void buildRarityCache() {
        rarityById = new ConcurrentHashMap<>();
        for (CrateRarity r : rarities) {
            rarityById.put(r.getId(), r);
        }
    }

    private void buildRewardCache() {
        rewardById = new ConcurrentHashMap<>();
        for (CrateReward r : rewards) {
            rewardById.put(r.getId(), r);
        }
    }

    private void invalidateRarityCache() { rarityById = null; }
    private void invalidateRewardCache() { rewardById = null; }

    private void touch() { this.updatedAt = Instant.now(); }

    // JSON Serialization
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("key", key);
        json.addProperty("displayName", displayName);
        json.addProperty("description", description);
        json.addProperty("enabled", enabled);
        json.addProperty("openingType", openingType.name());
        json.addProperty("cooldownMillis", cooldownMillis);
        json.addProperty("cost", cost);
        json.addProperty("createdAt", createdAt.toString());
        json.addProperty("updatedAt", updatedAt.toString());
        if (lastEditedBy != null) json.addProperty("lastEditedBy", lastEditedBy.toString());
        if (lastEditReason != null) json.addProperty("lastEditReason", lastEditReason);
        
        if (displayItem != null && !displayItem.isEmpty()) {
            json.add("displayItem", ItemSerializer.serialize(displayItem));
        }
        
        JsonArray loreArray = new JsonArray();
        for (String line : lore) loreArray.add(line);
        json.add("lore", loreArray);
        
        json.add("previewConfig", previewConfig.toJson());
        json.add("animationConfig", animationConfig.toJson());
        json.add("requirements", requirements.toJson());
        json.add("visualConfig", visualConfig.toJson());
        
        JsonArray raritiesArray = new JsonArray();
        for (CrateRarity r : rarities) raritiesArray.add(r.toJson());
        json.add("rarities", raritiesArray);
        
        JsonArray rewardsArray = new JsonArray();
        for (CrateReward r : rewards) rewardsArray.add(r.toJson());
        json.add("rewards", rewardsArray);
        
        JsonArray milestonesArray = new JsonArray();
        for (CrateMilestone m : milestones) milestonesArray.add(m.toJson());
        json.add("milestones", milestonesArray);
        
        return json;
    }

    public static CrateDefinition fromJson(JsonObject json) {
        UUID id = UUID.fromString(json.get("id").getAsString());
        String key = json.get("key").getAsString();
        String displayName = json.has("displayName") ? json.get("displayName").getAsString() : key;
        
        CrateDefinition crate = new CrateDefinition(id, key, displayName);
        
        if (json.has("description")) crate.description = json.get("description").getAsString();
        if (json.has("enabled")) crate.enabled = json.get("enabled").getAsBoolean();
        if (json.has("openingType")) crate.openingType = CrateOpeningType.valueOf(json.get("openingType").getAsString());
        if (json.has("cooldownMillis")) crate.cooldownMillis = json.get("cooldownMillis").getAsLong();
        if (json.has("cost")) crate.cost = json.get("cost").getAsDouble();
        if (json.has("createdAt")) crate.createdAt = Instant.parse(json.get("createdAt").getAsString());
        if (json.has("updatedAt")) crate.updatedAt = Instant.parse(json.get("updatedAt").getAsString());
        if (json.has("lastEditedBy")) crate.lastEditedBy = UUID.fromString(json.get("lastEditedBy").getAsString());
        if (json.has("lastEditReason")) crate.lastEditReason = json.get("lastEditReason").getAsString();
        
        if (json.has("displayItem")) {
            crate.displayItem = ItemSerializer.deserialize(json.get("displayItem").getAsJsonObject());
        }
        
        if (json.has("lore")) {
            JsonArray loreArray = json.getAsJsonArray("lore");
            crate.lore = new ArrayList<>();
            for (JsonElement e : loreArray) crate.lore.add(e.getAsString());
        }
        
        if (json.has("previewConfig")) crate.previewConfig = CratePreviewConfig.fromJson(json.getAsJsonObject("previewConfig"));
        if (json.has("animationConfig")) crate.animationConfig = CrateAnimationConfig.fromJson(json.getAsJsonObject("animationConfig"));
        if (json.has("requirements")) crate.requirements = CrateRequirements.fromJson(json.getAsJsonObject("requirements"));
        if (json.has("visualConfig")) crate.visualConfig = CrateVisualConfig.fromJson(json.getAsJsonObject("visualConfig"));
        
        if (json.has("rarities")) {
            JsonArray raritiesArray = json.getAsJsonArray("rarities");
            crate.rarities = new ArrayList<>();
            for (JsonElement e : raritiesArray) crate.rarities.add(CrateRarity.fromJson(e.getAsJsonObject()));
        }
        
        if (json.has("rewards")) {
            JsonArray rewardsArray = json.getAsJsonArray("rewards");
            crate.rewards = new ArrayList<>();
            for (JsonElement e : rewardsArray) crate.rewards.add(CrateReward.fromJson(e.getAsJsonObject()));
        }
        
        if (json.has("milestones")) {
            JsonArray milestonesArray = json.getAsJsonArray("milestones");
            crate.milestones = new ArrayList<>();
            for (JsonElement e : milestonesArray) crate.milestones.add(CrateMilestone.fromJson(e.getAsJsonObject()));
        }
        
        return crate;
    }
}