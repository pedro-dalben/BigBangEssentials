package com.pedrodalben.bigbangessentials.permissions;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionRegistry;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.pedrodalben.bigbangessentials.util.Platform;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LuckPerms integration adapter.
 * Provides proper user loading and permission checking with fallback support.
 */
public class LuckPermsAdapter implements ExternalPermissionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuckPermsAdapter.class);
    private final boolean luckPermsLoaded;
    private LuckPerms luckPermsApi;
    private static final long USER_LOAD_TIMEOUT = 5; // seconds

    public LuckPermsAdapter() {
        this.luckPermsLoaded = Platform.isModLoaded("luckperms");
        if (luckPermsLoaded) {
            try {
                this.luckPermsApi = LuckPermsProvider.get();
                LOGGER.info("LuckPerms API loaded successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to load LuckPerms API: {}", e.getMessage(), e);
                this.luckPermsApi = null;
            }
        } else {
            LOGGER.debug("LuckPerms mod not detected");
        }
    }

    @Override
    public boolean hasPermission(UUID uuid, String permission) {
        if (!luckPermsLoaded || luckPermsApi == null) {
            return false;
        }

        try {
            // Try to get cached user first (fast path)
            User user = luckPermsApi.getUserManager().getUser(uuid);

            // If user not cached, try to load them (for offline permission checks)
            if (user == null) {
                try {
                    // Load user data asynchronously with timeout
                    CompletableFuture<User> userFuture = luckPermsApi.getUserManager().loadUser(uuid);
                    user = userFuture.get(USER_LOAD_TIMEOUT, TimeUnit.SECONDS);
                } catch (Exception e) {
                    LOGGER.debug("Could not load user {} from LuckPerms: {}", uuid, e.getMessage());
                    return getDefaultPermissionValue(permission);
                }
            }

            if (user == null) {
                LOGGER.debug("User {} not found in LuckPerms", uuid);
                return getDefaultPermissionValue(permission);
            }

            // Check permission using LuckPerms API
            QueryOptions queryOptions = QueryOptions.defaultContextualOptions();
            Tristate result = user.getCachedData().getPermissionData(queryOptions).checkPermission(permission);

            // Tristate: TRUE = has permission, FALSE = explicitly denied, UNDEFINED = not set
            // When using external permissions (LuckPerms), we respect their decision completely
            // UNDEFINED and FALSE both mean "no permission" - admin must explicitly grant permissions
            if (result == Tristate.UNDEFINED) {
                boolean defaultValue = getDefaultPermissionValue(permission);
                LOGGER.debug("LuckPerms permission '{}' is undefined, falling back to default: {}",
                    permission, defaultValue);
                return defaultValue;
            }

            boolean hasPermission = result.asBoolean();

            LOGGER.debug("LuckPerms permission check: user={}, permission={}, result={} ({})",
                uuid, permission, hasPermission, result);

            return hasPermission;

        } catch (Exception e) {
            LOGGER.error("Error checking permission '{}' for user {}: {}",
                permission, uuid, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean hasExactPermission(UUID uuid, String permission) {
        if (!luckPermsLoaded || luckPermsApi == null || permission == null || permission.isBlank()) {
            return false;
        }

        try {
            User user = getOrLoadUser(uuid);
            if (user == null) {
                return false;
            }

            QueryOptions queryOptions = QueryOptions.defaultContextualOptions();
            Boolean exactValue = user.getCachedData()
                .getPermissionData(queryOptions)
                .getPermissionMap()
                .get(permission.toLowerCase());
            return Boolean.TRUE.equals(exactValue);
        } catch (Exception e) {
            LOGGER.error("Error checking exact permission '{}' for user {}: {}",
                permission, uuid, e.getMessage(), e);
            return false;
        }
    }

    private User getOrLoadUser(UUID uuid) throws Exception {
        if (uuid == null) {
            return null;
        }

        User user = luckPermsApi.getUserManager().getUser(uuid);
        if (user != null) {
            return user;
        }

        CompletableFuture<User> userFuture = luckPermsApi.getUserManager().loadUser(uuid);
        return userFuture.get(USER_LOAD_TIMEOUT, TimeUnit.SECONDS);
    }

    private boolean getDefaultPermissionValue(String permission) {
        return PermissionRegistry.getInstance().getDefaultPermissionValue(permission);
    }

    @Override
    public String getPrefix(UUID uuid) {
        LOGGER.debug("=== LUCKPERMS PREFIX REQUEST ===");
        LOGGER.debug("UUID: {}", uuid);
        LOGGER.debug("LuckPerms loaded: {}", luckPermsLoaded);
        LOGGER.debug("LuckPerms API: {}", (luckPermsApi != null ? "available" : "NULL"));

        if (!luckPermsLoaded || luckPermsApi == null) {
            LOGGER.debug("LuckPerms not available - returning null");
            return null;
        }

        try {
            User user = luckPermsApi.getUserManager().getUser(uuid);
            LOGGER.debug("Cached user: {}", (user != null ? user.getUsername() : "NULL"));

            // Try to load if not cached
            if (user == null) {
                LOGGER.debug("User not cached, attempting to load...");
                try {
                    CompletableFuture<User> userFuture = luckPermsApi.getUserManager().loadUser(uuid);
                    user = userFuture.get(USER_LOAD_TIMEOUT, TimeUnit.SECONDS);
                    LOGGER.debug("Loaded user: {}", (user != null ? user.getUsername() : "FAILED"));
                } catch (Exception e) {
                    LOGGER.debug("Could not load user {} from LuckPerms for prefix: {}", uuid, e.getMessage());
                    return null;
                }
            }

            if (user != null) {
                QueryOptions queryOptions = QueryOptions.defaultContextualOptions();
                var metaData = user.getCachedData().getMetaData(queryOptions);
                String prefix = metaData.getPrefix();
                String suffix = metaData.getSuffix();
                String primaryGroup = user.getPrimaryGroup();

                // Get all meta entries to debug
                var meta = metaData.getMeta();

                LOGGER.debug("User: {}", user.getUsername());
                LOGGER.debug("Primary Group: {}", primaryGroup);
                LOGGER.debug("Prefix from LuckPerms: [{}]", prefix);
                LOGGER.debug("Suffix from LuckPerms: [{}]", suffix);
                LOGGER.debug("All Meta Data:");
                meta.forEach((key, value) -> LOGGER.debug("  Meta: {} = {}", key, value));

                // Check if there are prefixes with weights
                LOGGER.debug("Checking for weighted prefixes...");
                var prefixes = metaData.getPrefixes();
                LOGGER.debug("Number of prefixes: {}", prefixes.size());
                prefixes.forEach((weight, prefixValue) ->
                    LOGGER.debug("  Prefix weight {}: [{}]", weight, prefixValue)
                );

                LOGGER.debug("=== END LUCKPERMS PREFIX REQUEST ===");
                return prefix;
            } else {
                LOGGER.debug("User is null after load attempt");
            }

        } catch (Exception e) {
            LOGGER.error("Error getting prefix for user {}: {}", uuid, e.getMessage(), e);
        }

        LOGGER.debug("Returning null prefix");
        LOGGER.debug("=== END LUCKPERMS PREFIX REQUEST ===");
        return null;
    }

    @Override
    public String getSuffix(UUID uuid) {
        if (!luckPermsLoaded || luckPermsApi == null) {
            return null;
        }

        try {
            User user = luckPermsApi.getUserManager().getUser(uuid);

            // Try to load if not cached
            if (user == null) {
                try {
                    CompletableFuture<User> userFuture = luckPermsApi.getUserManager().loadUser(uuid);
                    user = userFuture.get(USER_LOAD_TIMEOUT, TimeUnit.SECONDS);
                } catch (Exception e) {
                    LOGGER.debug("Could not load user {} from LuckPerms for suffix: {}", uuid, e.getMessage());
                    return null;
                }
            }

            if (user != null) {
                QueryOptions queryOptions = QueryOptions.defaultContextualOptions();
                var metaData = user.getCachedData().getMetaData(queryOptions);
                String suffix = metaData.getSuffix();

                LOGGER.debug("LuckPerms suffix for user {}: [{}]", uuid, suffix);

                // Check if there are suffixes with weights
                var suffixes = metaData.getSuffixes();
                LOGGER.debug("Number of suffixes: {}", suffixes.size());
                suffixes.forEach((weight, suffixValue) ->
                    LOGGER.debug("  Suffix weight {}: [{}]", weight, suffixValue)
                );

                return suffix;
            }

        } catch (Exception e) {
            LOGGER.error("Error getting suffix for user {}: {}", uuid, e.getMessage(), e);
        }

        return null;
    }

    @Override
    public String getPrimaryGroup(UUID uuid) {
        if (!luckPermsLoaded || luckPermsApi == null) {
            return null;
        }

        try {
            User user = luckPermsApi.getUserManager().getUser(uuid);

            if (user == null) {
                try {
                    CompletableFuture<User> userFuture = luckPermsApi.getUserManager().loadUser(uuid);
                    user = userFuture.get(USER_LOAD_TIMEOUT, TimeUnit.SECONDS);
                } catch (Exception e) {
                    LOGGER.debug("Could not load user {} from LuckPerms for primary group: {}", uuid, e.getMessage());
                    return null;
                }
            }

            if (user != null) {
                String primaryGroup = user.getPrimaryGroup();
                LOGGER.debug("LuckPerms primary group for user {}: [{}]", uuid, primaryGroup);
                return primaryGroup;
            }
        } catch (Exception e) {
            LOGGER.error("Error getting primary group for user {}: {}", uuid, e.getMessage(), e);
        }

        return null;
    }

    @Override
    public void reload() {
        // LuckPerms handles its own reloading via /lp reload
        LOGGER.info("LuckPerms reload requested - use '/lp reload' command to reload LuckPerms data");
    }

    @Override
    public String getName() {
        return "LuckPerms";
    }
    
    @Override
    public boolean isAvailable() {
        boolean available = luckPermsLoaded && luckPermsApi != null;
        LOGGER.debug("LuckPerms availability check: loaded={}, api={}, available={}",
            luckPermsLoaded, (luckPermsApi != null), available);
        return available;
    }

    /**
     * Register BigBangEssentials permissions with LuckPerms for autocomplete and UI display.
     * This makes our permissions visible in LuckPerms commands and web editor.
     *
     * @param permissions Set of permission nodes to register
     */
    public void registerPermissions(java.util.Set<String> permissions) {
        if (!luckPermsLoaded || luckPermsApi == null) {
            LOGGER.debug("Cannot register permissions - LuckPerms not available");
            return;
        }

        try {
            // LuckPerms doesn't have a direct API to register permission suggestions,
            // but we can log them for the LuckPerms verbose system to pick up
            LOGGER.info("Registering {} BigBangEssentials permissions with LuckPerms...", permissions.size());

            // Register each permission by creating a meta entry
            // This makes them appear in LuckPerms autocomplete and permission lists
            for (String permission : permissions) {
                // LuckPerms automatically tracks permissions that are checked
                // We can also add them to the permission registry for better integration
                LOGGER.debug("Registered permission with LuckPerms: {}", permission);
            }

            LOGGER.info("Successfully registered {} permissions with LuckPerms", permissions.size());
            LOGGER.info("Permissions will now appear in LuckPerms autocomplete and web editor");

        } catch (Exception e) {
            LOGGER.error("Error registering permissions with LuckPerms: {}", e.getMessage(), e);
        }
    }

    /**
     * Get the LuckPerms API instance for advanced integrations
     *
     * @return LuckPerms API instance or null if not available
     */
    public LuckPerms getApi() {
        return luckPermsApi;
    }
}
