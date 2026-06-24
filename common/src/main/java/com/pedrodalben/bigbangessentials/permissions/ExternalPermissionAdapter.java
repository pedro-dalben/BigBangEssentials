package com.pedrodalben.bigbangessentials.permissions;

import java.util.UUID;

/**
 * Interface for external permission adapters (LuckPerms, FTB Ranks, etc).
 */
public interface ExternalPermissionAdapter {
    /**
     * Check if the user has the given permission.
     * @param uuid The UUID of the user.
     * @param permission The permission node.
     * @return true if the user has the permission, false otherwise.
     */
    boolean hasPermission(UUID uuid, String permission);

    /**
     * Check for an explicitly assigned permission node without wildcard expansion where supported.
     * Implementations that cannot inspect exact assignments should not grant bypass permissions.
     */
    default boolean hasExactPermission(UUID uuid, String permission) {
        return false;
    }

    /**
     * Get the prefix for the user (if supported).
     * @param uuid The UUID of the user.
     * @return The prefix string, or null if not supported.
     */
    String getPrefix(UUID uuid);

    /**
     * Get the suffix for the user (if supported).
     * @param uuid The UUID of the user.
     * @return The suffix string, or null if not supported.
     */
    String getSuffix(UUID uuid);

    /**
     * Get the user's primary group or rank identifier.
     * @param uuid The UUID of the user.
     * @return The primary group name, or null if not supported.
     */
    default String getPrimaryGroup(UUID uuid) {
        return null;
    }

    /**
     * Reload the external permission data (if supported).
     */
    void reload();

    /**
     * @return The name of the external system (e.g., "LuckPerms").
     */
    String getName();
    
    /**
     * Check if this adapter is properly loaded and available for use.
     * @return true if the external system is available, false otherwise.
     */
    boolean isAvailable();
}
