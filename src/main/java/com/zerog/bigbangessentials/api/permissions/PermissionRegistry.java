package com.zerog.bigbangessentials.api.permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all BigBangEssentials permission nodes.
 * This class automatically collects and manages all permission nodes used by the mod
 * for integration with permission plugins like PermissionsEX, LuckPerms, etc.
 */
public class PermissionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionRegistry.class);
    
    // Singleton pattern
    private static class SingletonHolder {
        private static final PermissionRegistry INSTANCE = new PermissionRegistry();
    }
    
    public static PermissionRegistry getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    // Storage for all permission nodes
    private final Set<String> registeredPermissions = ConcurrentHashMap.newKeySet();
    private final Map<String, PermissionInfo> permissionInfo = new ConcurrentHashMap<>();
    
    // Permission categories for organization
    public enum PermissionCategory {
        ADMIN("admin", "Administrative commands"),
    ECONOMY("economy", "Economy system"),
    TELEPORT("teleport", "Teleportation commands"),
    CHAT("chat", "Chat and messaging"),
    KITS("kits", "Kit system"),
    ITEMS("items", "Item management"),
    MODERATION("moderation", "Moderation commands"),
    PLAYER("player", "Player state commands"),
    MISC("misc", "Miscellaneous commands"),
    CORE("core", "Core functionality");

        private final String key;
        private final String description;
        
        PermissionCategory(String key, String description) {
            this.key = key;
            this.description = description;
        }
        
        public String getKey() { return key; }
        public String getDescription() { return description; }
    }
    
    // Permission info class
    public static class PermissionInfo {
        private final String permission;
        private final String description;
        private final PermissionCategory category;
        private final boolean defaultValue;
        
        public PermissionInfo(String permission, String description, PermissionCategory category, boolean defaultValue) {
            this.permission = permission;
            this.description = description;
            this.category = category;
            this.defaultValue = defaultValue;
        }
        
        public String getPermission() { return permission; }
        public String getDescription() { return description; }
        public PermissionCategory getCategory() { return category; }
        public boolean getDefaultValue() { return defaultValue; }
    }
    
    private PermissionRegistry() {
        // Initialize with all known permission nodes
        registerAllPermissions();
        
        // Automatically discover and register ALL permissions from the codebase
        autoDiscoverPermissions();
    }
    
    /**
     * Register a permission node with metadata
     */
    public void register(String permission, String description, PermissionCategory category, boolean defaultValue) {
        if (permission == null || permission.trim().isEmpty()) {
            LOGGER.warn("Attempted to register empty or null permission");
            return;
        }
        
        permission = permission.trim();
        
        // Validate permission format
        if (!isValidPermission(permission)) {
            LOGGER.warn("Invalid permission format: {}", permission);
            return;
        }
        
        registeredPermissions.add(permission);
        permissionInfo.put(permission, new PermissionInfo(permission, description, category, defaultValue));
        
        LOGGER.debug("Registered permission: {} ({})", permission, category.getKey());
    }
    
    /**
     * Register a permission node with default settings
     */
    public void register(String permission, String description, PermissionCategory category) {
        register(permission, description, category, false);
    }
    
    /**
     * Register a permission node with minimal info
     */
    public void register(String permission) {
        register(permission, "Permission for " + permission, PermissionCategory.MISC, false);
    }
    
    /**
     * Get all registered permissions
     */
    public Set<String> getAllPermissions() {
        return Collections.unmodifiableSet(registeredPermissions);
    }
    
    /**
     * Get all permissions for a specific category
     */
    public Set<String> getPermissionsByCategory(PermissionCategory category) {
        return permissionInfo.values().stream()
                .filter(info -> info.getCategory() == category)
                .map(PermissionInfo::getPermission)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
    }
    
    /**
     * Get permission info
     */
    public PermissionInfo getPermissionInfo(String permission) {
        return permissionInfo.get(permission);
    }
    
    /**
     * Check if a permission is registered
     */
    public boolean isRegistered(String permission) {
        return registeredPermissions.contains(permission);
    }
    
    /**
     * Get all permissions starting with a prefix (for tab completion)
     */
    public List<String> getPermissionsStartingWith(String prefix) {
        return registeredPermissions.stream()
                .filter(perm -> perm.startsWith(prefix.toLowerCase()))
                .sorted()
                .toList();
    }
    
    /**
     * Get all BigBangEssentials permissions (for tab completion)
     * Includes both registered and discovered permissions
     */
    public List<String> getBigBangEssentialsPermissions() {
        // Get discovered permissions from scanner as well
        PermissionScanner scanner = PermissionScanner.getInstance();
        scanner.scanForPermissions();
        
        // Combine registered and discovered permissions
        java.util.Set<String> allBigBangEssentialsPermissions = new java.util.HashSet<>(getPermissionsStartingWith("bigbangessentials."));
        allBigBangEssentialsPermissions.addAll(scanner.getDiscoveredPermissions().stream()
                .filter(perm -> perm.startsWith("bigbangessentials."))
                .toList());
        
        return allBigBangEssentialsPermissions.stream().sorted().toList();
    }
    
    /**
     * Validate permission format
     */
    private boolean isValidPermission(String permission) {
        return permission.matches("^[a-z0-9._-]+$") && permission.startsWith("bigbangessentials.");
    }
    
    /**
     * Register all known permission nodes
     */
    private void registerAllPermissions() {
        LOGGER.info("Registering BigBangEssentials permission nodes...");
        
        // Core permissions
        register("bigbangessentials.use", "Basic mod usage", PermissionCategory.CORE, true);
        register("bigbangessentials.admin", "Administrative access", PermissionCategory.ADMIN, false);
        register("bigbangessentials.reload", "Reload configuration", PermissionCategory.ADMIN, false);
        
        // Economy permissions
        register("bigbangessentials.economy.balance", "Check own balance", PermissionCategory.ECONOMY, true);
        register("bigbangessentials.economy.balance.others", "Check others' balance", PermissionCategory.ECONOMY, false);
        register("bigbangessentials.economy.pay", "Send payments to online players", PermissionCategory.ECONOMY, true);
        register("bigbangessentials.economy.pay.offline", "Send payments to offline players", PermissionCategory.ECONOMY, false);
        register("bigbangessentials.economy.pay.toggle", "Toggle payment acceptance", PermissionCategory.ECONOMY, true);
        register("bigbangessentials.economy.baltop", "View balance leaderboard", PermissionCategory.ECONOMY, true);
        register("bigbangessentials.economy.baltop.exempt", "Exclude self from baltop ranking", PermissionCategory.ECONOMY, false);
        register("bigbangessentials.economy.admin", "Economy administration", PermissionCategory.ECONOMY, false);
        register("bigbangessentials.economy.admin.give", "Give money to players", PermissionCategory.ECONOMY, false);
        register("bigbangessentials.economy.admin.take", "Take money from players", PermissionCategory.ECONOMY, false);
        register("bigbangessentials.economy.eco", "Run /eco admin commands (give/take/set/reset)", PermissionCategory.ECONOMY, false);
        register("bigbangessentials.economy.admin.set", "Set player balance", PermissionCategory.ECONOMY, false);
        // Worth / Sell
        register("bigbangessentials.worth", "Check the sell value of an item (/worth)", PermissionCategory.ECONOMY, true);
        register("bigbangessentials.sell", "Use the /sell command", PermissionCategory.ECONOMY, true);
        register("bigbangessentials.sell.hand", "Sell item in hand (/sell hand)", PermissionCategory.ECONOMY, true);
        register("bigbangessentials.sell.bulk", "Sell entire inventory (/sell inventory|all)", PermissionCategory.ECONOMY, true);
        register("bigbangessentials.setworth", "Set item sell prices (/setworth)", PermissionCategory.ECONOMY, false);

        // Player-state / admin tool permissions
        register("bigbangessentials.fly", "Toggle flight mode", PermissionCategory.PLAYER, false);
        register("bigbangessentials.fly.others", "Toggle flight for other players", PermissionCategory.PLAYER, false);
        register("bigbangessentials.god", "Toggle god mode (invincibility)", PermissionCategory.PLAYER, false);
        register("bigbangessentials.god.others", "Toggle god mode for other players", PermissionCategory.PLAYER, false);
        register("bigbangessentials.heal", "Restore own health and hunger", PermissionCategory.PLAYER, false);
        register("bigbangessentials.heal.others", "Restore another player's health", PermissionCategory.PLAYER, false);
        register("bigbangessentials.feed", "Restore own hunger", PermissionCategory.PLAYER, false);
        register("bigbangessentials.feed.others", "Restore another player's hunger", PermissionCategory.PLAYER, false);
        register("bigbangessentials.speed", "Set walk/fly speed", PermissionCategory.PLAYER, false);
        register("bigbangessentials.speed.others", "Set another player's speed", PermissionCategory.PLAYER, false);
        register("bigbangessentials.ext", "Extinguish self", PermissionCategory.PLAYER, true);
        register("bigbangessentials.ext.others", "Extinguish another player", PermissionCategory.PLAYER, false);
        register("bigbangessentials.burn", "Set a player on fire", PermissionCategory.PLAYER, false);
        register("bigbangessentials.give", "Give items to players", PermissionCategory.PLAYER, false);
        register("bigbangessentials.more", "Fill held stack to max", PermissionCategory.PLAYER, false);
        register("bigbangessentials.hat", "Wear held item as helmet", PermissionCategory.PLAYER, false);
        register("bigbangessentials.exp", "View XP info", PermissionCategory.PLAYER, true);
        register("bigbangessentials.exp.set", "Set own XP", PermissionCategory.PLAYER, false);
        register("bigbangessentials.exp.set.others", "Set another player's XP", PermissionCategory.PLAYER, false);
        register("bigbangessentials.exp.give", "Give XP to self", PermissionCategory.PLAYER, false);
        register("bigbangessentials.exp.give.others", "Give XP to another player", PermissionCategory.PLAYER, false);
        register("bigbangessentials.sudo", "Run commands as another player", PermissionCategory.PLAYER, false);
        register("bigbangessentials.sudo.exempt", "Cannot be sudo'd by non-console", PermissionCategory.PLAYER, false);
        register("bigbangessentials.playtime", "View own playtime", PermissionCategory.PLAYER, true);
        register("bigbangessentials.playtime.others", "View another player's playtime", PermissionCategory.PLAYER, false);
        // Server admin commands
        register("bigbangessentials.broadcast", "Broadcast a message to all players", PermissionCategory.ADMIN, false);
        register("bigbangessentials.time", "View current world time", PermissionCategory.ADMIN, false);
        register("bigbangessentials.time.set", "Set or add world time", PermissionCategory.ADMIN, false);
        register("bigbangessentials.weather", "Set world weather", PermissionCategory.ADMIN, false);
        register("bigbangessentials.kill", "Kill players", PermissionCategory.ADMIN, false);
        register("bigbangessentials.kill.exempt", "Exempt from being killed by /kill", PermissionCategory.ADMIN, false);
        register("bigbangessentials.kill.force", "Force kill even exempt players", PermissionCategory.ADMIN, false);
        register("bigbangessentials.gamemode", "Change own gamemode", PermissionCategory.ADMIN, false);
        register("bigbangessentials.gamemode.others", "Change another player's gamemode", PermissionCategory.ADMIN, false);
        register("bigbangessentials.teleport.tpo", "Teleport to player ignoring tptoggle", PermissionCategory.ADMIN, false);
        register("bigbangessentials.teleport.tpohere", "Bring player here ignoring tptoggle", PermissionCategory.ADMIN, false);
        register("bigbangessentials.teleport.tpoffline", "Teleport to offline player's last location", PermissionCategory.ADMIN, false);
        // Utility commands
        register("bigbangessentials.ptime", "Set own per-player time override", PermissionCategory.PLAYER, false);
        register("bigbangessentials.ptime.others", "Set another player's time override", PermissionCategory.ADMIN, false);
        register("bigbangessentials.pweather", "Set own per-player weather override", PermissionCategory.PLAYER, false);
        register("bigbangessentials.pweather.others", "Set another player's weather override", PermissionCategory.ADMIN, false);
        register("bigbangessentials.effect", "Apply potion effects to players", PermissionCategory.ADMIN, false);
        register("bigbangessentials.spawnmob", "Spawn entities at a player", PermissionCategory.ADMIN, false);
        register("bigbangessentials.spawnmob.others", "Spawn entities at another player", PermissionCategory.ADMIN, false);
        register("bigbangessentials.unlimited", "Toggle unlimited item use", PermissionCategory.ADMIN, false);
        register("bigbangessentials.unlimited.others", "Toggle unlimited items for another player", PermissionCategory.ADMIN, false);
        register("bigbangessentials.condense", "Condense items to storage blocks", PermissionCategory.PLAYER, false);
        // Item customisation & misc
        register("bigbangessentials.me", "Broadcast action messages (/me)", PermissionCategory.CHAT, true);
        register("bigbangessentials.tptoggle", "Toggle teleport request acceptance", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.tptoggle.others", "Toggle tptoggle for another player", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.gc", "View server memory and TPS info", PermissionCategory.ADMIN, false);
        register("bigbangessentials.lightning", "Strike lightning at look target", PermissionCategory.ADMIN, false);
        register("bigbangessentials.lightning.others", "Strike lightning at a named player", PermissionCategory.ADMIN, false);
        register("bigbangessentials.skull", "Get a player head item", PermissionCategory.PLAYER, false);
        register("bigbangessentials.itemname", "Rename held item", PermissionCategory.ITEMS, false);
        register("bigbangessentials.itemlore", "Edit held item lore", PermissionCategory.ITEMS, false);
        register("bigbangessentials.remove", "Remove entities in a radius", PermissionCategory.ADMIN, false);
        register("bigbangessentials.loom", "Open portable loom", PermissionCategory.PLAYER, false);
        register("bigbangessentials.cartography", "Open portable cartography table", PermissionCategory.PLAYER, false);
        // Home & Warp Enhancements
        register("bigbangessentials.renamehome", "Rename own homes", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.renamehome.others", "Rename another player's homes", PermissionCategory.ADMIN, false);
        register("bigbangessentials.warpinfo", "Show warp location info", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.world", "Teleport to a world/dimension", PermissionCategory.ADMIN, false);
        register("bigbangessentials.world.others", "Teleport another player to a world", PermissionCategory.ADMIN, false);
        register("bigbangessentials.spawner", "Change a mob spawner type", PermissionCategory.ADMIN, false);
        register("bigbangessentials.spawner.*", "Change spawner to any mob type", PermissionCategory.ADMIN, false);
        register("bigbangessentials.recipe", "Show/unlock crafting recipe for an item", PermissionCategory.PLAYER, true);
        register("bigbangessentials.tpauto", "Auto-accept all incoming teleport requests", PermissionCategory.TELEPORT, true);
        // Fun / miscellaneous commands
        register("bigbangessentials.firework", "Edit held firework rockets", PermissionCategory.PLAYER, false);
        register("bigbangessentials.firework.fire", "Launch firework rockets with /firework fire", PermissionCategory.PLAYER, false);
        register("bigbangessentials.nuke", "Rain TNT on a player (/nuke)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.antioch", "Spawn lit TNT at look target (/antioch)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.kittycannon", "Launch exploding baby cat (/kittycannon)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.beezooka", "Launch angry bees (/beezooka)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.itemdb", "Look up item registry information (/itemdb)", PermissionCategory.PLAYER, false);
        register("bigbangessentials.potion", "Edit potion effects on held potion item", PermissionCategory.ITEMS, false);
        register("bigbangessentials.info", "View server info/MOTD (/info)", PermissionCategory.PLAYER, true);
        register("bigbangessentials.rest", "Reset sleep timer to prevent phantom spawning", PermissionCategory.PLAYER, true);
        register("bigbangessentials.rest.others", "Reset another player's sleep timer", PermissionCategory.ADMIN, false);
        register("bigbangessentials.backup", "Trigger server world save and backup", PermissionCategory.ADMIN, false);
        register("bigbangessentials.tpauto.others", "Toggle tpauto for another player", PermissionCategory.ADMIN, false);
        // World Interaction & Fun Commands
        register("bigbangessentials.fireball", "Shoot projectiles", PermissionCategory.ADMIN, false);
        register("bigbangessentials.fireball.*", "Shoot any projectile type", PermissionCategory.ADMIN, false);
        register("bigbangessentials.fireball.ride", "Ride a shot projectile", PermissionCategory.ADMIN, false);
        register("bigbangessentials.tree", "Grow a tree at look target", PermissionCategory.ADMIN, false);
        register("bigbangessentials.break", "Break the looked-at block instantly", PermissionCategory.ADMIN, false);
        register("bigbangessentials.break.bedrock", "Break bedrock blocks", PermissionCategory.ADMIN, false);
        register("bigbangessentials.ice", "Freeze self with ice", PermissionCategory.PLAYER, false);
        register("bigbangessentials.ice.others", "Freeze another player", PermissionCategory.ADMIN, false);
        register("bigbangessentials.bottom", "Teleport to world bottom at current XZ", PermissionCategory.PLAYER, false);
        register("bigbangessentials.tpaall", "Send tpa-here to all online players", PermissionCategory.ADMIN, false);
        register("bigbangessentials.tpaall.others", "Send tpaall on behalf of another player", PermissionCategory.ADMIN, false);
        register("bigbangessentials.broadcastworld", "Broadcast to players in your current world", PermissionCategory.ADMIN, false);
        // Player Info & Admin Tools
        register("bigbangessentials.seen", "View when a player was last online", PermissionCategory.PLAYER, true);
        register("bigbangessentials.near", "List nearby players", PermissionCategory.PLAYER, true);
        register("bigbangessentials.ping", "View your ping", PermissionCategory.PLAYER, true);
        register("bigbangessentials.ping.others", "View another player's ping", PermissionCategory.PLAYER, true);
        register("bigbangessentials.playtime", "View your total play time", PermissionCategory.PLAYER, true);
        register("bigbangessentials.playtime.others", "View another player's play time", PermissionCategory.PLAYER, true);
        register("bigbangessentials.whois", "View detailed player info", PermissionCategory.ADMIN, false);
        register("bigbangessentials.realname", "Look up real name from nickname", PermissionCategory.PLAYER, true);
        register("bigbangessentials.sudo", "Force a player to run a command", PermissionCategory.ADMIN, false);
        register("bigbangessentials.sudo.exempt", "Be immune to /sudo", PermissionCategory.ADMIN, false);
        register("bigbangessentials.suicide", "Kill yourself with /suicide", PermissionCategory.PLAYER, true);
        register("bigbangessentials.msgtoggle", "Toggle your incoming private messages", PermissionCategory.PLAYER, true);
        register("bigbangessentials.msgtoggle.others", "Toggle another player's messages", PermissionCategory.ADMIN, false);
        register("bigbangessentials.rtoggle", "Toggle reply-to-last-sender", PermissionCategory.PLAYER, true);
        register("bigbangessentials.rtoggle.others", "Toggle rtoggle for another player", PermissionCategory.ADMIN, false);
        register("bigbangessentials.motd", "View the message of the day", PermissionCategory.PLAYER, true);
        register("bigbangessentials.rules", "View server rules", PermissionCategory.PLAYER, true);

        // Teleportation permissions
        register("bigbangessentials.teleport.admin", "Administrative teleportation", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.admin.tp", "Teleport players", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.admin.tphere", "Teleport players to you", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.admin.tpall", "Teleport all players", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.admin.tppos", "Teleport to coordinates", PermissionCategory.TELEPORT, false);
        
        // Teleport requests
        register("bigbangessentials.teleport.request.tpa", "Send teleport requests", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.request.tpahere", "Request players teleport to you", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.request.accept", "Accept teleport requests", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.request.deny", "Deny teleport requests", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.request.cancel", "Cancel sent teleport requests", PermissionCategory.TELEPORT, true);
        
        // Home system
        register("bigbangessentials.teleport.home", "Use home system", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.home.set", "Set home locations", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.home.delete", "Delete home locations", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.home.list", "List home locations", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.home.others", "Access others' homes", PermissionCategory.TELEPORT, false);
        
        // Dynamic home limit permissions
        // Pattern: bigbangessentials.home.<amount> where <amount> is 1-100
        // Example: bigbangessentials.home.5 allows 5 homes
        // Note: These are checked dynamically, not registered individually
        // The highest matching permission wins, or config default is used
        
        // Warp system
        register("bigbangessentials.teleport.warp", "Use warp system", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.warp.list", "List all available warps", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.warp.others", "Warp another player to a warp (/warp <name> <player>)", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.warp.create", "Create warps", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.warp.delete", "Delete warps", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.warps.*", "Access ALL warps regardless of per-warp permissions", PermissionCategory.TELEPORT, false);

        // Dynamic player warp limit permissions
        // Pattern: bigbangessentials.warp.limit.<amount> where <amount> is 1-100
        // Example: bigbangessentials.warp.limit.10 allows 10 player warps
        // Special: bigbangessentials.warp.limit.unlimited allows unlimited player warps
        register("bigbangessentials.warp.limit.unlimited", "Unlimited player warps", PermissionCategory.TELEPORT, false);
        
        // Spawn system
        register("bigbangessentials.teleport.spawn", "Use spawn teleportation", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.spawn.set", "Set spawn location", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.spawn.info", "View spawn information", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.spawn.clear", "Clear spawn location", PermissionCategory.TELEPORT, false);
        
        // Misc teleport
        register("bigbangessentials.teleport.back", "Use back teleportation", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.death", "Teleport to death location", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.top", "Teleport to highest block", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.jump", "Teleport through walls", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.jumpto", "Teleport to looking at", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.tpr", "Random teleportation", PermissionCategory.TELEPORT, true);
        
        // Direct teleport - others access
        register("bigbangessentials.teleport.admin.tpo", "Teleport other players to locations", PermissionCategory.TELEPORT, false);
        
        // Kit system
        register("bigbangessentials.kits.use", "Use kit system", PermissionCategory.KITS, true);
        register("bigbangessentials.kits.list", "List available kits", PermissionCategory.KITS, true);
        register("bigbangessentials.kits.nocooldown", "Bypass kit cooldowns", PermissionCategory.KITS, false);
        register("bigbangessentials.kit.others", "Give a kit to another player (/kit <name> <player>)", PermissionCategory.KITS, false);
        register("bigbangessentials.kitreset", "Reset own kit cooldown", PermissionCategory.KITS, false);
        register("bigbangessentials.kitreset.others", "Reset another player's kit cooldown", PermissionCategory.KITS, false);
        register("bigbangessentials.kits.admin", "Kit administration", PermissionCategory.KITS, false);
        register("bigbangessentials.kits.admin.create", "Create kits", PermissionCategory.KITS, false);
        register("bigbangessentials.kits.admin.delete", "Delete kits", PermissionCategory.KITS, false);
        register("bigbangessentials.kits.admin.list", "List all kits (admin)", PermissionCategory.KITS, false);
        
        // Individual kit permissions (will be added dynamically)
        // These follow the pattern: bigbangessentials.kits.<kitname>
        // Cooldown exemption can also be per-kit: bigbangessentials.kits.<kitname>.nocooldown
        
        // Item management
        register("bigbangessentials.item.repair", "Repair items", PermissionCategory.ITEMS, false);
        register("bigbangessentials.item.enchant", "Enchant items", PermissionCategory.ITEMS, false);
        register("bigbangessentials.item.enchant.unsafe", "Unsafe enchanting", PermissionCategory.ITEMS, false);
        register("bigbangessentials.item.enchant.others", "Enchant others' items", PermissionCategory.ITEMS, false);
        register("bigbangessentials.item.powertool", "Use powertools", PermissionCategory.ITEMS, false);
        register("bigbangessentials.item.powertool.toggle", "Toggle powertools", PermissionCategory.ITEMS, false);
        register("bigbangessentials.item.dispose", "Use disposal system", PermissionCategory.ITEMS, true);
        register("bigbangessentials.item.clearinventory", "Clear inventory", PermissionCategory.ITEMS, false);
        register("bigbangessentials.item.clearinventory.others", "Clear others' inventory", PermissionCategory.ITEMS, false);
        // Inventory viewing/editing permissions
        register("bigbangessentials.invsee", "View other players' inventories", PermissionCategory.ITEMS, false);
        register("bigbangessentials.invsee.edit", "Edit other players' inventories", PermissionCategory.ITEMS, false);
        register("bigbangessentials.enderchest", "View other players' ender chests", PermissionCategory.ITEMS, false);
        register("bigbangessentials.enderchest.edit", "Edit other players' ender chests", PermissionCategory.ITEMS, false);

        // Chat system
        register("bigbangessentials.chat.msg", "Send private messages", PermissionCategory.CHAT, true);
        register("bigbangessentials.chat.reply", "Reply to messages", PermissionCategory.CHAT, true);
        register("bigbangessentials.chat.ignore", "Ignore players", PermissionCategory.CHAT, true);
        register("bigbangessentials.chat.unignore", "Unignore players", PermissionCategory.CHAT, true);
        register("bigbangessentials.chat.msgtoggle", "Toggle message acceptance", PermissionCategory.CHAT, true);
        register("bigbangessentials.chat.socialspy", "Use social spy", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.mute", "Mute players", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.unmute", "Unmute players", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.mutelist", "View mute list", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.exempt", "Exempt from muting", PermissionCategory.CHAT, false);
        
        // Chat formatting and colors
        register("bigbangessentials.chat.color", "Use basic color codes in chat (&0-9, &a-f)", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.color.hex", "Use hex colors in chat (&#RRGGBB)", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.format", "Use formatting codes in chat (&k-o, &r)", PermissionCategory.CHAT, false);

        // Chat channels and features
        register("bigbangessentials.chat.channel.local", "Use local chat channel", PermissionCategory.CHAT, true);
        register("bigbangessentials.chat.channel.global", "Use global chat channel", PermissionCategory.CHAT, true);
        register("bigbangessentials.chat.staff", "Access to staff chat channel", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.mention", "Mention other players with @name", PermissionCategory.CHAT, true);
        register("bigbangessentials.chat.mention.all", "Mention everyone with @everyone", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.itemlink", "Show held item in chat with [item]", PermissionCategory.CHAT, true);

        // Chat anti-spam bypasses (Phase 3)
        register("bigbangessentials.chat.caps.bypass", "Bypass caps filter", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.repeat.bypass", "Bypass repeat message filter", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.links.bypass", "Bypass link filter", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.spam.bypass", "Bypass spam rate limit", PermissionCategory.CHAT, false);

        // Rich text effects (Phase 4)
        register("bigbangessentials.chat.richtext", "Use rich text effects (gradients, rainbow)", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.gradient", "Use gradient text effects", PermissionCategory.CHAT, false);
        register("bigbangessentials.chat.rainbow", "Use rainbow text effects", PermissionCategory.CHAT, false);

        // AFK system
        register("bigbangessentials.afk", "Use AFK system", PermissionCategory.MISC, true);
        register("bigbangessentials.afk.exempt", "Exempt from AFK kick", PermissionCategory.MISC, false);
        
        // Portable workstations
        register("bigbangessentials.anvil", "Open portable anvil", PermissionCategory.MISC, true);
        register("bigbangessentials.crafting", "Open portable crafting table", PermissionCategory.MISC, true);
        register("bigbangessentials.grindstone", "Open portable grindstone", PermissionCategory.MISC, true);
        register("bigbangessentials.smithing", "Open portable smithing table", PermissionCategory.MISC, true);
        register("bigbangessentials.stonecutting", "Open portable stonecutter", PermissionCategory.MISC, true);

        // Utility commands
        register("bigbangessentials.realname", "Find player by nickname", PermissionCategory.MISC, true);
        register("bigbangessentials.whois", "View player information", PermissionCategory.MISC, true);
        register("bigbangessentials.whois.detailed", "View detailed player information", PermissionCategory.MISC, false);
        register("bigbangessentials.seen", "Check when player was last seen", PermissionCategory.MISC, true);
        register("bigbangessentials.sign", "Edit sign text", PermissionCategory.MISC, true);
        register("bigbangessentials.sign.colors", "Use colors in signs", PermissionCategory.MISC, false);
        register("bigbangessentials.rules", "View server rules", PermissionCategory.MISC, true);
        register("bigbangessentials.rules.admin", "Manage server rules", PermissionCategory.ADMIN, false);
        register("bigbangessentials.suicide", "Use suicide command", PermissionCategory.MISC, true);
        register("bigbangessentials.ping", "Check own ping", PermissionCategory.MISC, true);
        register("bigbangessentials.ping.others", "Check others' ping", PermissionCategory.MISC, false);
        register("bigbangessentials.book", "Give yourself a writable book", PermissionCategory.MISC, true);
        register("bigbangessentials.book.unlock", "Unlock a written book for editing", PermissionCategory.MISC, false);
        register("bigbangessentials.book.title", "Set the title of a written book", PermissionCategory.MISC, false);
        register("bigbangessentials.book.author", "Set the author of a written book", PermissionCategory.MISC, false);
        register("bigbangessentials.depth", "View depth/Y-level information", PermissionCategory.MISC, true);
        register("bigbangessentials.depth.others", "View others' depth information", PermissionCategory.MISC, false);
        register("bigbangessentials.gamemode", "Change own gamemode", PermissionCategory.MISC, false);
        register("bigbangessentials.gamemode.others", "Change others' gamemode", PermissionCategory.ADMIN, false);
        register("bigbangessentials.helpop", "Send a help request to staff", PermissionCategory.MISC, true);
        register("bigbangessentials.helpop.receive", "Receive help-op requests", PermissionCategory.MISC, false);

        // Permission system
        register("bigbangessentials.permissions.admin", "Permission system administration", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.reload", "Reload permissions", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.list", "List permissions", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.user", "User permission management", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group", "Group permission management", PermissionCategory.ADMIN, false);
        
        // Debug and info
        register("bigbangessentials.debug", "Debug mode access", PermissionCategory.ADMIN, false);
        register("bigbangessentials.info", "View mod information", PermissionCategory.MISC, true);

        // ── Moderation commands (actual permission nodes, not lang keys) ─────
        register("bigbangessentials.moderation.ban", "Ban players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.banip", "Ban IP addresses", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.banlist", "View ban list", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.tempban", "Temporarily ban players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.unban", "Unban players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.unbanip", "Unban IP addresses", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.kick", "Kick players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.kickall", "Kick all players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.freeze", "Freeze players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.unfreeze", "Unfreeze players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.freezeall", "Freeze all players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.unfreezeall", "Unfreeze all players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.freezelist", "View frozen players list", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.jail", "Jail players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.jail.timed", "Jail players for a set duration (/jailfor)", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.unjail", "Unjail players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.setjail", "Create jail locations", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.deljail", "Delete jail locations", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.jaillist", "View jailed players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.jailinfo", "View jail info", PermissionCategory.MODERATION, false);
        register("bigbangessentials.jail.allow-break", "Break blocks while jailed", PermissionCategory.MODERATION, false);
        register("bigbangessentials.jail.allow-place", "Place blocks while jailed", PermissionCategory.MODERATION, false);
        register("bigbangessentials.jail.allow-interact", "Interact with blocks/items while jailed", PermissionCategory.MODERATION, false);
        register("bigbangessentials.jail.allow-attack", "Attack entities while jailed", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.vanish", "Vanish self", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.vanish.others", "Vanish other players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.seevanished", "See vanished players", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.vanishlist", "View vanished players list", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.notify", "Receive moderation notifications", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.notifications", "Receive moderation event broadcasts", PermissionCategory.MODERATION, false);
        register("bigbangessentials.vanish.see", "See vanished players (alias)", PermissionCategory.MODERATION, false);

        // ── Utility / misc commands not yet registered ────────────────────────
        register("bigbangessentials.list", "View online player list", PermissionCategory.MISC, true);
        register("bigbangessentials.near", "View nearby players", PermissionCategory.MISC, true);
        register("bigbangessentials.nick", "Change own nickname", PermissionCategory.MISC, true);
        register("bigbangessentials.nick.color", "Use colour codes in nickname", PermissionCategory.MISC, false);
        register("bigbangessentials.nick.others", "Change other players' nicknames", PermissionCategory.MISC, false);
        register("bigbangessentials.staff", "Access staff chat and staff features", PermissionCategory.MISC, false);
        register("bigbangessentials.motd", "View MOTD", PermissionCategory.MISC, true);
        register("bigbangessentials.motd.set", "Set MOTD", PermissionCategory.ADMIN, false);
        register("bigbangessentials.motd.broadcast", "Broadcast MOTD", PermissionCategory.ADMIN, false);
        register("bigbangessentials.motd.reload", "Reload MOTD", PermissionCategory.ADMIN, false);

        // ── Mail system ───────────────────────────────────────────────────────
        register("bigbangessentials.mail", "Use mail system (read, delete, status)", PermissionCategory.CHAT, true);
        register("bigbangessentials.mail.send", "Send mail to players", PermissionCategory.CHAT, true);
        register("bigbangessentials.mail.sendtemp", "Send timed/expiring mail to a player", PermissionCategory.CHAT, true);
        register("bigbangessentials.mail.sendall", "Broadcast mail to all players", PermissionCategory.CHAT, false);
        register("bigbangessentials.mail.sendtempall", "Broadcast timed mail to all players", PermissionCategory.CHAT, false);
        register("bigbangessentials.mail.clear", "Clear own mail", PermissionCategory.CHAT, true);
        register("bigbangessentials.mail.clear.others", "Clear another player's mail (admin)", PermissionCategory.CHAT, false);
        register("bigbangessentials.mail.clearall", "Wipe every player's mailbox (admin)", PermissionCategory.CHAT, false);

        // ── Item system additions ─────────────────────────────────────────────
        register("bigbangessentials.item.enchant.any", "Enchant any item (ignore restrictions)", PermissionCategory.ITEMS, false);
        register("bigbangessentials.item.spawn", "Use /spawnitem command", PermissionCategory.ITEMS, false);

        // ── Teleport additions ────────────────────────────────────────────────
        register("bigbangessentials.teleport.settpr", "Set random teleport centre", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.tp", "Teleport self (alias)", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.tphere", "Teleport others to self (alias)", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.tppos", "Teleport to coordinates (alias)", PermissionCategory.TELEPORT, false);
        register("bigbangessentials.teleport.pwarp", "Use player warps", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.pwarp.create", "Create player warps", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.pwarp.delete", "Delete player warps", PermissionCategory.TELEPORT, true);
        register("bigbangessentials.teleport.pwarp.list", "List player warps", PermissionCategory.TELEPORT, true);

        // ── Kits additions ────────────────────────────────────────────────────
        register("bigbangessentials.kits.create", "Create kits via /createkit", PermissionCategory.KITS, false);
        register("bigbangessentials.kits.delete", "Delete kits via /delkit", PermissionCategory.KITS, false);
        register("bigbangessentials.kits.override", "Override kit restrictions", PermissionCategory.KITS, false);

        // ── Permissions sub-command nodes ─────────────────────────────────────
        register("bigbangessentials.permissions.check", "Check a player's permissions", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.search", "Search permissions", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.list.groups", "List permission groups", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.list.users", "List permission users", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.info.user", "View user permission info", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.info.group", "View group permission info", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.user.permissions", "Manage user permission nodes", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.user.groups", "Manage user group membership", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.user.clear", "Clear all user permissions", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group.create", "Create permission groups", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group.delete", "Delete permission groups", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group.rename", "Rename permission groups", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group.clone", "Clone permission groups", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group.inherit", "Set group inheritance", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group.permissions", "Manage group permission nodes", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group.modify", "Modify group settings", PermissionCategory.ADMIN, false);
        register("bigbangessentials.permissions.group.clear", "Clear all group permissions", PermissionCategory.ADMIN, false);

        // ── Player-state / admin tool commands ───────────────────────────────
        register("bigbangessentials.fly", "Toggle flight mode", PermissionCategory.PLAYER, false);
        register("bigbangessentials.fly.others", "Toggle flight for other players", PermissionCategory.PLAYER, false);
        register("bigbangessentials.god", "Toggle god mode (invincibility)", PermissionCategory.PLAYER, false);
        register("bigbangessentials.god.others", "Toggle god mode for other players", PermissionCategory.PLAYER, false);
        register("bigbangessentials.heal", "Restore own health and hunger", PermissionCategory.PLAYER, false);
        register("bigbangessentials.heal.others", "Restore another player's health", PermissionCategory.PLAYER, false);
        register("bigbangessentials.feed", "Restore own hunger", PermissionCategory.PLAYER, false);
        register("bigbangessentials.feed.others", "Restore another player's hunger", PermissionCategory.PLAYER, false);
        register("bigbangessentials.speed", "Set walk/fly speed", PermissionCategory.PLAYER, false);
        register("bigbangessentials.speed.others", "Set another player's speed", PermissionCategory.PLAYER, false);
        register("bigbangessentials.ext", "Extinguish self", PermissionCategory.PLAYER, true);
        register("bigbangessentials.ext.others", "Extinguish another player", PermissionCategory.PLAYER, false);
        register("bigbangessentials.burn", "Set a player on fire", PermissionCategory.PLAYER, false);
        register("bigbangessentials.give", "Give items to players", PermissionCategory.PLAYER, false);
        register("bigbangessentials.more", "Fill held stack to max", PermissionCategory.PLAYER, false);
        register("bigbangessentials.hat", "Wear held item as helmet", PermissionCategory.PLAYER, false);
        register("bigbangessentials.exp", "View XP info", PermissionCategory.PLAYER, true);
        register("bigbangessentials.exp.set", "Set own XP", PermissionCategory.PLAYER, false);
        register("bigbangessentials.exp.set.others", "Set another player's XP", PermissionCategory.PLAYER, false);
        register("bigbangessentials.exp.give", "Give XP to self", PermissionCategory.PLAYER, false);
        register("bigbangessentials.exp.give.others", "Give XP to another player", PermissionCategory.PLAYER, false);
        register("bigbangessentials.sudo", "Run commands as another player", PermissionCategory.PLAYER, false);
        register("bigbangessentials.sudo.exempt", "Cannot be sudo'd by non-console", PermissionCategory.PLAYER, false);
        register("bigbangessentials.playtime", "View own playtime", PermissionCategory.PLAYER, true);
        register("bigbangessentials.playtime.others", "View another player's playtime", PermissionCategory.PLAYER, false);

        // ── Dashboard ────────────────────────────────────────────────────────        register("bigbangessentials.admin.dashboard", "Access web dashboard (admin)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.dashboard.access", "Register and access the web dashboard", PermissionCategory.MISC, false);
        register("bigbangessentials.dashboard.view", "View-only dashboard access", PermissionCategory.MISC, false);
        register("bigbangessentials.dashboard.manage", "Manage dashboard settings", PermissionCategory.ADMIN, false);
        register("bigbangessentials.dashboard.moderator", "Moderator dashboard access", PermissionCategory.MODERATION, false);
        register("bigbangessentials.dashboard.admin", "Full admin dashboard access", PermissionCategory.ADMIN, false);

        register("bigbangessentials.item", "Give yourself an item by name (/item)", PermissionCategory.ITEMS, false);
        register("bigbangessentials.rtoggle", "Toggle /r reply direction", PermissionCategory.CHAT, true);
        register("bigbangessentials.rtoggle.others", "Toggle /r reply direction for another player", PermissionCategory.ADMIN, false);
        register("bigbangessentials.help", "View command help list", PermissionCategory.MISC, true);
        register("bigbangessentials.moderation.tempbanip", "Temporarily ban an IP address", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.togglejail", "Toggle a player's jail state", PermissionCategory.MODERATION, false);
        register("bigbangessentials.moderation.jailinfo", "View jail location info", PermissionCategory.MODERATION, false);
        register("bigbangessentials.powertooltoggle", "Toggle all powertools on/off globally", PermissionCategory.ITEMS, true);
        register("bigbangessentials.tablist.admin", "Manage the custom tablist system", PermissionCategory.ADMIN, false);

        // ── Fun / miscellaneous commands ─────────────────────────────────────
        register("bigbangessentials.firework", "Edit held firework rockets", PermissionCategory.PLAYER, false);
        register("bigbangessentials.firework.fire", "Launch firework rockets with /firework fire", PermissionCategory.PLAYER, false);
        register("bigbangessentials.nuke", "Rain TNT on a player (/nuke)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.antioch", "Spawn lit TNT at look target (/antioch)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.kittycannon", "Launch exploding baby cat (/kittycannon)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.beezooka", "Launch angry bees (/beezooka)", PermissionCategory.ADMIN, false);
        register("bigbangessentials.itemdb", "Look up item registry information", PermissionCategory.PLAYER, false);
        register("bigbangessentials.potion", "Edit potion effects on held potion item", PermissionCategory.ITEMS, false);
        register("bigbangessentials.info", "View server info/MOTD (/info)", PermissionCategory.PLAYER, true);
        register("bigbangessentials.rest", "Reset sleep timer to prevent phantom spawning", PermissionCategory.PLAYER, true);
        register("bigbangessentials.rest.others", "Reset another player's sleep timer", PermissionCategory.ADMIN, false);
        register("bigbangessentials.backup", "Trigger server world save and backup", PermissionCategory.ADMIN, false);
        register("bigbangessentials.showkit", "Preview kit contents without claiming", PermissionCategory.PLAYER, true);
        register("bigbangessentials.powertoollist", "List all active powertool bindings", PermissionCategory.PLAYER, true);
        register("bigbangessentials.customtext", "Display custom server text pages", PermissionCategory.PLAYER, true);
        register("bigbangessentials.payconfirmtoggle", "Toggle payment confirmation prompts", PermissionCategory.PLAYER, true);
        register("bigbangessentials.ciconfirmtoggle", "Toggle /clearinventory confirmation prompts", PermissionCategory.PLAYER, true);

        LOGGER.info("Registered {} permission nodes", registeredPermissions.size());
    }
    
    /**
     * Add kit permission dynamically when a kit is created
     */
    public void registerKitPermission(String kitName) {
        if (kitName == null || kitName.trim().isEmpty()) return;
        
        String permission = "bigbangessentials.kits." + kitName.toLowerCase();
        String nocooldownPermission = permission + ".nocooldown";
        
        register(permission, "Use kit: " + kitName, PermissionCategory.KITS, false);
        register(nocooldownPermission, "Bypass cooldown for kit: " + kitName, PermissionCategory.KITS, false);
    }
    
    /**
     * Remove kit permission when a kit is deleted
     */
    public void unregisterKitPermission(String kitName) {
        if (kitName == null || kitName.trim().isEmpty()) return;
        
        String permission = "bigbangessentials.kits." + kitName.toLowerCase();
        String nocooldownPermission = permission + ".nocooldown";
        
        registeredPermissions.remove(permission);
        permissionInfo.remove(permission);
        registeredPermissions.remove(nocooldownPermission);
        permissionInfo.remove(nocooldownPermission);
        
        LOGGER.debug("Unregistered kit permissions: {} and {}", permission, nocooldownPermission);
    }
    
    /**
     * Get summary of registered permissions by category
     */
    @SuppressWarnings("unused") // Public API method
    public Map<PermissionCategory, Integer> getPermissionSummary() {
        Map<PermissionCategory, Integer> summary = new EnumMap<>(PermissionCategory.class);
        
        for (PermissionCategory category : PermissionCategory.values()) {
            summary.put(category, getPermissionsByCategory(category).size());
        }
        
        return summary;
    }
    
    /**
     * Automatically discover and register permissions from the codebase
     */
    private void autoDiscoverPermissions() {
        LOGGER.info("Starting automatic permission discovery...");
        
        try {
            // Get the permission scanner and scan for permissions
            PermissionScanner scanner = PermissionScanner.getInstance();
            scanner.scanForPermissions();
            
            // Register all discovered permissions
            Set<String> discoveredPermissions = scanner.getDiscoveredPermissions();
            
            for (String permission : discoveredPermissions) {
                if (!isRegistered(permission)) {
                    // Auto-categorize based on permission structure
                    PermissionCategory category = categorizePermission(permission);
                    register(permission, "Auto-discovered permission", category, false);
                }
            }
            
            LOGGER.info("Auto-discovery completed: {} permissions discovered, {} new permissions registered", 
                discoveredPermissions.size(), 
                discoveredPermissions.stream().mapToInt(p -> isRegistered(p) ? 0 : 1).sum());
                
        } catch (Exception e) {
            LOGGER.error("Error during automatic permission discovery", e);
        }
    }
    
    /**
     * Categorize a permission based on its structure
     */
    private PermissionCategory categorizePermission(String permission) {
        String[] parts = permission.split("\\.");
        
        if (parts.length >= 2) {
            String category = parts[1].toLowerCase();
            
        return switch (category) {
            case "economy", "eco", "balance", "pay", "money" -> PermissionCategory.ECONOMY;
            case "teleport", "tp", "tpa", "home", "warp", "spawn" -> PermissionCategory.TELEPORT;
            case "chat", "msg", "message", "reply", "socialspy", "mute", "ignore" -> PermissionCategory.CHAT;
            case "kit", "kits" -> PermissionCategory.KITS;
            case "item", "items", "give", "enchant", "repair" -> PermissionCategory.ITEMS;
            case "moderation", "mod", "ban", "kick", "freeze", "jail", "vanish" -> PermissionCategory.MODERATION;
            case "admin", "reload", "permissions", "debug" -> PermissionCategory.ADMIN;
            case "fly", "god", "heal", "feed", "speed", "ext", "burn", "more", "hat", "exp", "sudo", "playtime" -> PermissionCategory.PLAYER;
            default -> PermissionCategory.MISC;
        };
        }
        
        return PermissionCategory.CORE;
    }
    
    /**
     * Refresh permissions by re-scanning the codebase (useful for development)
     */
    public void refreshPermissions() {
        LOGGER.info("Refreshing permission registry...");
        
        int initialCount = registeredPermissions.size();
        autoDiscoverPermissions();
        int finalCount = registeredPermissions.size();
        
        LOGGER.info("Permission refresh completed: {} -> {} permissions (+" + (finalCount - initialCount) + " new)", 
            initialCount, finalCount);
    }
    
    /**
     * Get all auto-discovered permissions (separate from manual registrations)
     */
    public Set<String> getAutoDiscoveredPermissions() {
        try {
            PermissionScanner scanner = PermissionScanner.getInstance();
            return scanner.getDiscoveredPermissions();
        } catch (Exception e) {
            LOGGER.error("Error getting auto-discovered permissions", e);
            return Collections.emptySet();
        }
    }
    
    /**
     * Export permissions to a readable format (for documentation)
     */
    public List<String> exportPermissions() {
        List<String> export = new ArrayList<>();
        export.add("# BigBangEssentials Permission Nodes");
        export.add("# Total: " + registeredPermissions.size() + " permissions");
        export.add("");
        
        for (PermissionCategory category : PermissionCategory.values()) {
            Set<String> categoryPerms = getPermissionsByCategory(category);
            if (categoryPerms.isEmpty()) continue;
            
            export.add("## " + category.getDescription() + " (" + categoryPerms.size() + ")");
            export.add("");
            
            categoryPerms.stream()
                    .sorted()
                    .forEach(perm -> {
                        PermissionInfo info = permissionInfo.get(perm);
                        export.add("- `" + perm + "` - " + info.getDescription() + 
                                  " (default: " + info.getDefaultValue() + ")");
                    });
            export.add("");
        }
        
        return export;
    }

    /**
     * Sync all registered permissions with LuckPerms (if available).
     * This makes BigBangEssentials permissions appear in LuckPerms autocomplete and web UI.
     * Call this after all permissions are registered.
     */
    public void syncWithLuckPerms() {
        try {
            // Check if we're using LuckPerms
            var externalAdapter = com.zerog.bigbangessentials.api.permissions.PermissionAPI.getExternalAdapter();

            if (externalAdapter instanceof com.zerog.bigbangessentials.permissions.LuckPermsAdapter luckPermsAdapter) {
                LOGGER.info("Syncing {} permissions with LuckPerms...", registeredPermissions.size());
                luckPermsAdapter.registerPermissions(registeredPermissions);

                LOGGER.info("✓ Permissions synced with LuckPerms");
                LOGGER.info("  - Permissions will now appear in LuckPerms autocomplete");
                LOGGER.info("  - Use '/lp info' to see registered permissions");
                LOGGER.info("  - Web editor will show BigBangEssentials permissions");

            } else {
                LOGGER.debug("LuckPerms not detected - skipping permission sync");
            }

        } catch (Exception e) {
            LOGGER.warn("Could not sync permissions with LuckPerms: {}", e.getMessage());
            LOGGER.debug("LuckPerms sync error details", e);
        }
    }

    /**
     * Export permissions in LuckPerms import format.
     * Can be used with /lp import command.
     *
     * @return YAML-formatted string for LuckPerms import
     */
    @SuppressWarnings("unused") // Public API method for LuckPerms integration
    public String exportForLuckPerms() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# BigBangEssentials Permissions for LuckPerms\n");
        yaml.append("# Generated on: ").append(java.time.LocalDateTime.now()).append("\n");
        yaml.append("# Total permissions: ").append(registeredPermissions.size()).append("\n");
        yaml.append("#\n");
        yaml.append("# To import: Save this file and run: /lp import <filename>\n");
        yaml.append("#\n\n");

        yaml.append("groups:\n");
        yaml.append("  default:\n");
        yaml.append("    permissions:\n");

        // Add all permissions that default to true
        for (String permission : registeredPermissions) {
            PermissionInfo info = permissionInfo.get(permission);
            if (info != null && info.getDefaultValue()) {
                yaml.append("      - ").append(permission).append("\n");
            }
        }

        yaml.append("\n");
        yaml.append("  admin:\n");
        yaml.append("    permissions:\n");
        yaml.append("      - bigbangessentials.*  # Grant all BigBangEssentials permissions\n");

        return yaml.toString();
    }
}

