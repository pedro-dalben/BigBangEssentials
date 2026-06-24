package com.pedrodalben.bigbangessentials.menu.integration.teleportation;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.config.ConfigManager;

public class TeleportMenuConfig {
    private static boolean enabled = true;
    private static CommandDisplayMode warpsCommandMode = CommandDisplayMode.MENU;
    private static CommandDisplayMode homesCommandMode = CommandDisplayMode.MENU;
    private static CommandDisplayMode pwarpsCommandMode = CommandDisplayMode.MENU;
    private static boolean allowPlayerPreferences = true;
    private static boolean fallbackToChatIfMenuFails = true;
    private static boolean autoRefreshOpenMenus = true;

    private static String mainMenuId = "teleports_main_menu";
    private static String warpsMenuId = "warps_menu";
    private static String homesMenuId = "homes_menu";
    private static String pwarpsMenuId = "pwarps_menu";

    public static void load() {
        try {
            JsonObject root = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            if (root.has("teleport-menus")) {
                JsonObject section = root.getAsJsonObject("teleport-menus");
                if (section.has("enabled")) {
                    enabled = section.get("enabled").getAsBoolean();
                }
                if (section.has("allow-player-preferences")) {
                    allowPlayerPreferences = section.get("allow-player-preferences").getAsBoolean();
                }
                if (section.has("fallback-to-chat-if-menu-fails")) {
                    fallbackToChatIfMenuFails = section.get("fallback-to-chat-if-menu-fails").getAsBoolean();
                }
                if (section.has("auto-refresh-open-menus")) {
                    autoRefreshOpenMenus = section.get("auto-refresh-open-menus").getAsBoolean();
                }

                if (section.has("defaults")) {
                    JsonObject defaults = section.getAsJsonObject("defaults");
                    if (defaults.has("warps-command-mode")) {
                        warpsCommandMode = CommandDisplayMode.valueOf(defaults.get("warps-command-mode").getAsString().toUpperCase());
                    }
                    if (defaults.has("homes-command-mode")) {
                        homesCommandMode = CommandDisplayMode.valueOf(defaults.get("homes-command-mode").getAsString().toUpperCase());
                    }
                    if (defaults.has("pwarps-command-mode")) {
                        pwarpsCommandMode = CommandDisplayMode.valueOf(defaults.get("pwarps-command-mode").getAsString().toUpperCase());
                    }
                }

                if (section.has("command-display-mode")) {
                    JsonObject cmdMode = section.getAsJsonObject("command-display-mode");
                    if (cmdMode.has("warps")) {
                        warpsCommandMode = CommandDisplayMode.valueOf(cmdMode.get("warps").getAsString().toUpperCase());
                    }
                    if (cmdMode.has("homes")) {
                        homesCommandMode = CommandDisplayMode.valueOf(cmdMode.get("homes").getAsString().toUpperCase());
                    }
                    if (cmdMode.has("pwarps")) {
                        pwarpsCommandMode = CommandDisplayMode.valueOf(cmdMode.get("pwarps").getAsString().toUpperCase());
                    }
                }

                if (section.has("menus")) {
                    JsonObject menus = section.getAsJsonObject("menus");
                    if (menus.has("main")) {
                        mainMenuId = menus.get("main").getAsString();
                    }
                    if (menus.has("warps")) {
                        warpsMenuId = menus.get("warps").getAsString();
                    }
                    if (menus.has("homes")) {
                        homesMenuId = menus.get("homes").getAsString();
                    }
                    if (menus.has("pwarps")) {
                        pwarpsMenuId = menus.get("pwarps").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            // Safe fallback to defaults
        }
    }

    public static boolean isEnabled() { return enabled; }
    public static CommandDisplayMode getWarpsCommandMode() { return warpsCommandMode; }
    public static CommandDisplayMode getHomesCommandMode() { return homesCommandMode; }
    public static CommandDisplayMode getPwarpsCommandMode() { return pwarpsCommandMode; }
    public static boolean isAllowPlayerPreferences() { return allowPlayerPreferences; }
    public static boolean isFallbackToChatIfMenuFails() { return fallbackToChatIfMenuFails; }
    public static boolean isAutoRefreshOpenMenus() { return autoRefreshOpenMenus; }
    public static String getMainMenuId() { return mainMenuId; }
    public static String getWarpsMenuId() { return warpsMenuId; }
    public static String getHomesMenuId() { return homesMenuId; }
    public static String getPwarpsMenuId() { return pwarpsMenuId; }
}
