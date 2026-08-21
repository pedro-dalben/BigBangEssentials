package com.pedrodalben.bigbangessentials.tablist.render;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.api.PlaceholderManager;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TabConditionEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(TabConditionEngine.class);

    public static boolean evaluate(String condition, ServerPlayer player, TabPlayerState state) {
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        try {
            if (condition.startsWith("permission:")) {
                String perm = condition.substring("permission:".length());
                return PermissionAPI.hasPermission(player.getUUID(), perm);
            } else if (condition.startsWith("group:")) {
                String group = condition.substring("group:".length());
                return group.equalsIgnoreCase(state.getPrimaryGroup());
            } else if (condition.startsWith("world:")) {
                String world = condition.substring("world:".length());
                return world.equalsIgnoreCase(state.getWorld());
            } else if (condition.startsWith("afk:")) {
                boolean targetVal = Boolean.parseBoolean(condition.substring("afk:".length()));
                return targetVal == state.isAfk();
            } else if (condition.startsWith("vanished:")) {
                boolean targetVal = Boolean.parseBoolean(condition.substring("vanished:".length()));
                return targetVal == state.isVanished();
            } else if (condition.startsWith("placeholder:")) {
                String expr = condition.substring("placeholder:".length());
                String[] parts = expr.split("=", 2);
                if (parts.length == 2) {
                    String placeholder = parts[0];
                    String expectedValue = parts[1];
                    String actualValue = PlaceholderManager.getInstance().setPlaceholders(player, placeholder);
                    return expectedValue.equalsIgnoreCase(actualValue);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to evaluate condition '{}' for player {}: {}", condition, player.getName().getString(), e.getMessage());
        }

        LOGGER.debug("Unknown condition prefix '{}' for player {}. Returning false (fail-closed).", condition, player.getName().getString());
        return false;
    }
}
