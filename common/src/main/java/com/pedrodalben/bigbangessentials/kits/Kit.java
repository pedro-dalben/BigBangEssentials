package com.pedrodalben.bigbangessentials.kits;

import net.minecraft.world.item.ItemStack;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import com.pedrodalben.bigbangessentials.util.ResourceLocationHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Represents a kit containing items, metadata, and usage restrictions.
 * Kits can have cooldowns, permission requirements, and usage limits.
 */
public class Kit {
    private final String name;
    private final String displayName;
    private final String description;
    private final List<ItemStack> items;
    private final long cooldownMillis;
    private final String permission;
    private final int maxUses;
    private final boolean enabled;

    /**
     * Creates a new Kit instance.
     * 
     * @param name Unique identifier for the kit (lowercase, no spaces)
     * @param displayName Human-readable name for display
     * @param description Brief description of the kit
     * @param items List of ItemStacks in the kit
     * @param cooldownMillis Cooldown between uses in milliseconds
     * @param permission Required permission node (null for no requirement)
     * @param maxUses Maximum uses per player (-1 for unlimited)
     * @param enabled Whether the kit is currently enabled
     */
    public Kit(String name, String displayName, String description, List<ItemStack> items, 
               long cooldownMillis, String permission, int maxUses, boolean enabled) {
        this.name = name.toLowerCase().replaceAll("[^a-z0-9_]", ""); // Sanitize name
        this.displayName = displayName != null ? displayName : name;
        this.description = description != null ? description : "";
        this.items = new ArrayList<>(items != null ? items : Collections.emptyList());
        this.cooldownMillis = Math.max(0, cooldownMillis);
        this.permission = permission;
        this.maxUses = maxUses;
        this.enabled = enabled;
    }
    
    // Getters
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public List<ItemStack> getItems() { return new ArrayList<>(items); }
    public long getCooldownMillis() { return cooldownMillis; }
    public String getPermission() { return permission; }
    public int getMaxUses() { return maxUses; }
    public boolean isEnabled() { return enabled; }

    @SuppressWarnings("unused") // Public API method - reserved for future use
    public Map<String, Object> getMetadata() { return new HashMap<>(); }

    /**
     * Gets cooldown duration in a human-readable format.
     */
    @SuppressWarnings("unused") // Public API method
    public String getCooldownDisplay() {
        if (cooldownMillis == 0) return "No cooldown";
        
        long seconds = TimeUnit.MILLISECONDS.toSeconds(cooldownMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(cooldownMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(cooldownMillis);
        
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
    
    /**
     * Checks if the kit has any restrictions.
     */
    @SuppressWarnings("unused") // Public API method
    public boolean hasRestrictions() {
        return cooldownMillis > 0 || permission != null || maxUses > 0;
    }
    
    /**
     * Creates a copy of this kit with modified properties.
     */
    @SuppressWarnings("unused") // Public API method
    public Kit withEnabled(boolean enabled) {
        return new Kit(name, displayName, description, items, cooldownMillis, 
                      permission, maxUses, enabled);
    }
    
    @SuppressWarnings("unused") // Public API method
    public Kit withCooldown(long cooldownMillis) {
        return new Kit(name, displayName, description, items, cooldownMillis, 
                      permission, maxUses, enabled);
    }
    
    @SuppressWarnings("unused") // Public API method
    public Kit withPermission(String permission) {
        return new Kit(name, displayName, description, items, cooldownMillis, 
                      permission, maxUses, enabled);
    }
    
    /**
     * Converts the kit to JSON for storage.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("displayName", displayName);
        json.addProperty("description", description);
        json.addProperty("cooldownHours", cooldownMillis / 3600000d);
        json.addProperty("permission", permission);
        json.addProperty("maxUses", maxUses);
        json.addProperty("enabled", enabled);
        
        // Serialize items
        JsonArray itemsArray = new JsonArray();
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                JsonObject itemJson = new JsonObject();
                itemJson.addProperty("item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                itemJson.addProperty("count", item.getCount());
                
                if (item.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
                    var customData = item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if (customData != null) {
                        itemJson.addProperty("nbt", customData.toString());
                    }
                }
                
                itemsArray.add(itemJson);
            }
        }
        json.add("items", itemsArray);
        
        return json;
    }
    
    /**
     * Creates a Kit from JSON data.
     */
    public static Kit fromJson(JsonObject json) {
        String name = json.get("name").getAsString();
        String displayName = json.has("displayName") ? json.get("displayName").getAsString() : name;
        String description = json.has("description") ? json.get("description").getAsString() : "";

        long rawCooldownMillis = parseCooldownMillis(json);
        boolean oneTimeKit = rawCooldownMillis < 0;
        long cooldownMillis = Math.max(0, rawCooldownMillis);

        // Always set permission node to bigbangessentials.kits.<kitname> if not present
        String permission = json.has("permission") && !json.get("permission").getAsString().isEmpty()
                ? json.get("permission").getAsString()
                : ("bigbangessentials.kits." + name.toLowerCase());
        int maxUses = json.has("maxUses") ? json.get("maxUses").getAsInt() : (oneTimeKit ? 1 : -1);
        boolean enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
        
        // Deserialize items
        List<ItemStack> items = new ArrayList<>();
        if (json.has("items") && json.get("items").isJsonArray()) {
            JsonArray itemsArray = json.getAsJsonArray("items");
            for (JsonElement element : itemsArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject itemJson = element.getAsJsonObject();
                try {
                    if (!itemJson.has("item") || !itemJson.get("item").isJsonPrimitive()) {
                        continue;
                    }
                    String itemString = itemJson.get("item").getAsString();

                    // Use helper to create ResourceLocation safely across versions
                    ResourceLocation itemId = ResourceLocationHelper.parse(itemString);

                    // Use getOptional() for Minecraft 1.21.4+ compatibility
                    Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                    if (item == null) {
                        // Skip unknown items
                        continue;
                    }
                    int count = itemJson.has("count") ? itemJson.get("count").getAsInt() : 1;
                    
                    ItemStack stack = new ItemStack(item, count);
                    
                    // Apply NBT if present
                    if (itemJson.has("nbt")) {
                        try {
                            CompoundTag nbt = TagParser.parseTag(itemJson.get("nbt").getAsString());
                            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                                     net.minecraft.world.item.component.CustomData.of(nbt));
                        } catch (Exception e) {
                            // Skip invalid NBT
                        }
                    }
                    
                    items.add(stack);
                } catch (Exception e) {
                    // Skip invalid items
                }
            }
        }
        
        return new Kit(name, displayName, description, items, cooldownMillis, 
                      permission, maxUses, enabled);
    }

    private static long parseCooldownMillis(JsonObject json) {
        if (hasNumericValue(json, "cooldownMillis")) {
            return millisFromValue(json.get("cooldownMillis").getAsDouble(), 1d);
        }
        if (hasNumericValue(json, "cooldownMs")) {
            return millisFromValue(json.get("cooldownMs").getAsDouble(), 1d);
        }
        if (hasNumericValue(json, "cooldownHours")) {
            return millisFromValue(json.get("cooldownHours").getAsDouble(), 60d * 60d * 1000d);
        }
        if (hasNumericValue(json, "cooldownMinutes")) {
            return millisFromValue(json.get("cooldownMinutes").getAsDouble(), 60d * 1000d);
        }
        if (hasNumericValue(json, "cooldownSeconds")) {
            return millisFromValue(json.get("cooldownSeconds").getAsDouble(), 1000d);
        }
        if (hasNumericValue(json, "cooldownTicks")) {
            return millisFromValue(json.get("cooldownTicks").getAsDouble(), 50d);
        }
        if (hasNumericValue(json, "delay")) {
            return millisFromValue(json.get("delay").getAsDouble(), 1000d);
        }
        if (hasNumericValue(json, "cooldown")) {
            return millisFromValue(json.get("cooldown").getAsDouble(), 1000d);
        }
        return 0;
    }

    private static boolean hasNumericValue(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() && json.get(key).isJsonPrimitive()
            && json.get(key).getAsJsonPrimitive().isNumber();
    }

    private static long millisFromValue(double value, double multiplier) {
        if (value < 0) {
            return -1;
        }
        return Math.max(0, Math.round(value * multiplier));
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Kit other)) return false;
        return Objects.equals(name, other.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
    
    @Override
    public String toString() {
        return String.format("Kit{name='%s', displayName='%s', items=%d, enabled=%s}", 
                           name, displayName, items.size(), enabled);
    }
}
