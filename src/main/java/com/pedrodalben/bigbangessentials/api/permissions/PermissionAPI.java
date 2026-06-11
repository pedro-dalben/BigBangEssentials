
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
        
        LOGGER.debug("═══ PERMISSION CHECK ═══");
        LOGGER.debug("Player UUID: {}", uuid);
        LOGGER.debug("Permission: {}", permission);
        LOGGER.debug("External adapter: {}", (externalAdapter != null ? externalAdapter.getName() : "NONE"));

        // Minecraft OPs should be able to use admin commands even when a permission
        // bridge is configured, as long as the mod's OP bypass setting is enabled.
        if (com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isOpsBypassPermissionsEnabled()) {
            if (isPlayerOpped(uuid)) {
                LOGGER.debug("Player is OP - bypassing permission check");
                LOGGER.debug("Result: TRUE (op bypass)");
                LOGGER.debug("═══════════════════════");
                return true;
            }
        }

        // If using external permissions (LuckPerms, FTB Ranks), delegate after OP bypass.
        if (externalAdapter != null) {
            LOGGER.debug("Using external permission system: {}", externalAdapter.getName());
            boolean hasExternalPerm = externalAdapter.hasPermission(uuid, permission);
            LOGGER.debug("External system returned: {}", hasExternalPerm);
            LOGGER.debug("═══════════════════════");
            return hasExternalPerm;
        }
        
        LOGGER.debug("Using INTERNAL permission system");

        // Finally check internal permission manager
        if (manager == null) {
            LOGGER.warn("PermissionAPI.hasPermission: PermissionManager is null - returning false");
            LOGGER.debug("Result: FALSE (no manager)");
            LOGGER.debug("═══════════════════════");
            return false;
        }

        boolean hasInternalPerm = manager.hasPermission(uuid, permission);
        LOGGER.debug("Internal system returned: {}", hasInternalPerm);
        LOGGER.debug("Result: {}", hasInternalPerm);
        LOGGER.debug("═══════════════════════");
        return hasInternalPerm;
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
     * Checks if a player is opped by their UUID.
     */
    private static boolean isPlayerOpped(UUID uuid) {
        try {
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
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

        LOGGER.debug(">>> PermissionAPI.getPrefix() called for UUID: {}", uuid);
        LOGGER.debug(">>> Using external adapter: {}", (externalAdapter != null ? externalAdapter.getName() : "NONE"));

        if (externalAdapter != null) {
            LOGGER.debug(">>> Querying external adapter for prefix...");
            String prefix = externalAdapter.getPrefix(uuid);
            LOGGER.debug(">>> External adapter returned: [{}]", prefix);
            if (prefix != null && !prefix.isBlank()) {
                return prefix;
            }

            String fallback = fallbackInternalPrefix(uuid);
            LOGGER.debug(">>> Falling back to internal prefix: [{}]", fallback);
            return fallback;
        }

        LOGGER.debug(">>> Using internal permission system (no external adapter)");
        String prefix = fallbackInternalPrefix(uuid);
        LOGGER.debug(">>> Internal system prefix: [{}]", prefix);
        return prefix;
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
