
    package com.pedrodalben.bigbangessentials.api.permissions;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import com.pedrodalben.bigbangessentials.permissions.PermissionGroup;
import com.pedrodalben.bigbangessentials.permissions.PermissionManager;
import com.pedrodalben.bigbangessentials.permissions.PermissionUser;

public class PermissionAPI {
    private static PermissionManager manager;
    private static ExternalPermissionAdapter externalAdapter = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionAPI.class);

    /**
     * Set the built-in permission manager (default system).
     */
    public static void setManager(PermissionManager m) {
        manager = m;
    }

    /**
     * Set an external permission adapter (e.g., LuckPerms, FTB Ranks).
     * If set, all permission checks will be delegated to this adapter.
     */
    public static void setExternalAdapter(ExternalPermissionAdapter adapter) {
        externalAdapter = adapter;
        LOGGER.info("External permission adapter set: " + (adapter != null ? adapter.getName() : "none"));
    }

    /**
     * Returns the current external permission adapter, or null if using built-in.
     */
    @SuppressWarnings("unused") // Public API method
    public static ExternalPermissionAdapter getExternalAdapter() {
        return externalAdapter;
    }

    private static String fallbackInternalPrefix(UUID uuid) {
        if (manager == null) {
            return "";
        }
        PermissionUser user = manager.getUser(uuid);
        String groupName = (user != null && user.getGroup() != null) ? user.getGroup() : manager.getDefaultGroup();
        if (groupName == null) {
            return "";
        }
        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            return "";
        }
        String prefix = group.getPrefix();
        return prefix != null ? prefix : "";
    }

    private static String fallbackInternalSuffix(UUID uuid) {
        if (manager == null) {
            return "";
        }
        PermissionUser user = manager.getUser(uuid);
        String groupName = (user != null && user.getGroup() != null) ? user.getGroup() : manager.getDefaultGroup();
        if (groupName == null) {
            return "";
        }
        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            return "";
        }
        String suffix = group.getSuffix();
        return suffix != null ? suffix : "";
    }

    /**
     * Returns true if using an external permission system.
     */
    public static boolean isUsingExternal() {
        return externalAdapter != null;
    }

    public static boolean hasPermission(UUID uuid, String permission) {
        // Validate input parameters
        if (uuid == null) {
            LOGGER.warn("PermissionAPI.hasPermission: UUID is null");
            return false;
        }
        if (permission == null || permission.trim().isEmpty()) {
            LOGGER.warn("PermissionAPI.hasPermission: Permission string is null or empty");
            return false;
        }
        
        LOGGER.debug("hasPermission uuid={} perm={} ext={}", uuid, permission, externalAdapter != null);

        // Minecraft OPs should be able to use admin commands even when a permission
        // bridge is configured, as long as the mod's OP bypass setting is enabled.
        if (com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isOpsBypassPermissionsEnabled()) {
            if (isPlayerOpped(uuid)) {
                return true;
            }
        }

        // If using external permissions (LuckPerms, FTB Ranks), delegate after OP bypass.
        if (externalAdapter != null) {
            return externalAdapter.hasPermission(uuid, permission);
        }
        
        // Finally check internal permission manager
        if (manager == null) {
            LOGGER.warn("PermissionAPI.hasPermission: PermissionManager is null - returning false");
            return false;
        }

        return manager.hasPermission(uuid, permission);
    }

    /**
     * Returns true if the player has any permission in the provided list.
     */
    public static boolean hasAnyPermission(UUID uuid, String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }

        for (String permission : permissions) {
            if (hasPermission(uuid, permission)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the player has any of the provided permissions as explicit nodes.
     * This ignores parent-node inheritance so sensitive subcommands do not inherit access
     * from a broader parent permission.
     */
    public static boolean hasAnyExactPermission(UUID uuid, String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }

        for (String permission : permissions) {
            if (hasExactPermission(uuid, permission)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks only explicitly assigned permission nodes, without wildcard expansion.
     * OP bypass still applies when enabled so server operators keep administrative access.
     */
    public static boolean hasExactPermission(UUID uuid, String permission) {
        if (uuid == null) {
            LOGGER.warn("PermissionAPI.hasExactPermission: UUID is null");
            return false;
        }
        if (permission == null || permission.trim().isEmpty()) {
            LOGGER.warn("PermissionAPI.hasExactPermission: Permission string is null or empty");
            return false;
        }

        if (com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isOpsBypassPermissionsEnabled()
            && isPlayerOpped(uuid)) {
            return true;
        }

        if (externalAdapter != null) {
            return externalAdapter.hasExactPermission(uuid, permission);
        }

        return manager != null && manager.hasExactPermission(uuid, permission);
    }

    /**
     * Checks a target/others permission without inheriting from its plain parent node.
     * Explicit target nodes and explicitly assigned ancestor wildcards still apply.
     */
    public static boolean hasTargetPermission(UUID uuid, String permission) {
        if (permission == null || permission.trim().isEmpty()) {
            return false;
        }

        String normalized = permission.toLowerCase();
        if (hasExactPermission(uuid, normalized)) {
            return true;
        }

        int dot = normalized.indexOf('.');
        while (dot > 0 && dot < normalized.length() - 1) {
            if (hasExactPermission(uuid, normalized.substring(0, dot) + ".*")) {
                return true;
            }
            dot = normalized.indexOf('.', dot + 1);
        }
        return false;
    }
    
    /**
     * Checks if a player is opped by their UUID.
     */
    private static boolean isPlayerOpped(UUID uuid) {
        try {
            net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
            if (server != null) {
                // Try to get the player directly and check their permission level
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    return player.hasPermissions(2); // Op level 2 or higher
                }
                
                // If player is offline, check the ops file
                var profileCache = server.getProfileCache();
                if (profileCache != null) {
                    com.mojang.authlib.GameProfile profile = profileCache.get(uuid).orElse(null);
                    if (profile != null) {
                        return server.getPlayerList().isOp(profile);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not check op status for UUID {}: {}", uuid, e.getMessage());
        }
        return false;
    }

    public static PermissionManager getManager() {
        return manager;
    }

    public static String getPrefix(UUID uuid) {
        // Validate input parameters
        if (uuid == null) {
            LOGGER.warn("PermissionAPI.getPrefix: UUID is null");
            return "";
        }

        if (externalAdapter != null) {
            String prefix = externalAdapter.getPrefix(uuid);
            if (prefix != null && !prefix.isBlank()) {
                return prefix;
            }
            return fallbackInternalPrefix(uuid);
        }

        return fallbackInternalPrefix(uuid);
    }

    public static String getSuffix(UUID uuid) {
        // Validate input parameters
        if (uuid == null) {
            LOGGER.warn("PermissionAPI.getSuffix: UUID is null");
            return "";
        }

        if (externalAdapter != null) {
            String suffix = externalAdapter.getSuffix(uuid);
            if (suffix != null && !suffix.isBlank()) {
                return suffix;
            }

            String fallback = fallbackInternalSuffix(uuid);
            LOGGER.debug(">>> Falling back to internal suffix: [{}]", fallback);
            return fallback;
        }

        return fallbackInternalSuffix(uuid);
    }

    /**
     * Returns the player's primary group or rank identifier.
     */
    public static String getPrimaryGroup(UUID uuid) {
        if (uuid == null) {
            LOGGER.warn("PermissionAPI.getPrimaryGroup: UUID is null");
            return "default";
        }

        if (externalAdapter != null) {
            String group = externalAdapter.getPrimaryGroup(uuid);
            if (group != null && !group.trim().isEmpty()) {
                return group;
            }
        }

        if (manager == null) {
            LOGGER.warn("PermissionAPI.getPrimaryGroup: PermissionManager is null");
            return "default";
        }

        PermissionUser user = manager.getUser(uuid);
        String groupName = (user != null && user.getGroup() != null && !user.getGroup().trim().isEmpty())
            ? user.getGroup()
            : manager.getDefaultGroup();
        return groupName != null && !groupName.trim().isEmpty() ? groupName : "default";
    }

    /**
     * Reloads all permissions and groups from disk at runtime.
     */
    public static void reload() throws Exception {
        if (externalAdapter != null) {
            externalAdapter.reload();
        } else if (manager != null) {
            manager.reload();
        } else {
            LOGGER.warn("PermissionAPI.reload: Both externalAdapter and manager are null - nothing to reload");
            throw new IllegalStateException("Permission system not initialized - cannot reload");
        }
    }
}
