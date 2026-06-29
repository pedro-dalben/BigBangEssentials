package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Represents a reward that can be won from a crate.
 */
public class CrateReward {
    private final String id;
    private String name;
    private String crateId; // Parent crate ID
    private ItemStack icon; // Display icon in preview
    private List<String> lore; // Description in preview
    private RewardType type; // ITEM or COMMAND
    private String rarityId; // Rarity tier
    private double weight; // Weight within rarity
    private List<ItemStack> items; // Items to give (for ITEM type)
    private List<String> commands; // Commands to execute (for COMMAND type)
    private String requiredPermission; // Permission required to receive
    private List<String> blockingPermissions; // Permissions that block this reward
    private int globalLimit; // Max times this reward can be given globally (-1 = unlimited)
    private int playerLimit; // Max times a single player can receive (-1 = unlimited)
    private boolean broadcast; // Whether to broadcast to server
    private String broadcastMessage; // Custom broadcast message
    private String playerMessage; // Custom message to player
    private boolean active; // Whether reward is active
    private boolean visibleInPreview; // Show in preview
    private boolean milestoneOnly; // Only available as milestone reward
    private int displayOrder; // Order in editor/preview
    
    public CrateReward(String id, String crateId, String name, RewardType type, String rarityId) {
        this.id = validateId(id);
        this.crateId = crateId;
        this.name = name != null ? name : id;
        this.type = type != null ? type : RewardType.ITEM;
        this.rarityId = validateRarityId(rarityId);
        this.weight = 1.0;
        this.items = new ArrayList<>();
        this.commands = new ArrayList<>();
        this.requiredPermission = "";
        this.blockingPermissions = new ArrayList<>();
        this.globalLimit = -1;
        this.playerLimit = -1;
        this.broadcast = false;
        this.broadcastMessage = "";
        this.playerMessage = "";
        this.active = true;
        this.visibleInPreview = true;
        this.milestoneOnly = false;
        this.displayOrder = 0;
        this.lore = new ArrayList<>();
        this.icon = createDefaultIcon();
    }
    
    private String validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Reward ID cannot be null or empty");
        }
        String normalized = id.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (!normalized.equals(id.toLowerCase())) {
            throw new IllegalArgumentException("Reward ID can only contain lowercase letters, numbers, underscore, and hyphen: " + id);
        }
        return normalized;
    }
    
    private String validateRarityId(String rarityId) {
        if (rarityId == null || rarityId.isBlank()) {
            throw new IllegalArgumentException("Rarity ID cannot be null or empty");
        }
        return rarityId.toLowerCase();
    }
    
    private ItemStack createDefaultIcon() {
        // Return a paper as default icon
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse("minecraft:paper")).orElse(null);
        if (item == null) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getCrateId() { return crateId; }
    public ItemStack getIcon() { return icon; }
    public List<String> getLore() { return new ArrayList<>(lore); }
    public RewardType getType() { return type; }
    public String getRarityId() { return rarityId; }
    public double getWeight() { return weight; }
    public List<ItemStack> getItems() { return new ArrayList<>(items); }
    public List<String> getCommands() { return new ArrayList<>(commands); }
    public String getRequiredPermission() { return requiredPermission; }
    public List<String> getBlockingPermissions() { return new ArrayList<>(blockingPermissions); }
    public int getGlobalLimit() { return globalLimit; }
    public int getPlayerLimit() { return playerLimit; }
    public boolean isBroadcast() { return broadcast; }
    public String getBroadcastMessage() { return broadcastMessage; }
    public String getPlayerMessage() { return playerMessage; }
    public boolean isActive() { return active; }
    public boolean isVisibleInPreview() { return visibleInPreview; }
    public boolean isMilestoneOnly() { return milestoneOnly; }
    public int getDisplayOrder() { return displayOrder; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setIcon(ItemStack icon) { this.icon = icon != null ? icon : createDefaultIcon(); }
    public void setLore(List<String> lore) { this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>(); }
    public void setType(RewardType type) { this.type = type; }
    public void setRarityId(String rarityId) { this.rarityId = validateRarityId(rarityId); }
    public void setWeight(double weight) { this.weight = Math.max(0, weight); }
    public void setItems(List<ItemStack> items) { this.items = items != null ? new ArrayList<>(items) : new ArrayList<>(); }
    public void setCommands(List<String> commands) { this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>(); }
    public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission != null ? requiredPermission : ""; }
    public void setBlockingPermissions(List<String> blockingPermissions) { this.blockingPermissions = blockingPermissions != null ? new ArrayList<>(blockingPermissions) : new ArrayList<>(); }
    public void setGlobalLimit(int globalLimit) { this.globalLimit = globalLimit; }
    public void setPlayerLimit(int playerLimit) { this.playerLimit = playerLimit; }
    public void setBroadcast(boolean broadcast) { this.broadcast = broadcast; }
    public void setBroadcastMessage(String broadcastMessage) { this.broadcastMessage = broadcastMessage; }
    public void setPlayerMessage(String playerMessage) { this.playerMessage = playerMessage; }
    public void setActive(boolean active) { this.active = active; }
    public void setVisibleInPreview(boolean visibleInPreview) { this.visibleInPreview = visibleInPreview; }
    public void setMilestoneOnly(boolean milestoneOnly) { this.milestoneOnly = milestoneOnly; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    /**
     * Checks if this reward is eligible for the given player/context.
     */
    public boolean isEligible(Set<String> playerPermissions, Map<String, Integer> playerRewardCounts, Map<String, Integer> globalRewardCounts) {
        if (!active) return false;
        
        // Check required permission
        if (requiredPermission != null && !requiredPermission.isBlank()) {
            if (!playerPermissions.contains(requiredPermission)) return false;
        }
        
        // Check blocking permissions
        for (String perm : blockingPermissions) {
            if (playerPermissions.contains(perm)) return false;
        }
        
        // Check global limit
        if (globalLimit > 0) {
            int globalGiven = globalRewardCounts.getOrDefault(id, 0);
            if (globalGiven >= globalLimit) return false;
        }
        
        // Check player limit
        if (playerLimit > 0) {
            int playerGiven = playerRewardCounts.getOrDefault(id, 0);
            if (playerGiven >= playerLimit) return false;
        }
        
        return true;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("crateId", crateId);
        json.addProperty("type", type.name());
        json.addProperty("rarityId", rarityId);
        json.addProperty("weight", weight);
        json.addProperty("requiredPermission", requiredPermission);
        json.addProperty("globalLimit", globalLimit);
        json.addProperty("playerLimit", playerLimit);
        json.addProperty("broadcast", broadcast);
        json.addProperty("broadcastMessage", broadcastMessage);
        json.addProperty("playerMessage", playerMessage);
        json.addProperty("active", active);
        json.addProperty("visibleInPreview", visibleInPreview);
        json.addProperty("milestoneOnly", milestoneOnly);
        json.addProperty("displayOrder", displayOrder);
        
        if (icon != null && !icon.isEmpty()) {
            json.add("icon", ItemSerializer.serialize(icon));
        }
        
        JsonArray loreArray = new JsonArray();
        for (String line : lore) loreArray.add(line);
        json.add("lore", loreArray);
        
        JsonArray blockingArray = new JsonArray();
        for (String perm : blockingPermissions) blockingArray.add(perm);
        json.add("blockingPermissions", blockingArray);
        
        JsonArray itemsArray = new JsonArray();
        for (ItemStack item : items) {
            if (!item.isEmpty()) itemsArray.add(ItemSerializer.serialize(item));
        }
        json.add("items", itemsArray);
        
        JsonArray commandsArray = new JsonArray();
        for (String cmd : commands) commandsArray.add(cmd);
        json.add("commands", commandsArray);
        
        return json;
    }

    public static CrateReward fromJson(JsonObject json) {
        String id = json.get("id").getAsString();
        String crateId = json.has("crateId") ? json.get("crateId").getAsString() : "";
        String name = json.has("name") ? json.get("name").getAsString() : id;
        RewardType type = json.has("type") ? RewardType.valueOf(json.get("type").getAsString()) : RewardType.ITEM;
        String rarityId = json.has("rarityId") ? json.get("rarityId").getAsString() : "common";
        
        CrateReward reward = new CrateReward(id, crateId, name, type, rarityId);
        
        if (json.has("weight")) reward.weight = json.get("weight").getAsDouble();
        if (json.has("requiredPermission")) reward.requiredPermission = json.get("requiredPermission").getAsString();
        if (json.has("globalLimit")) reward.globalLimit = json.get("globalLimit").getAsInt();
        if (json.has("playerLimit")) reward.playerLimit = json.get("playerLimit").getAsInt();
        if (json.has("broadcast")) reward.broadcast = json.get("broadcast").getAsBoolean();
        if (json.has("broadcastMessage")) reward.broadcastMessage = json.get("broadcastMessage").getAsString();
        if (json.has("playerMessage")) reward.playerMessage = json.get("playerMessage").getAsString();
        if (json.has("active")) reward.active = json.get("active").getAsBoolean();
        if (json.has("visibleInPreview")) reward.visibleInPreview = json.get("visibleInPreview").getAsBoolean();
        if (json.has("milestoneOnly")) reward.milestoneOnly = json.get("milestoneOnly").getAsBoolean();
        if (json.has("displayOrder")) reward.displayOrder = json.get("displayOrder").getAsInt();
        
        if (json.has("icon")) reward.icon = ItemSerializer.deserialize(json.getAsJsonObject("icon"));
        
        if (json.has("lore")) {
            JsonArray loreArray = json.getAsJsonArray("lore");
            reward.lore = new ArrayList<>();
            for (JsonElement e : loreArray) reward.lore.add(e.getAsString());
        }
        
        if (json.has("blockingPermissions")) {
            JsonArray blockingArray = json.getAsJsonArray("blockingPermissions");
            reward.blockingPermissions = new ArrayList<>();
            for (JsonElement e : blockingArray) reward.blockingPermissions.add(e.getAsString());
        }
        
        if (json.has("items")) {
            JsonArray itemsArray = json.getAsJsonArray("items");
            reward.items = new ArrayList<>();
            for (JsonElement e : itemsArray) reward.items.add(ItemSerializer.deserialize(e.getAsJsonObject()));
        }
        
        if (json.has("commands")) {
            JsonArray commandsArray = json.getAsJsonArray("commands");
            reward.commands = new ArrayList<>();
            for (JsonElement e : commandsArray) reward.commands.add(e.getAsString());
        }
        
        return reward;
    }
}

