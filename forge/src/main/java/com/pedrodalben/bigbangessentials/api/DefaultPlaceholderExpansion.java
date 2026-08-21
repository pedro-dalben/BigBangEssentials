package com.pedrodalben.bigbangessentials.api;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.Set;
import java.util.HashSet;

/**
 * Default placeholder expansion for BigBangEssentials.
 * Provides all the built-in placeholders that BigBangEssentials supports.
 */
public class DefaultPlaceholderExpansion extends PlaceholderExpansion {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlaceholderExpansion.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    
    private final Set<String> placeholders = new HashSet<>();
    
    public DefaultPlaceholderExpansion() {
        // Register all default placeholders
        initializePlaceholders();
    }
    
    private void initializePlaceholders() {
        // Player identity placeholders
        placeholders.add("displayname");
        placeholders.add("username");
        placeholders.add("name"); // alias for username
        
        // Permission system placeholders
        placeholders.add("prefix");
        placeholders.add("suffix");
        placeholders.add("group");
        placeholders.add("tag");
        placeholders.add("tag_name");
        placeholders.add("tag_format");
        
        // Location placeholders
        placeholders.add("world");
        placeholders.add("x");
        placeholders.add("y");
        placeholders.add("z");
        placeholders.add("biome");
        
        // Player status placeholders
        placeholders.add("health");
        placeholders.add("max_health");
        placeholders.add("food");
        placeholders.add("level");
        placeholders.add("exp");
        placeholders.add("gamemode");
        placeholders.add("ping");

        // Economy placeholders
        placeholders.add("balance");
        placeholders.add("balance_formatted");
        placeholders.add("gems");
        placeholders.add("gems_formatted");
        placeholders.add("gems_available");
        placeholders.add("gems_held");
        placeholders.add("gems_currency_name");
        placeholders.add("gems_currency_symbol");
        
        // Server placeholders
        placeholders.add("server_name");
        placeholders.add("online_players");
        placeholders.add("max_players");
        
        // Time placeholders
        placeholders.add("time");
        placeholders.add("time_24");
        placeholders.add("date");
        
        // AFK status placeholders
        placeholders.add("afk");
        placeholders.add("afk_time");
        placeholders.add("afk_reason");
        
        // RankUp placeholders
        placeholders.add("rankup_current_id");
        placeholders.add("rankup_current_name");
        placeholders.add("rankup_next_id");
        placeholders.add("rankup_next_name");
        placeholders.add("rankup_progress_percent");
        placeholders.add("rankup_money_required");
        placeholders.add("rankup_gems_required");
        placeholders.add("rankup_tasks_completed");
        placeholders.add("rankup_tasks_total");
        
        // Aliases for common shorthand use
        placeholders.add("online");      // alias for online_players
        placeholders.add("max");         // alias for max_players
        placeholders.add("tps");         // server TPS
        placeholders.add("bar");          // decorative progress bar

        LOGGER.debug("Initialized {} default placeholders", placeholders.size());
    }
    
    @Override
    public String getIdentifier() {
        return "bigbangessentials";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getAuthor() {
        return "ZeroG Network";
    }
    
    @Override
    public Set<String> getPlaceholders() {
        return new HashSet<>(placeholders);
    }
    
    @Override
    @Nullable
    public String onPlaceholderRequest(@Nullable ServerPlayer player, String identifier, @Nullable String params) {
        if (player == null && requiresPlayer(identifier)) {
            return null;
        }
        
        try {
            return switch (identifier.toLowerCase()) {
                // Player identity
                case "displayname", "username", "name" -> player != null ? com.pedrodalben.bigbangessentials.util.commands.NickCommand.getDisplayName(player) : null;
                case "realname" -> player != null ? player.getName().getString() : null;
                
                // Permission system
                case "prefix" -> getPlayerPrefix(player);
                case "suffix" -> getPlayerSuffix(player);
                case "group" -> getPlayerGroup(player);
                case "tag" -> getPlayerTag(player);
                case "tag_name" -> getPlayerTagName(player);
                case "tag_format" -> getPlayerTagFormat(player);
                
                // Location
                case "world" -> player != null ? getWorldName(player) : null;
                case "x" -> player != null ? String.valueOf((int) player.getX()) : null;
                case "y" -> player != null ? String.valueOf((int) player.getY()) : null;
                case "z" -> player != null ? String.valueOf((int) player.getZ()) : null;
                case "biome" -> player != null ? getBiome(player) : null;
                
                // Player status
                case "health" -> player != null ? DECIMAL_FORMAT.format(player.getHealth()) : null;
                case "max_health" -> player != null ? DECIMAL_FORMAT.format(player.getMaxHealth()) : null;
                case "food" -> player != null ? String.valueOf(player.getFoodData().getFoodLevel()) : null;
                case "level" -> player != null ? String.valueOf(player.experienceLevel) : null;
                case "exp" -> player != null ? (int) (player.experienceProgress * 100) + "%" : null;
                case "gamemode" -> player != null ? player.gameMode.getGameModeForPlayer().getName() : null;
                case "ping" -> player != null ? String.valueOf(player.latency) : null;

                // Economy
                case "balance" -> getBalance(player);
                case "balance_formatted" -> getFormattedBalance(player);
                case "gems" -> getGems(player);
                case "gems_formatted" -> getFormattedGems(player);
                case "gems_available" -> getAvailableGems(player);
                case "gems_held" -> getHeldGems(player);
                case "gems_currency_name" -> getGemsCurrencyName();
                case "gems_currency_symbol" -> getGemsCurrencySymbol();
                
                // Server
                case "server_name" -> getServerName(player);
                case "online", "online_players" -> getOnlinePlayerCount(player);
                case "max", "max_players" -> getMaxPlayerCount(player);
                case "tps" -> getTps(player);
                case "bar" -> "\u00a78\u00a7m                              \u00a7r";
                
                // Time
                case "time" -> getCurrentTime();
                case "time_24" -> getCurrentTime24();
                case "date" -> getCurrentDate();
                
                // AFK status
                case "afk" -> getAfkStatus(player);
                case "afk_time" -> getAfkTime(player);
                case "afk_reason" -> getAfkReason(player);
                
                // RankUp placeholders
                case "rankup_current_id", "rankup_current_name", "rankup_next_id", "rankup_next_name",
                     "rankup_progress_percent", "rankup_money_required", "rankup_gems_required",
                     "rankup_tasks_completed", "rankup_tasks_total" -> getRankupPlaceholder(player, identifier);
                
                default -> null;
            };
        } catch (Exception e) {
            LOGGER.error("Error resolving placeholder '{}': {}", identifier, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check if a placeholder requires a player context.
     */
    private boolean requiresPlayer(String identifier) {
        return switch (identifier.toLowerCase()) {
            case "server_name", "online_players", "max_players", "tps", "bar", "time", "time_24", "date", "gems_currency_name", "gems_currency_symbol" -> false;
            case "rankup_current_id", "rankup_current_name", "rankup_next_id", "rankup_next_name",
                 "rankup_progress_percent", "rankup_money_required", "rankup_gems_required",
                 "rankup_tasks_completed", "rankup_tasks_total" -> true;
            default -> true;
        };
    }
    
    /**
     * Get player's prefix from the permission system.
     */
    @Nullable
    private String getPlayerPrefix(@Nullable ServerPlayer player) {
        if (player == null) {
            LOGGER.warn("getPlayerPrefix called with null player");
            return null;
        }

        boolean debugEnabled = LOGGER.isDebugEnabled();
        if (debugEnabled) {
            LOGGER.debug(">>> DefaultPlaceholderExpansion.getPlayerPrefix() for: {}", player.getName().getString());
            LOGGER.debug(">>> Player UUID: {}", player.getUUID());
        }

        try {
            String prefix = PermissionAPI.getPrefix(player.getUUID());
            if (prefix == null) prefix = "";
            com.pedrodalben.bigbangessentials.economy.magnata.MagnataManager mm = com.pedrodalben.bigbangessentials.economy.magnata.MagnataManager.getInstance();
            if (mm.isEnabled()) {
                java.util.UUID magnata = mm.getCurrentMagnataUuid();
                if (magnata != null && magnata.equals(player.getUUID())) {
                    prefix = "§2[Magnata]§r " + prefix;
                }
            }
            if (debugEnabled) {
                LOGGER.debug(">>> PermissionAPI returned prefix: [{}]", prefix);
                LOGGER.debug(">>> Returning prefix: [{}]", prefix);
            }
            return prefix;
        } catch (Exception e) {
            LOGGER.error("Error getting prefix for player {}: {}", player.getName().getString(), e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Get player's suffix from the permission system.
     */
    @Nullable
    private String getPlayerSuffix(@Nullable ServerPlayer player) {
        if (player == null) return null;
        
        try {
            return PermissionAPI.getSuffix(player.getUUID());
        } catch (Exception e) {
            LOGGER.debug("Error getting suffix for player {}: {}", player.getName().getString(), e.getMessage());
            return "";
        }
    }
    
    /**
     * Get player's primary group from the permission system.
     */
    @Nullable
    private String getPlayerGroup(@Nullable ServerPlayer player) {
        if (player == null) return null;
        
        try {
            return PermissionAPI.getPrimaryGroup(player.getUUID());
        } catch (Exception e) {
            LOGGER.debug("Error getting group for player {}: {}", player.getName().getString(), e.getMessage());
            return "default";
        }
    }

    /**
     * Get the player's selected chat tag, including a trailing separator when present.
     */
    @Nullable
    private String getPlayerTag(@Nullable ServerPlayer player) {
        if (player == null) return null;

        try {
            return com.pedrodalben.bigbangessentials.tags.TagManager.getInstance()
                .getSelectedChatTag(player);
        } catch (Exception e) {
            LOGGER.debug("Error getting tag for player {}: {}", player.getName().getString(), e.getMessage());
            return "";
        }
    }

    /**
     * Get the player's selected tag name.
     */
    @Nullable
    private String getPlayerTagName(@Nullable ServerPlayer player) {
        if (player == null) return null;

        try {
            String tagName = com.pedrodalben.bigbangessentials.tags.TagManager.getInstance()
                .getSelectedTagName(player.getUUID());
            return tagName != null ? tagName : "";
        } catch (Exception e) {
            LOGGER.debug("Error getting tag name for player {}: {}", player.getName().getString(), e.getMessage());
            return "";
        }
    }

    /**
     * Get the player's selected tag format.
     */
    @Nullable
    private String getPlayerTagFormat(@Nullable ServerPlayer player) {
        if (player == null) return null;

        try {
            return com.pedrodalben.bigbangessentials.tags.TagManager.getInstance()
                .getSelectedTagFormat(player.getUUID());
        } catch (Exception e) {
            LOGGER.debug("Error getting tag format for player {}: {}", player.getName().getString(), e.getMessage());
            return "";
        }
    }
    
    /**
     * Get the name of the world the player is in.
     */
    @Nullable
    private String getWorldName(@Nullable ServerPlayer player) {
        if (player == null) return null;
        
        try {
            @SuppressWarnings("resource") // Level is managed by Minecraft
            Level level = player.level();
            return level.dimension().location().getPath();
        } catch (Exception e) {
            LOGGER.debug("Error getting world name for player {}: {}", player.getName().getString(), e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * Get the biome the player is currently in.
     */
    @Nullable
    private String getBiome(@Nullable ServerPlayer player) {
        if (player == null) return null;
        
        try {
            @SuppressWarnings("resource") // Level is managed by Minecraft
            var biome = player.level().getBiome(player.blockPosition());
            return biome.unwrapKey().map(key -> key.location().getPath()).orElse("unknown");
        } catch (Exception e) {
            LOGGER.debug("Error getting biome for player {}: {}", player.getName().getString(), e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * Get player's balance from the economy system.
     */
    @Nullable
    private String getBalance(@Nullable ServerPlayer player) {
        if (player == null) return null;
        
        try {
            EconomyManager economyManager = EconomyManager.getInstance();
            if (economyManager != null) {
                var balance = economyManager.getBalance(player.getUUID());
                return balance.toString();
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting balance for player {}: {}", player.getName().getString(), e.getMessage());
        }
        return "0.0";
    }
    
    /**
     * Get player's formatted balance from the economy system.
     */
    @Nullable
    private String getFormattedBalance(@Nullable ServerPlayer player) {
        if (player == null) return null;
        
        try {
            EconomyManager economyManager = EconomyManager.getInstance();
            if (economyManager != null) {
                var balance = economyManager.getBalance(player.getUUID());
                return DECIMAL_FORMAT.format(balance.doubleValue());
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting formatted balance for player {}: {}", player.getName().getString(), e.getMessage());
        }
        return "0.00";
    }
    
    private String getGems(@Nullable ServerPlayer player) {
        if (player == null) return "0";
        try {
            var manager = com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager.getInstance();
            if (manager != null) {
                var balance = manager.getBalanceView(player.getUUID());
                return String.valueOf(balance.totalBalance());
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting gems for player {}: {}", player.getName().getString(), e.getMessage());
        }
        return "0";
    }

    private String getFormattedGems(@Nullable ServerPlayer player) {
        if (player == null) return "0 ✦";
        try {
            var manager = com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager.getInstance();
            if (manager != null) {
                var balance = manager.getBalanceView(player.getUUID());
                return manager.format(balance.totalBalance());
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting formatted gems for player {}: {}", player.getName().getString(), e.getMessage());
        }
        return "0 ✦";
    }

    private String getAvailableGems(@Nullable ServerPlayer player) {
        if (player == null) return "0";
        try {
            var manager = com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager.getInstance();
            if (manager != null) {
                var balance = manager.getBalanceView(player.getUUID());
                return String.valueOf(balance.availableBalance());
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting available gems for player {}: {}", player.getName().getString(), e.getMessage());
        }
        return "0";
    }

    private String getHeldGems(@Nullable ServerPlayer player) {
        if (player == null) return "0";
        try {
            var manager = com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager.getInstance();
            if (manager != null) {
                var balance = manager.getBalanceView(player.getUUID());
                return String.valueOf(balance.heldBalance());
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting held gems for player {}: {}", player.getName().getString(), e.getMessage());
        }
        return "0";
    }

    private String getGemsCurrencyName() {
        try {
            var manager = com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager.getInstance();
            if (manager != null) {
                return manager.getCurrencyDescriptor().plural();
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting gems currency name: {}", e.getMessage());
        }
        return "Gemas";
    }

    private String getGemsCurrencySymbol() {
        try {
            var manager = com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager.getInstance();
            if (manager != null) {
                return manager.getCurrencyDescriptor().symbol();
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting gems currency symbol: {}", e.getMessage());
        }
        return "✦";
    }
    
    /**
     * Get the server name (motd or configured name).
     */
    private String getServerName(@Nullable ServerPlayer player) {
        try {
            if (player != null && player.getServer() != null) {
                return player.getServer().getMotd();
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting server name: {}", e.getMessage());
        }
        return "Minecraft Server";
    }
    
    /**
     * Get the current online player count.
     */
    private String getOnlinePlayerCount(@Nullable ServerPlayer player) {
        try {
            if (player != null && player.getServer() != null) {
                return String.valueOf(player.getServer().getPlayerCount());
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting online player count: {}", e.getMessage());
        }
        return "0";
    }
    
    /**
     * Get the maximum player count.
     */
    private String getMaxPlayerCount(@Nullable ServerPlayer player) {
        try {
            if (player != null && player.getServer() != null) {
                return String.valueOf(player.getServer().getMaxPlayers());
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting max player count: {}", e.getMessage());
        }
        return "20";
    }

    /**
     * Get the current server TPS (ticks per second).
     */
    private String getTps(@Nullable ServerPlayer player) {
        try {
            if (player != null && player.getServer() != null) {
                net.minecraft.server.MinecraftServer server = player.getServer();
                // MinecraftServer stores last 100 tick times in nanoseconds.
                // Access via reflection since getTickTimes() doesn't exist in 1.21.1 mappings.
                java.lang.reflect.Field tickTimesField = net.minecraft.server.MinecraftServer.class.getDeclaredField("tickTimes");
                tickTimesField.setAccessible(true);
                long[] tickTimes = (long[]) tickTimesField.get(server);
                if (tickTimes != null && tickTimes.length > 0) {
                    double avgNanos = 0;
                    for (long t : tickTimes) avgNanos += t;
                    avgNanos /= tickTimes.length;
                    double tps = 1_000_000_000.0 / avgNanos;
                    return String.format("%.1f", Math.min(tps, 20.0));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting TPS: {}", e.getMessage());
        }
        return "20.0";
    }
    
    /**
     * Get current time in 12-hour format.
     */
    private String getCurrentTime() {
        try {
            return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
        } catch (Exception e) {
            LOGGER.debug("Error getting current time: {}", e.getMessage());
            return "00:00 AM";
        }
    }
    
    /**
     * Get current time in 24-hour format.
     */
    private String getCurrentTime24() {
        try {
            return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            LOGGER.debug("Error getting current time (24h): {}", e.getMessage());
            return "00:00";
        }
    }
    
    /**
     * Get current date.
     */
    private String getCurrentDate() {
        try {
            return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            LOGGER.debug("Error getting current date: {}", e.getMessage());
            return "1970-01-01";
        }
    }
    
    /**
     * Get player's AFK status.
     * Returns "AFK" if player is AFK, empty string otherwise.
     */
    private String getAfkStatus(@Nullable ServerPlayer player) {
        if (player == null) return "";
        
        try {
            var afkManager = com.pedrodalben.bigbangessentials.chat.AfkManager.getInstance();
            boolean isAfk = afkManager.isAfk(player.getUUID());
            return isAfk ? "AFK" : "";
        } catch (Exception e) {
            LOGGER.debug("Error getting AFK status for player {}: {}", player.getName().getString(), e.getMessage());
            return "";
        }
    }
    
    /**
     * Get how long player has been AFK.
     * Returns formatted time like "5m 30s" or empty if not AFK.
     */
    private String getAfkTime(@Nullable ServerPlayer player) {
        if (player == null) return "";
        
        try {
            var afkManager = com.pedrodalben.bigbangessentials.chat.AfkManager.getInstance();
            if (!afkManager.isAfk(player.getUUID())) {
                return "";
            }
            
            long afkMs = afkManager.getAfkDuration(player.getUUID());
            if (afkMs <= 0) return "";
            
            long seconds = afkMs / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            
            if (hours > 0) {
                return String.format("%dh %dm", hours, minutes % 60);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds % 60);
            } else {
                return String.format("%ds", seconds);
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting AFK time for player {}: {}", player.getName().getString(), e.getMessage());
            return "";
        }
    }
    
    private String getRankupPlaceholder(@Nullable ServerPlayer player, String identifier) {
        if (player == null) return "";
        try {
            var manager = com.pedrodalben.bigbangessentials.rankup.RankupManager.getInstance();
            if (manager == null) return "";
            return switch (identifier.toLowerCase()) {
                case "rankup_current_id" -> manager.getPlaceholderService().get(player.getUUID(), "current_id");
                case "rankup_current_name" -> manager.getPlaceholderService().get(player.getUUID(), "current_name");
                case "rankup_next_id" -> manager.getPlaceholderService().get(player.getUUID(), "next_id");
                case "rankup_next_name" -> manager.getPlaceholderService().get(player.getUUID(), "next_name");
                case "rankup_progress_percent" -> manager.getPlaceholderService().get(player.getUUID(), "progress_percent");
                case "rankup_money_required" -> manager.getPlaceholderService().get(player.getUUID(), "money_required");
                case "rankup_gems_required" -> manager.getPlaceholderService().get(player.getUUID(), "gems_required");
                case "rankup_tasks_completed" -> manager.getPlaceholderService().get(player.getUUID(), "tasks_completed");
                case "rankup_tasks_total" -> manager.getPlaceholderService().get(player.getUUID(), "tasks_total");
                default -> "";
            };
        } catch (Exception e) {
            LOGGER.debug("Error resolving rankup placeholder: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Get player's AFK reason.
     * Returns the reason text or empty string if no reason or not AFK.
     */
    private String getAfkReason(@Nullable ServerPlayer player) {
        if (player == null) return "";
        
        try {
            var afkManager = com.pedrodalben.bigbangessentials.chat.AfkManager.getInstance();
            if (!afkManager.isAfk(player.getUUID())) {
                return "";
            }
            
            String reason = afkManager.getAfkReason(player.getUUID());
            return reason != null ? reason : "";
        } catch (Exception e) {
            LOGGER.debug("Error getting AFK reason for player {}: {}", player.getName().getString(), e.getMessage());
            return "";
        }
    }
}
