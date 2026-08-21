package com.pedrodalben.bigbangessentials.jobs;

import java.io.Serializable;
import java.util.*;

/**
 * Extensible, safe, and serializable context for specific job action information.
 * Avoids fragile subclass explosions while providing structured fields for common game mechanics
 * and an encapsulated, type-safe custom attributes map for future bridges.
 */
public class JobActionContext implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String dimension;
    private final String position;
    private final String blockId;
    private final String blockStateString;
    private final String toolUsed;
    private final String recipeId;
    private final boolean playerPlacedBlock;
    private final boolean cropMature;
    private final boolean automationBlocked;
    private final boolean spawnerSpawned;
    private final String biome;
    private final String structure;
    private final UUID targetUuid;
    private final String raidTier;
    private final UUID raidUuid;
    private final String pokemonSpecies;
    private final String pokemonForm;
    private final boolean firstDiscovery;
    private final String eventSource;
    private final Set<String> tags;
    private final Map<String, String> customAttributes;

    private JobActionContext(Builder builder) {
        this.dimension = builder.dimension != null ? builder.dimension : "";
        this.position = builder.position != null ? builder.position : "";
        this.blockId = builder.blockId != null ? builder.blockId : "";
        this.blockStateString = builder.blockStateString != null ? builder.blockStateString : "";
        this.toolUsed = builder.toolUsed != null ? builder.toolUsed : "";
        this.recipeId = builder.recipeId != null ? builder.recipeId : "";
        this.playerPlacedBlock = builder.playerPlacedBlock;
        this.cropMature = builder.cropMature;
        this.automationBlocked = builder.automationBlocked;
        this.spawnerSpawned = builder.spawnerSpawned;
        this.biome = builder.biome != null ? builder.biome : "";
        this.structure = builder.structure != null ? builder.structure : "";
        this.targetUuid = builder.targetUuid;
        this.raidTier = builder.raidTier != null ? builder.raidTier : "";
        this.raidUuid = builder.raidUuid;
        this.pokemonSpecies = builder.pokemonSpecies != null ? builder.pokemonSpecies : "";
        this.pokemonForm = builder.pokemonForm != null ? builder.pokemonForm : "";
        this.firstDiscovery = builder.firstDiscovery;
        this.eventSource = builder.eventSource != null ? builder.eventSource : "";
        this.tags = builder.tags != null ? Collections.unmodifiableSet(new HashSet<>(builder.tags)) : Collections.emptySet();
        this.customAttributes = builder.customAttributes != null ? Collections.unmodifiableMap(new HashMap<>(builder.customAttributes)) : Collections.emptyMap();
    }

    public String getDimension() { return dimension; }
    public String getPosition() { return position; }
    public String getBlockId() { return blockId; }
    public String getBlockStateString() { return blockStateString; }
    public String getToolUsed() { return toolUsed; }
    public String getRecipeId() { return recipeId; }
    public boolean isPlayerPlacedBlock() { return playerPlacedBlock; }
    public boolean isCropMature() { return cropMature; }
    public boolean isAutomationBlocked() { return automationBlocked; }
    public boolean isSpawnerSpawned() { return spawnerSpawned; }
    public String getBiome() { return biome; }
    public String getStructure() { return structure; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getRaidTier() { return raidTier; }
    public UUID getRaidUuid() { return raidUuid; }
    public String getPokemonSpecies() { return pokemonSpecies; }
    public String getPokemonForm() { return pokemonForm; }
    public boolean isFirstDiscovery() { return firstDiscovery; }
    public String getEventSource() { return eventSource; }
    public String getSource() { return eventSource; } // Legacy alias
    public Set<String> getTags() { return tags; }
    public Map<String, String> getCustomAttributes() { return customAttributes; }

    public static JobActionContext empty() {
        return builder().build();
    }

    public String getCustomAttribute(String key) {
        return customAttributes.get(key);
    }

    public String getCustomAttribute(String key, String defaultValue) {
        return customAttributes.getOrDefault(key, defaultValue);
    }

    public int getCustomAttributeAsInt(String key, int defaultValue) {
        String val = customAttributes.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getCustomAttributeAsBoolean(String key, boolean defaultValue) {
        String val = customAttributes.get(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    public String getMetadataJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (!dimension.isEmpty()) { sb.append("\"dim\":\"").append(dimension).append("\""); first = false; }
        if (!position.isEmpty()) { if (!first) sb.append(","); sb.append("\"pos\":\"").append(position).append("\""); first = false; }
        if (!eventSource.isEmpty()) { if (!first) sb.append(","); sb.append("\"src\":\"").append(eventSource).append("\""); first = false; }
        if (playerPlacedBlock) { if (!first) sb.append(","); sb.append("\"placed\":true"); first = false; }
        sb.append("}");
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String dimension;
        private String position;
        private String blockId;
        private String blockStateString;
        private String toolUsed;
        private String recipeId;
        private boolean playerPlacedBlock;
        private boolean cropMature;
        private boolean automationBlocked;
        private boolean spawnerSpawned;
        private String biome;
        private String structure;
        private UUID targetUuid;
        private String raidTier;
        private UUID raidUuid;
        private String pokemonSpecies;
        private String pokemonForm;
        private boolean firstDiscovery;
        private String eventSource;
        private Set<String> tags = new HashSet<>();
        private Map<String, String> customAttributes = new HashMap<>();

        public Builder dimension(String dimension) { this.dimension = dimension; return this; }
        public Builder position(String position) { this.position = position; return this; }
        public Builder blockId(String blockId) { this.blockId = blockId; return this; }
        public Builder blockStateString(String blockStateString) { this.blockStateString = blockStateString; return this; }
        public Builder toolUsed(String toolUsed) { this.toolUsed = toolUsed; return this; }
        public Builder recipeId(String recipeId) { this.recipeId = recipeId; return this; }
        public Builder playerPlacedBlock(boolean playerPlacedBlock) { this.playerPlacedBlock = playerPlacedBlock; return this; }
        public Builder cropMature(boolean cropMature) { this.cropMature = cropMature; return this; }
        public Builder automationBlocked(boolean automationBlocked) { this.automationBlocked = automationBlocked; return this; }
        public Builder spawnerSpawned(boolean spawnerSpawned) { this.spawnerSpawned = spawnerSpawned; return this; }
        public Builder biome(String biome) { this.biome = biome; return this; }
        public Builder structure(String structure) { this.structure = structure; return this; }
        public Builder targetUuid(UUID targetUuid) { this.targetUuid = targetUuid; return this; }
        public Builder raidTier(String raidTier) { this.raidTier = raidTier; return this; }
        public Builder raidUuid(UUID raidUuid) { this.raidUuid = raidUuid; return this; }
        public Builder pokemonSpecies(String pokemonSpecies) { this.pokemonSpecies = pokemonSpecies; return this; }
        public Builder pokemonForm(String pokemonForm) { this.pokemonForm = pokemonForm; return this; }
        public Builder firstDiscovery(boolean firstDiscovery) { this.firstDiscovery = firstDiscovery; return this; }
        public Builder eventSource(String eventSource) { this.eventSource = eventSource; return this; }
        
        public Builder tag(String tag) { if (tag != null) this.tags.add(tag); return this; }
        public Builder tags(Collection<String> tags) { if (tags != null) this.tags.addAll(tags); return this; }
        
        public Builder customAttribute(String key, Object value) {
            if (key != null && !key.trim().isEmpty() && value != null) {
                this.customAttributes.put(key, String.valueOf(value));
            }
            return this;
        }

        public JobActionContext build() {
            return new JobActionContext(this);
        }
    }
}
