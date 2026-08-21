package com.pedrodalben.bigbangessentials.menu.placeholder;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.api.PlaceholderAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PlaceholderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceholderService.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    /**
     * Resolves placeholders in a given string.
     */
    public static String resolve(String text, ServerPlayer player, MenuContext context) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        MenuSession session = null;
        MenuDefinition menu = null;
        try {
            if (player != null && MenuSystem.getInstance() != null && MenuSystem.getInstance().getMenuService() != null) {
                session = MenuSystem.getInstance().getMenuService().getCurrentSession(player.getUUID()).orElse(null);
                if (session != null) {
                    menu = MenuSystem.getInstance().getMenuService().getMenu(session.getMenuId()).orElse(null);
                }
            }
        } catch (Exception ignored) {}

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String fullPlaceholder = matcher.group(0);
            String content = matcher.group(1);

            String id;
            String params = null;
            int colonIndex = content.indexOf(':');
            if (colonIndex != -1) {
                id = content.substring(0, colonIndex);
                params = content.substring(colonIndex + 1);
            } else {
                id = content;
            }

            String resolvedValue = null;
            try {
                resolvedValue = resolveSingle(id, params, player, context, session, menu);
            } catch (Exception e) {
                boolean debugEnabled = false;
                try {
                    debugEnabled = com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isDebugLoggingEnabled();
                } catch (Exception ignored) {}
                
                if (debugEnabled) {
                    LOGGER.warn("Failed to resolve placeholder {} for player {}: {}", 
                        fullPlaceholder, player != null ? player.getName().getString() : "null", e.getMessage());
                }
            }

            if (resolvedValue != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(resolvedValue));
            } else {
                // Keep original placeholder if not resolved
                matcher.appendReplacement(result, Matcher.quoteReplacement(fullPlaceholder));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveSingle(String id, String params, ServerPlayer player, MenuContext context, MenuSession session, MenuDefinition menu) {
        // 0. Try local placeholder overrides from context
        if (context != null && context.placeholderOverrides() != null && context.placeholderOverrides().containsKey(id)) {
            return context.placeholderOverrides().get(id);
        }

        // 1. Try MenuPlaceholderRegistry
        try {
            if (MenuSystem.getInstance() != null && MenuSystem.getInstance().getPlaceholderRegistry() != null) {
                Optional<PlaceholderResolver> customResolver = MenuSystem.getInstance().getPlaceholderRegistry().getPlaceholder(id);
                if (customResolver.isPresent()) {
                    PlaceholderRequest req = new PlaceholderRequest(id + (params != null ? ":" + params : ""), params);
                    PlaceholderValue value = awaitResolver(customResolver.get(), customResolver.get().resolve(player, context, req), player, id);
                    if (value != null && value.value() != null) {
                        return value.value();
                    }
                }
            }
            } catch (Exception e) {
                LOGGER.error("Error executing custom placeholder resolver for '{}' (params='{}')", id, params, e);
            }

        // 2. Resolve Core Built-in Placeholders
        String lowerId = id.toLowerCase();
        
        // Player Placeholders
        if (player != null) {
            switch (lowerId) {
                case "player_name":
                    return player.getName().getString();
                case "player_uuid":
                    return player.getUUID().toString();
                case "player_level":
                    return String.valueOf(player.experienceLevel);
                case "player_health":
                    return String.format(java.util.Locale.ROOT, "%.1f", player.getHealth());
                case "player_food":
                    return String.valueOf(player.getFoodData().getFoodLevel());
                case "player_world":
                    return player.level().dimension().location().getPath();
            }
        }

        // Server Placeholders
        switch (lowerId) {
            case "server_online_players":
                if (player != null && player.getServer() != null) {
                    return String.valueOf(player.getServer().getPlayerCount());
                }
                return "0";
            case "server_max_players":
                if (player != null && player.getServer() != null) {
                    return String.valueOf(player.getServer().getMaxPlayers());
                }
                return "20";
            case "server_time":
                return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        // Menu Placeholders
        if (session != null) {
            switch (lowerId) {
                case "menu_id":
                    return session.getMenuId();
                case "menu_page":
                    return session.getCurrentPageId();
                case "menu_page_index":
                    return String.valueOf(session.getCurrentPageIndex());
            }
        }
        if (menu != null) {
            if (lowerId.equals("menu_total_pages")) {
                return String.valueOf(menu.pages().size());
            }
        }

        // Session/Context Placeholders
        if (lowerId.equals("context") && params != null) {
            // First check context parameter map
            if (context != null && context.values() != null && context.values().containsKey(params)) {
                Object val = context.values().get(params);
                if (val != null) return String.valueOf(val);
            }
            // Then check session data map
            if (session != null && session.getSessionData() != null && session.getSessionData().containsKey(params)) {
                Object val = session.getSessionData().get(params);
                if (val != null) return String.valueOf(val);
            }
            return ""; // Context placeholders fallback to empty string if not found
        }

        // 3. Fallback to global PlaceholderAPI if registered there
        try {
            if (player != null) {
                String globalVal = PlaceholderAPI.getPlaceholderValue(player, id, params);
                if (globalVal != null) {
                    return globalVal;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static PlaceholderValue awaitResolver(PlaceholderResolver resolver, CompletionStage<PlaceholderValue> stage, ServerPlayer player, String placeholderId) {
        if (stage == null) {
            return null;
        }

        CompletableFuture<PlaceholderValue> future = stage.toCompletableFuture();
        if (future.isDone()) {
            return future.join();
        }

        boolean onServerThread = player != null && player.getServer() != null && player.getServer().isSameThread();
        if (onServerThread && resolver.mode() == PlaceholderMode.ASYNC) {
            LOGGER.error("Refusing to block the server thread while resolving async placeholder '{}'", placeholderId);
            return null;
        }

        return future.join();
    }
}
