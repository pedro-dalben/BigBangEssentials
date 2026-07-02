package com.pedrodalben.bigbangessentials.objectives;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared activity/action types used by Jobs, RankUp, and future progression systems.
 * Normalizes hyphen/underscore/lower-case variants.
 */
public enum ObjectiveActionType {
    BREAK_BLOCK,
    PLACE_BLOCK,
    KILL_ENTITY,
    FISH,
    HARVEST_CROP,
    CRAFT_ITEM,
    SMELT_ITEM,
    EXPLORE,
    PLAYTIME_MINUTES,
    VISIT_BIOME,
    ADVANCEMENT,
    COBBLEMON_CAPTURE,
    COBBLEMON_BATTLE_WIN,
    COBBLEMON_DEFEAT,
    COBBLEMON_HATCH_EGG,
    UNKNOWN;

    private static final Map<String, ObjectiveActionType> BY_NAME = new ConcurrentHashMap<>();

    static {
        for (ObjectiveActionType type : values()) {
            BY_NAME.put(normalize(type.name()), type);
        }
    }

    public static ObjectiveActionType fromString(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        return BY_NAME.getOrDefault(normalize(raw), UNKNOWN);
    }

    public static boolean isKnown(String raw) {
        return fromString(raw) != UNKNOWN;
    }

    public static void register(String raw) {
        if (raw == null || raw.isBlank()) return;
        String key = normalize(raw);
        if (!BY_NAME.containsKey(key)) {
            BY_NAME.put(key, UNKNOWN);
        }
    }

    public String configName() {
        return name().toLowerCase().replace('_', '-');
    }

    private static String normalize(String input) {
        return input.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }
}
