package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a rarity tier for crate rewards.
 */
public class CrateRarity {
    private final String id;
    private String name;
    private String color; // Hex color code (e.g., "#FFD700")
    private int priority; // Visual priority (higher = more prominent)
    private double weight; // Weight for random selection
    private String icon; // Item identifier for display
    private List<String> lore; // Description in preview
    private boolean active;
    private int displayOrder; // Order in editor/preview
    
    public CrateRarity(String id, String name, String color, double weight) {
        this.id = validateId(id);
        this.name = name != null ? name : id;
        this.color = validateColor(color);
        this.weight = Math.max(0, weight);
        this.priority = 0;
        this.icon = "minecraft:paper";
        this.lore = new ArrayList<>();
        this.active = true;
        this.displayOrder = 0;
    }
    
    private String validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Rarity ID cannot be null or empty");
        }
        String normalized = id.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (!normalized.equals(id.toLowerCase())) {
            throw new IllegalArgumentException("Rarity ID can only contain lowercase letters, numbers, underscore, and hyphen: " + id);
        }
        return normalized;
    }
    
    private String validateColor(String color) {
        if (color == null || color.isBlank()) return "#FFFFFF";
        if (!color.startsWith("#")) color = "#" + color;
        if (color.length() != 7) return "#FFFFFF";
        try {
            Color.decode(color);
            return color.toUpperCase();
        } catch (NumberFormatException e) {
            return "#FFFFFF";
        }
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public int getPriority() { return priority; }
    public double getWeight() { return weight; }
    public String getIcon() { return icon; }
    public List<String> getLore() { return new ArrayList<>(lore); }
    public boolean isActive() { return active; }
    public int getDisplayOrder() { return displayOrder; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setColor(String color) { this.color = validateColor(color); }
    public void setPriority(int priority) { this.priority = priority; }
    public void setWeight(double weight) { this.weight = Math.max(0, weight); }
    public void setIcon(String icon) { this.icon = icon; }
    public void setLore(List<String> lore) { this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>(); }
    public void setActive(boolean active) { this.active = active; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public Component getColoredName() {
        return Component.literal(name).withStyle(style -> style.withColor(TextColor.fromRgb(Color.decode(color).getRGB())));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("color", color);
        json.addProperty("priority", priority);
        json.addProperty("weight", weight);
        json.addProperty("icon", icon);
        json.addProperty("active", active);
        json.addProperty("displayOrder", displayOrder);
        
        com.google.gson.JsonArray loreArray = new com.google.gson.JsonArray();
        for (String line : lore) loreArray.add(line);
        json.add("lore", loreArray);
        
        return json;
    }

    public static CrateRarity fromJson(JsonObject json) {
        String id = json.get("id").getAsString();
        String name = json.has("name") ? json.get("name").getAsString() : id;
        String color = json.has("color") ? json.get("color").getAsString() : "#FFFFFF";
        double weight = json.has("weight") ? json.get("weight").getAsDouble() : 1.0;
        
        CrateRarity rarity = new CrateRarity(id, name, color, weight);
        
        if (json.has("priority")) rarity.priority = json.get("priority").getAsInt();
        if (json.has("icon")) rarity.icon = json.get("icon").getAsString();
        if (json.has("active")) rarity.active = json.get("active").getAsBoolean();
        if (json.has("displayOrder")) rarity.displayOrder = json.get("displayOrder").getAsInt();
        if (json.has("lore")) {
            com.google.gson.JsonArray loreArray = json.getAsJsonArray("lore");
            rarity.lore = new ArrayList<>();
            for (com.google.gson.JsonElement e : loreArray) rarity.lore.add(e.getAsString());
        }
        return rarity;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CrateRarity other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}