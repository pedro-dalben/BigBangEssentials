package com.pedrodalben.bigbangessentials.tablist;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom Player Tablist system for BigBangEssentials.
 *
 * Features:
 * - Animated header/footer with frame cycling
 * - Per-group prefix/suffix display on player rows (via GameProfile display name)
 * - Placeholder support: {player}, {online}, {max}, {ping}, {world}, {tps}, {time}
 * - Configurable refresh interval
 * - Per-player custom name override (e.g. nick system integration)
 * - Vanished player hiding for non-staff
 * - AFK indicator in tablist
 *
 * References: TAB [1.7.x-1.21.x], BungeeTabListPlus, Simple TabList
 */
public class TablistManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistManager.class);
    private static final int DEFAULT_REFRESH_INTERVAL_TICKS = 40;

    private static final TablistManager INSTANCE = new TablistManager();
    public static TablistManager getInstance() { return INSTANCE; }

    // ── Config ────────────────────────────────────────────────────────────────
    private boolean enabled = true;
    private int refreshIntervalTicks = DEFAULT_REFRESH_INTERVAL_TICKS; // 2 seconds
    private List<String> headerFrames = new ArrayList<>();
    private List<String> footerFrames = new ArrayList<>();
    private String playerFormat = "§f{prefix}§r{player}{suffix}";
    private boolean hideVanished = true;
    private boolean showAfkIndicator = true;
    private String afkSuffix = " §7[AFK]";
    /** Per-group colour overrides loaded from tablist.json groupColors section. */
    private final Map<String, String> groupColors = new java.util.LinkedHashMap<>();

    // ── Runtime state ─────────────────────────────────────────────────────────
    private int headerFrame = 0;
    private int footerFrame = 0;
    private int tickCounter = 0;

    // Per-player custom tab name override (used by nick system)
    private final Map<UUID, String> customNames = new ConcurrentHashMap<>();

    private TablistManager() {
        // Default header/footer if nothing configured
        headerFrames.add("§6§l{server_name} §8| §e{online}§8/§e{max} §7players");
        footerFrames.add("§7TPS: §a{tps} §8| §7Ping: §a{ping}ms §8| §7{world}");
    }

    // ── Initialisation ────────────────────────────────────────────────────────
    public void loadConfig() {
        try {
            // Prefer dedicated tablist.json; fall back to "tablist" section in config.json
            com.google.gson.JsonObject tab = null;

            // 1) Try standalone tablist.json first
            try {
                com.google.gson.JsonObject standalone = ConfigManager.getInstance()
                    .getConfig(ConfigManager.TABLIST_CONFIG);
                if (standalone != null && standalone.has("tablist")) {
                    tab = standalone.getAsJsonObject("tablist");
                    LOGGER.debug("TablistManager: loading from tablist.json");
                }
            } catch (Exception ex) {
                LOGGER.debug("TablistManager: tablist.json not available, trying config.json fallback: {}", ex.getMessage());
            }

            // 2) Legacy fallback: "tablist" key inside config.json
            if (tab == null) {
                com.google.gson.JsonObject cfg = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
                if (cfg != null && cfg.has("tablist")) {
                    tab = cfg.getAsJsonObject("tablist");
                    LOGGER.debug("TablistManager: loading from legacy tablist section in config.json");
                }
            }

            if (tab == null) {
                LOGGER.info("TablistManager: no tablist configuration found — using defaults.");
                return;
            }

            enabled            = !tab.has("enabled")           || tab.get("enabled").getAsBoolean();
            refreshIntervalTicks = tab.has("refreshInterval")
                ? Math.max(1, tab.get("refreshInterval").getAsInt())
                : DEFAULT_REFRESH_INTERVAL_TICKS;
            hideVanished       = !tab.has("hideVanished")       || tab.get("hideVanished").getAsBoolean();
            showAfkIndicator   = !tab.has("showAfkIndicator")   || tab.get("showAfkIndicator").getAsBoolean();
            afkSuffix          = tab.has("afkSuffix")
                                    ? tab.get("afkSuffix").getAsString().replace("&", "§")
                                    : " §7[AFK]";
            playerFormat       = tab.has("playerFormat")        ? tab.get("playerFormat").getAsString() : playerFormat;

            // Per-group colour overrides
            groupColors.clear();
            if (tab.has("groupColors") && tab.get("groupColors").isJsonObject()) {
                for (var entry : tab.getAsJsonObject("groupColors").entrySet()) {
                    groupColors.put(entry.getKey(), entry.getValue().getAsString().replace("&", "§"));
                }
            }

            headerFrames.clear();
            if (tab.has("header")) {
                var h = tab.get("header");
                if (h.isJsonArray()) {
                    for (var el : h.getAsJsonArray()) headerFrames.add(el.getAsString());
                } else {
                    headerFrames.add(h.getAsString());
                }
            }

            footerFrames.clear();
            if (tab.has("footer")) {
                var f = tab.get("footer");
                if (f.isJsonArray()) {
                    for (var el : f.getAsJsonArray()) footerFrames.add(el.getAsString());
                } else {
                    footerFrames.add(f.getAsString());
                }
            }

            if (headerFrames.isEmpty()) headerFrames.add("§6§l{server_name}");
            if (footerFrames.isEmpty()) footerFrames.add("§7{online}§8/§7{max} online");

            // Reset animation counters so changes take effect immediately on reload
            headerFrame  = 0;
            footerFrame  = 0;
            tickCounter  = 0;

            LOGGER.info("TablistManager loaded — {} header frame(s), {} footer frame(s), refresh every {} ticks.",
                headerFrames.size(), footerFrames.size(), refreshIntervalTicks);
        } catch (Exception e) {
            LOGGER.error("Failed to load tablist config: {}", e.getMessage());
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    /**
     * Called every server tick from TablistEventHandler.onServerTick().
     * Only does work every refreshIntervalTicks ticks.
     */
    public void onTick(MinecraftServer server) {
        if (!enabled) return;
        tickCounter++;
        if (tickCounter < refreshIntervalTicks) return;
        tickCounter = 0;

        // Advance animation frames
        if (headerFrames.size() > 1) headerFrame = (headerFrame + 1) % headerFrames.size();
        if (footerFrames.size() > 1) footerFrame = (footerFrame + 1) % footerFrames.size();

        updateAll(server);
    }

    // ── Update ────────────────────────────────────────────────────────────────
    /** Send updated header/footer packet to all online players. */
    public void updateAll(MinecraftServer server) {
        if (!enabled || server == null) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        String headerTemplate = currentFrame(headerFrames, this.headerFrame);
        String footerTemplate = currentFrame(footerFrames, this.footerFrame);
        PlaceholderUsage headerUsage = PlaceholderUsage.from(headerTemplate);
        PlaceholderUsage footerUsage = PlaceholderUsage.from(footerTemplate);
        PlaceholderUsage usage = headerUsage.merge(footerUsage);
        RefreshContext context = RefreshContext.create(server, players, usage, hideVanished);

        for (ServerPlayer player : players) {
            sendTablistPacket(player, headerTemplate, footerTemplate, usage, context);
        }
    }

    /** Send header/footer to a single player. */
    public void updatePlayer(ServerPlayer player, MinecraftServer server) {
        if (!enabled || player == null || server == null) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        String headerTemplate = currentFrame(headerFrames, this.headerFrame);
        String footerTemplate = currentFrame(footerFrames, this.footerFrame);
        PlaceholderUsage headerUsage = PlaceholderUsage.from(headerTemplate);
        PlaceholderUsage footerUsage = PlaceholderUsage.from(footerTemplate);
        PlaceholderUsage usage = headerUsage.merge(footerUsage);
        RefreshContext context = RefreshContext.create(server, players, usage, hideVanished);

        sendTablistPacket(player, headerTemplate, footerTemplate, usage, context);
    }

    // ── Placeholders ─────────────────────────────────────────────────────────
    /**
     * Supported placeholders:
     * {player}        — player's name
     * {displayname}   — player's display name (with nick/prefix)
     * {online}        — current online player count (excluding vanished for non-staff)
     * {max}           — max player slots
     * {ping}          — player's ping in ms
     * {world}         — current dimension path (e.g. overworld)
     * {tps}           — server TPS (formatted to 1 dp)
     * {time}          — server real-world time (HH:mm)
     * {server_name}   — server motd / name
     * {x}, {y}, {z}   — player coordinates
     * {balance}       — player balance (from EconomyManager)
     * {prefix}        — permission group prefix
     * {suffix}        — permission group suffix
     * {group}         — permission group name
     * {newline}       — line break
     * {bar}           — decorative separator
     * &<code>         — colour codes converted to §
     */
    private void sendTablistPacket(
        ServerPlayer player,
        String headerFrame,
        String footerFrame,
        PlaceholderUsage usage,
        RefreshContext context
    ) {
        try {
            PlayerPlaceholderValues values = PlayerPlaceholderValues.create(this, player, usage, context);
            String header = applyPlaceholders(headerFrame, player, usage, context, values);
            String footer = applyPlaceholders(footerFrame, player, usage, context, values);

            ClientboundTabListPacket packet = new ClientboundTabListPacket(
                Component.literal(header),
                Component.literal(footer)
            );
            player.connection.send(packet);
        } catch (Exception e) {
            LOGGER.debug("Failed to send tablist packet to {}: {}", player.getName().getString(), e.getMessage());
        }
    }

    private String currentFrame(List<String> frames, int frameIndex) {
        return frames.get(Math.min(frameIndex, frames.size() - 1));
    }

    private String applyPlaceholders(
        String text,
        ServerPlayer player,
        PlaceholderUsage usage,
        RefreshContext context,
        PlayerPlaceholderValues values
    ) {
        if (text == null) return "";

        // Convert & colour codes
        text = text.replace("&", "§");

        if (usage.player) {
            text = text.replace("{player}", values.playerName);
        }
        if (usage.displayName) {
            String groupColor = groupColors.getOrDefault(values.group, groupColors.getOrDefault("default", ""));
            String coloredDisplayName = groupColor.isEmpty() ? values.displayName : groupColor + values.displayName;
            text = text.replace("{displayname}", coloredDisplayName);
        }
        if (usage.online) {
            text = text.replace("{online}", String.valueOf(values.online));
        }
        if (usage.max) {
            text = text.replace("{max}", String.valueOf(context.maxPlayers));
        }
        if (usage.ping) {
            text = text.replace("{ping}", String.valueOf(values.ping));
        }
        if (usage.world) {
            text = text.replace("{world}", values.world);
        }
        if (usage.tps) {
            text = text.replace("{tps}", context.tpsStr);
        }
        if (usage.time) {
            text = text.replace("{time}", context.time);
        }
        if (usage.serverName) {
            text = text.replace("{server_name}", context.serverName);
        }
        if (usage.x) {
            text = text.replace("{x}", String.valueOf(values.x));
        }
        if (usage.y) {
            text = text.replace("{y}", String.valueOf(values.y));
        }
        if (usage.z) {
            text = text.replace("{z}", String.valueOf(values.z));
        }
        if (usage.balance) {
            text = text.replace("{balance}", values.balance);
        }
        if (usage.prefix) {
            text = text.replace("{prefix}", values.prefix);
        }
        if (usage.suffix) {
            text = text.replace("{suffix}", values.suffix);
        }
        if (usage.group) {
            text = text.replace("{group}", values.group);
        }

        text = text
            .replace("{newline}", "\n")
            .replace("{bar}", "§8§m                              §r");

        return text;
    }

    private static final class PlaceholderUsage {
        private static final PlaceholderUsage EMPTY = new PlaceholderUsage(
            false, false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false
        );

        final boolean player;
        final boolean displayName;
        final boolean online;
        final boolean max;
        final boolean ping;
        final boolean world;
        final boolean tps;
        final boolean time;
        final boolean serverName;
        final boolean x;
        final boolean y;
        final boolean z;
        final boolean balance;
        final boolean prefix;
        final boolean suffix;
        final boolean group;

        private PlaceholderUsage(
            boolean player,
            boolean displayName,
            boolean online,
            boolean max,
            boolean ping,
            boolean world,
            boolean tps,
            boolean time,
            boolean serverName,
            boolean x,
            boolean y,
            boolean z,
            boolean balance,
            boolean prefix,
            boolean suffix,
            boolean group
        ) {
            this.player = player;
            this.displayName = displayName;
            this.online = online;
            this.max = max;
            this.ping = ping;
            this.world = world;
            this.tps = tps;
            this.time = time;
            this.serverName = serverName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.balance = balance;
            this.prefix = prefix;
            this.suffix = suffix;
            this.group = group;
        }

        static PlaceholderUsage from(String text) {
            if (text == null || text.isEmpty()) {
                return EMPTY;
            }

            return new PlaceholderUsage(
                text.contains("{player}"),
                text.contains("{displayname}"),
                text.contains("{online}"),
                text.contains("{max}"),
                text.contains("{ping}"),
                text.contains("{world}"),
                text.contains("{tps}"),
                text.contains("{time}"),
                text.contains("{server_name}"),
                text.contains("{x}"),
                text.contains("{y}"),
                text.contains("{z}"),
                text.contains("{balance}"),
                text.contains("{prefix}"),
                text.contains("{suffix}"),
                text.contains("{group}")
            );
        }

        PlaceholderUsage merge(PlaceholderUsage other) {
            if (other == null || other == EMPTY) {
                return this;
            }
            if (this == EMPTY) {
                return other;
            }

            return new PlaceholderUsage(
                player || other.player,
                displayName || other.displayName,
                online || other.online,
                max || other.max,
                ping || other.ping,
                world || other.world,
                tps || other.tps,
                time || other.time,
                serverName || other.serverName,
                x || other.x,
                y || other.y,
                z || other.z,
                balance || other.balance,
                prefix || other.prefix,
                suffix || other.suffix,
                group || other.group
            );
        }

        boolean needsPermissionData() {
            return displayName || prefix || suffix || group;
        }
    }

    private static final class RefreshContext {
        final int totalPlayers;
        final int vanishedPlayers;
        final boolean hideVanished;
        final int maxPlayers;
        final String tpsStr;
        final String time;
        final String serverName;

        private RefreshContext(
            int totalPlayers,
            int vanishedPlayers,
            boolean hideVanished,
            int maxPlayers,
            String tpsStr,
            String time,
            String serverName
        ) {
            this.totalPlayers = totalPlayers;
            this.vanishedPlayers = vanishedPlayers;
            this.hideVanished = hideVanished;
            this.maxPlayers = maxPlayers;
            this.tpsStr = tpsStr;
            this.time = time;
            this.serverName = serverName;
        }

        static RefreshContext create(
            MinecraftServer server,
            List<ServerPlayer> players,
            PlaceholderUsage usage,
            boolean hideVanished
        ) {
            int totalPlayers = usage.online ? players.size() : 0;
            int vanishedPlayers = 0;

            if (usage.online && hideVanished) {
                try {
                    com.pedrodalben.bigbangessentials.moderation.VanishManager vanishManager =
                        com.pedrodalben.bigbangessentials.moderation.VanishManager.getInstance();
                    for (ServerPlayer onlinePlayer : players) {
                        if (vanishManager.isPlayerVanished(onlinePlayer.getUUID())) {
                            vanishedPlayers++;
                        }
                    }
                } catch (Exception ignored) {}
            }

            int maxPlayers = usage.max || usage.online ? server.getMaxPlayers() : 0;
            String tpsStr = usage.tps ? formatTps(TablistManager.getTps(server)) : "";
            String time = usage.time ? new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()) : "";
            String serverName = usage.serverName ? server.getMotd() : "";

            return new RefreshContext(
                totalPlayers,
                vanishedPlayers,
                hideVanished,
                maxPlayers,
                tpsStr,
                time,
                serverName
            );
        }

        int getVisibleOnlineCount(ServerPlayer viewer) {
            if (!hideVanished || vanishedPlayers <= 0) {
                return totalPlayers;
            }

            try {
                if (PermissionAPI.hasPermission(viewer.getUUID(), "bigbangessentials.vanish.see")) {
                    return totalPlayers;
                }
            } catch (Exception ignored) {}

            return Math.max(0, totalPlayers - vanishedPlayers);
        }
    }

    private static final class PermissionData {
        static final PermissionData EMPTY = new PermissionData("", "", "default");

        final String prefix;
        final String suffix;
        final String group;

        private PermissionData(String prefix, String suffix, String group) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.group = group;
        }
    }

    private static final class PlayerPlaceholderValues {
        final String playerName;
        final String displayName;
        final int online;
        final int ping;
        final String world;
        final int x;
        final int y;
        final int z;
        final String balance;
        final String prefix;
        final String suffix;
        final String group;

        private PlayerPlaceholderValues(
            String playerName,
            String displayName,
            int online,
            int ping,
            String world,
            int x,
            int y,
            int z,
            String balance,
            String prefix,
            String suffix,
            String group
        ) {
            this.playerName = playerName;
            this.displayName = displayName;
            this.online = online;
            this.ping = ping;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.balance = balance;
            this.prefix = prefix;
            this.suffix = suffix;
            this.group = group;
        }

        static PlayerPlaceholderValues create(
            TablistManager manager,
            ServerPlayer player,
            PlaceholderUsage usage,
            RefreshContext context
        ) {
            String playerName = usage.player || usage.displayName ? player.getName().getString() : "";
            String displayName = usage.displayName ? manager.getDisplayName(player) : "";
            PermissionData permissionData = usage.needsPermissionData() ? resolvePermissionData(player) : PermissionData.EMPTY;

            int online = usage.online ? context.getVisibleOnlineCount(player) : 0;
            int ping = usage.ping ? player.connection.latency() : 0;
            String world = usage.world ? player.serverLevel().dimension().location().getPath() : "";
            int x = usage.x ? player.getBlockX() : 0;
            int y = usage.y ? player.getBlockY() : 0;
            int z = usage.z ? player.getBlockZ() : 0;

            String balance = "0";
            if (usage.balance) {
                try {
                    java.math.BigDecimal bd = com.pedrodalben.bigbangessentials.economy.managers.EconomyManager.getInstance()
                        .getBalance(player.getUUID());
                    balance = String.format("%.2f", bd.doubleValue());
                } catch (Exception ignored) {}
            }

            return new PlayerPlaceholderValues(
                playerName,
                displayName,
                online,
                ping,
                world,
                x,
                y,
                z,
                balance,
                permissionData.prefix,
                permissionData.suffix,
                permissionData.group
            );
        }
    }

    private static String formatTps(double tps) {
        return tps >= 19.0 ? "§a" + String.format("%.1f", tps)
            : tps >= 15.0 ? "§e" + String.format("%.1f", tps)
            : "§c" + String.format("%.1f", tps);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean isVanishedFromPlayer(ServerPlayer target, ServerPlayer viewer) {
        if (!hideVanished) return false;
        boolean targetVanished = com.pedrodalben.bigbangessentials.moderation.VanishManager.getInstance().isPlayerVanished(target.getUUID());
        if (!targetVanished) return false;
        // Staff with seevanished perm can still see them
        return !PermissionAPI.hasPermission(viewer.getUUID(), "bigbangessentials.vanish.see");
    }

    private String getDisplayName(ServerPlayer player) {
        // Check custom name override (nick system)
        String custom = customNames.get(player.getUUID());
        if (custom != null && !custom.isEmpty()) return custom;
        return player.getName().getString();
    }

    private static PermissionData resolvePermissionData(ServerPlayer player) {
        try {
            String prefix = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getPrefix(player.getUUID());
            String suffix = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getSuffix(player.getUUID());
            String groupName = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getPrimaryGroup(player.getUUID());
            return new PermissionData(
                prefix != null ? prefix.replace("&", "§") : "",
                suffix != null ? suffix.replace("&", "§") : "",
                groupName != null ? groupName : "default"
            );
        } catch (Exception ignored) {}
        return PermissionData.EMPTY;
    }

    private static double getTps(MinecraftServer server) {
        try {
            // NeoForge 1.21.1: getAverageTickTimeNanos() → convert to TPS
            double avgMs = server.getAverageTickTimeNanos() / 1_000_000.0;
            return Math.min(20.0, 1000.0 / Math.max(avgMs, 1.0));
        } catch (Exception e) {
            return 20.0;
        }
    }

    private String getPermissionPrefix(ServerPlayer player) {
        try {
            String prefix = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getPrefix(player.getUUID());
            if (prefix != null && !prefix.isEmpty()) {
                return prefix.replace("&", "§");
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String getPermissionSuffix(ServerPlayer player) {
        try {
            String suffix = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getSuffix(player.getUUID());
            if (suffix != null && !suffix.isEmpty()) {
                return suffix.replace("&", "§");
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String getPermissionGroup(ServerPlayer player) {
        try {
            return com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.getPrimaryGroup(player.getUUID());
        } catch (Exception ignored) {}
        return "default";
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isHideVanished() { return hideVanished; }
    public int getRefreshIntervalTicks() { return refreshIntervalTicks; }
    public int getHeaderFrameCount() { return headerFrames.size(); }
    public int getFooterFrameCount() { return footerFrames.size(); }

    /** Runtime header override (first frame replaced). Cleared on reload. */
    public void setHeaderOverride(String text) {
        headerFrames.clear();
        headerFrames.add(text);
        headerFrame = 0;
    }

    /** Runtime footer override (first frame replaced). Cleared on reload. */
    public void setFooterOverride(String text) {
        footerFrames.clear();
        footerFrames.add(text);
        footerFrame = 0;
    }

    /** Set a per-player custom tab display name (used by /nick). */
    public void setCustomName(UUID uuid, String name) {
        if (name == null || name.isEmpty()) customNames.remove(uuid);
        else customNames.put(uuid, name);
    }

    public void clearCustomName(UUID uuid) { customNames.remove(uuid); }

    public String getAfkSuffix() { return afkSuffix; }
    public boolean isShowAfkIndicator() { return showAfkIndicator; }

    /** Called when a player joins — send initial tablist update. */
    public void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        // Small delay to ensure the client is fully ready
        server.execute(() -> updatePlayer(player, server));
    }

    /** Called when a player leaves — update all remaining players' online count. */
    public void onPlayerQuit(MinecraftServer server) {
        server.execute(() -> updateAll(server));
    }
}









