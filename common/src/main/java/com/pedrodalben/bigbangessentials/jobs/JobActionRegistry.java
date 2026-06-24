package com.pedrodalben.bigbangessentials.jobs;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JobActionRegistry {
    private static final Set<String> ACTION_TYPES = ConcurrentHashMap.newKeySet();

    static {
        ACTION_TYPES.add("BREAK_BLOCK");
        ACTION_TYPES.add("PLACE_BLOCK");
        ACTION_TYPES.add("KILL_ENTITY");
        ACTION_TYPES.add("FISH");
        ACTION_TYPES.add("HARVEST_CROP");
        ACTION_TYPES.add("CRAFT_ITEM");
        ACTION_TYPES.add("SMELT_ITEM");
        ACTION_TYPES.add("EXPLORE");
        ACTION_TYPES.add("USE_MAGIC");
    }

    public static boolean isValidActionType(String type) {
        if (type == null) return false;
        return ACTION_TYPES.contains(type.toUpperCase().replace('-', '_'));
    }

    public static void registerActionType(String type) {
        if (type != null) {
            ACTION_TYPES.add(type.toUpperCase().replace('-', '_'));
        }
    }

    public static Set<String> getActionTypes() {
        return ACTION_TYPES;
    }
}
