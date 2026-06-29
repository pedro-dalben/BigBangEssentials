package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * Configuration for crate preview (player view of possible rewards).
 */
public class CratePreviewConfig {
    private boolean enabled;
    private String layout; // e.g., "54", "45", "36", "27", "9"
    private int[] rewardSlots; // Slots where rewards are displayed
    private boolean showChance;
    private boolean hideUnavailableRewards;
    private String requirementsMessage;
    private boolean showOpenAllButton;
    private int maxPreviewItems; // Limit for pagination
    
    public CratePreviewConfig() {
        this.enabled = true;
        this.layout = "54";
        this.rewardSlots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        this.showChance = true;
        this.hideUnavailableRewards = true;
        this.requirementsMessage = "§7Clique para ver os requisitos";
        this.showOpenAllButton = true;
        this.maxPreviewItems = 28;
    }

    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }
    public int[] getRewardSlots() { return rewardSlots.clone(); }
    public void setRewardSlots(int[] rewardSlots) { this.rewardSlots = rewardSlots != null ? rewardSlots.clone() : new int[0]; }
    public boolean isShowChance() { return showChance; }
    public void setShowChance(boolean showChance) { this.showChance = showChance; }
    public boolean isHideUnavailableRewards() { return hideUnavailableRewards; }
    public void setHideUnavailableRewards(boolean hideUnavailableRewards) { this.hideUnavailableRewards = hideUnavailableRewards; }
    public String getRequirementsMessage() { return requirementsMessage; }
    public void setRequirementsMessage(String requirementsMessage) { this.requirementsMessage = requirementsMessage; }
    public boolean isShowOpenAllButton() { return showOpenAllButton; }
    public void setShowOpenAllButton(boolean showOpenAllButton) { this.showOpenAllButton = showOpenAllButton; }
    public int getMaxPreviewItems() { return maxPreviewItems; }
    public void setMaxPreviewItems(int maxPreviewItems) { this.maxPreviewItems = Math.max(1, maxPreviewItems); }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("layout", layout);
        json.addProperty("showChance", showChance);
        json.addProperty("hideUnavailableRewards", hideUnavailableRewards);
        json.addProperty("requirementsMessage", requirementsMessage);
        json.addProperty("showOpenAllButton", showOpenAllButton);
        json.addProperty("maxPreviewItems", maxPreviewItems);
        
        JsonArray slotsArray = new JsonArray();
        for (int slot : rewardSlots) slotsArray.add(slot);
        json.add("rewardSlots", slotsArray);
        
        return json;
    }

    public static CratePreviewConfig fromJson(JsonObject json) {
        CratePreviewConfig config = new CratePreviewConfig();
        if (json.has("enabled")) config.enabled = json.get("enabled").getAsBoolean();
        if (json.has("layout")) config.layout = json.get("layout").getAsString();
        if (json.has("showChance")) config.showChance = json.get("showChance").getAsBoolean();
        if (json.has("hideUnavailableRewards")) config.hideUnavailableRewards = json.get("hideUnavailableRewards").getAsBoolean();
        if (json.has("requirementsMessage")) config.requirementsMessage = json.get("requirementsMessage").getAsString();
        if (json.has("showOpenAllButton")) config.showOpenAllButton = json.get("showOpenAllButton").getAsBoolean();
        if (json.has("maxPreviewItems")) config.maxPreviewItems = json.get("maxPreviewItems").getAsInt();
        if (json.has("rewardSlots")) {
            JsonArray slotsArray = json.getAsJsonArray("rewardSlots");
            config.rewardSlots = new int[slotsArray.size()];
            for (int i = 0; i < slotsArray.size(); i++) {
                config.rewardSlots[i] = slotsArray.get(i).getAsInt();
            }
        }
        return config;
    }
}