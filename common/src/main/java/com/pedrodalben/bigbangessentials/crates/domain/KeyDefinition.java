package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.*;

public class KeyDefinition {
    private final String id;
    private String name;
    private ItemStack physicalItem;
    private List<String> lore;
    private boolean active;
    private CrateKeyType keyType;
    private List<String> compatibleCrateIds;
    private String requiredPermission;
    private String giveSound;
    private String takeSound;
    private List<String> giveCommands;
    private final Instant createdAt;
    private Instant updatedAt;

    public KeyDefinition(String id, String name) {
        this.id = validateId(id);
        this.name = name != null ? name : id;
        this.active = true;
        this.keyType = CrateKeyType.PHYSICAL;
        this.compatibleCrateIds = new ArrayList<>();
        this.lore = new ArrayList<>();
        this.requiredPermission = "";
        this.giveSound = "";
        this.takeSound = "";
        this.giveCommands = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    private String validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Key ID cannot be null or empty");
        }
        String normalized = id.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (!normalized.equals(id.toLowerCase())) {
            throw new IllegalArgumentException("Key ID can only contain lowercase letters, numbers, underscore, and hyphen: " + id);
        }
        return normalized;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ItemStack getPhysicalItem() { return physicalItem; }
    public List<String> getLore() { return new ArrayList<>(lore); }
    public boolean isActive() { return active; }
    public boolean isVirtual() { return keyType == CrateKeyType.VIRTUAL; }
    public CrateKeyType getKeyType() { return keyType; }
    public List<String> getCompatibleCrateIds() { return new ArrayList<>(compatibleCrateIds); }
    public String getRequiredPermission() { return requiredPermission; }
    public String getGiveSound() { return giveSound; }
    public String getTakeSound() { return takeSound; }
    public List<String> getGiveCommands() { return new ArrayList<>(giveCommands); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; touch(); }
    public void setPhysicalItem(ItemStack item) { this.physicalItem = item; touch(); }
    public void setLore(List<String> lore) { this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>(); touch(); }
    public void setActive(boolean active) { this.active = active; touch(); }
    public void setKeyType(CrateKeyType keyType) { this.keyType = keyType != null ? keyType : CrateKeyType.PHYSICAL; touch(); }
    @Deprecated public void setVirtual(boolean virtual) { this.keyType = virtual ? CrateKeyType.VIRTUAL : CrateKeyType.PHYSICAL; touch(); }
    public void setCompatibleCrateIds(List<String> ids) { this.compatibleCrateIds = ids != null ? new ArrayList<>(ids) : new ArrayList<>(); touch(); }
    public void addCompatibleCrateId(String crateId) { if (!compatibleCrateIds.contains(crateId)) compatibleCrateIds.add(crateId); touch(); }
    public void removeCompatibleCrateId(String crateId) { compatibleCrateIds.remove(crateId); touch(); }
    public void setRequiredPermission(String perm) { this.requiredPermission = perm; touch(); }
    public void setGiveSound(String sound) { this.giveSound = sound; touch(); }
    public void setTakeSound(String sound) { this.takeSound = sound; touch(); }
    public void setGiveCommands(List<String> commands) { this.giveCommands = commands != null ? new ArrayList<>(commands) : new ArrayList<>(); touch(); }

    private void touch() { this.updatedAt = Instant.now(); }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("active", active);
        json.addProperty("virtual", isVirtual());
        json.addProperty("keyType", keyType.name());
        json.addProperty("requiredPermission", requiredPermission);
        json.addProperty("giveSound", giveSound);
        json.addProperty("takeSound", takeSound);
        json.addProperty("createdAt", createdAt.toString());
        json.addProperty("updatedAt", updatedAt.toString());

        if (physicalItem != null && !physicalItem.isEmpty()) {
            json.add("physicalItem", ItemSerializer.serialize(physicalItem));
        }

        JsonArray loreArray = new JsonArray();
        for (String line : lore) loreArray.add(line);
        json.add("lore", loreArray);

        JsonArray crateIds = new JsonArray();
        for (String cid : compatibleCrateIds) crateIds.add(cid);
        json.add("compatibleCrateIds", crateIds);

        JsonArray commandsArray = new JsonArray();
        for (String cmd : giveCommands) commandsArray.add(cmd);
        json.add("giveCommands", commandsArray);

        return json;
    }

    public static KeyDefinition fromJson(JsonObject json) {
        String id = json.get("id").getAsString();
        String name = json.has("name") ? json.get("name").getAsString() : id;

        KeyDefinition key = new KeyDefinition(id, name);

        if (json.has("active")) key.active = json.get("active").getAsBoolean();
        if (json.has("keyType")) {
            key.keyType = CrateKeyType.valueOf(json.get("keyType").getAsString());
        } else if (json.has("virtual")) {
            key.keyType = json.get("virtual").getAsBoolean() ? CrateKeyType.VIRTUAL : CrateKeyType.PHYSICAL;
        }
        if (json.has("requiredPermission")) key.requiredPermission = json.get("requiredPermission").getAsString();
        if (json.has("giveSound")) key.giveSound = json.get("giveSound").getAsString();
        if (json.has("takeSound")) key.takeSound = json.get("takeSound").getAsString();

        if (json.has("physicalItem")) {
            key.physicalItem = ItemSerializer.deserialize(json.getAsJsonObject("physicalItem"));
        }

        if (json.has("lore")) {
            JsonArray loreArray = json.getAsJsonArray("lore");
            key.lore = new ArrayList<>();
            for (JsonElement e : loreArray) key.lore.add(e.getAsString());
        }

        if (json.has("compatibleCrateIds")) {
            JsonArray crateIds = json.getAsJsonArray("compatibleCrateIds");
            key.compatibleCrateIds = new ArrayList<>();
            for (JsonElement e : crateIds) key.compatibleCrateIds.add(e.getAsString());
        }

        if (json.has("giveCommands")) {
            JsonArray commandsArray = json.getAsJsonArray("giveCommands");
            key.giveCommands = new ArrayList<>();
            for (JsonElement e : commandsArray) key.giveCommands.add(e.getAsString());
        }

        return key;
    }
}
