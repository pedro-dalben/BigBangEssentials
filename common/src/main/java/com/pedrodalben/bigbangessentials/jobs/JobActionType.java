package com.pedrodalben.bigbangessentials.jobs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Normalizes all job action types across the game and future integrations (Cobblemon, Raids, Pastures, etc.).
 * Supports legacy configuration strings and aliases for backward compatibility.
 */
public enum JobActionType {
    // Core / Supported types
    BREAK_BLOCK("BREAK-BLOCK", "BREAK_BLOCK"),
    HARVEST_CROP("HARVEST-CROP", "HARVEST_CROP"),
    PLACE_BLOCK("PLACE-PROJECT-BLOCK", "PLACE-BLOCK", "PLACE_BLOCK", "PLACE_PROJECT_BLOCK"),
    KILL_ENTITY("KILL-ENTITY", "KILL_ENTITY"),
    FISH("FISH-CATCH", "FISH", "FISH_CATCH"),
    CRAFT_ITEM("CRAFT-RECIPE", "CRAFT-ITEM", "CRAFT_ITEM", "CRAFT_RECIPE"),
    SMELT_ITEM("SMELT-RECIPE", "SMELT-ITEM", "SMELT_ITEM", "SMELT_RECIPE"),
    EXPLORE("DISCOVER-BIOME", "DISCOVER-STRUCTURE", "EXPLORE", "DISCOVER_BIOME", "DISCOVER_STRUCTURE"),
    USE_MAGIC("USE-MAGIC", "USE_MAGIC"),
    CONTRACT_DELIVERED("CONTRACT-DELIVERED", "CONTRACT_DELIVERED"),

    // Future Pokemon / Addon types
    POKEMON_CAPTURED("POKEMON-CAPTURED", "POKEMON_CAPTURED"),
    DEX_ENTRY_ADDED("DEX-ENTRY-ADDED", "DEX_ENTRY_ADDED"),
    FOSSIL_REVIVED("FOSSIL-REVIVED", "FOSSIL_REVIVED"),
    EGG_CREATED("EGG-CREATED", "EGG_CREATED"),
    EGG_HATCHED("EGG-HATCHED", "EGG_HATCHED"),
    PASTURE_TASK_COMPLETED("PASTURE-TASK-COMPLETED", "PASTURE_TASK_COMPLETED"),
    TRAINER_BATTLE_WON("TRAINER-BATTLE-WON", "TRAINER_BATTLE_WON"),
    RAID_CLEARED("RAID-CLEARED", "RAID_CLEARED");

    private final List<String> configKeys;

    JobActionType(String... configKeys) {
        this.configKeys = Collections.unmodifiableList(Arrays.asList(configKeys));
    }

    public List<String> getConfigKeys() {
        return configKeys;
    }

    /**
     * Converts a string action type (from legacy calls or configs) to a JobActionType.
     * Returns null if no matching type is found.
     */
    public static JobActionType fromString(String str) {
        if (str == null) return null;
        String normalized = str.toUpperCase().replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            for (JobActionType type : values()) {
                for (String key : type.configKeys) {
                    if (key.equalsIgnoreCase(str) || key.replace('-', '_').equalsIgnoreCase(normalized)) {
                        return type;
                    }
                }
            }
            return null;
        }
    }
}
