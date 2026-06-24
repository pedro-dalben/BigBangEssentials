package com.pedrodalben.bigbangessentials.menu.integration.kits;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.config.ConfigManager;

public final class KitMenuConfig {
    private static boolean enabled = true;
    private static boolean fallbackToChatIfMenuFails = true;
    private static boolean autoRefreshOpenMenus = true;
    private static String menuId = "kits_menu";
    private static String previewMenuId = "kits_preview_menu";

    private KitMenuConfig() {}

    public static void load() {
        enabled = true;
        fallbackToChatIfMenuFails = true;
        autoRefreshOpenMenus = true;
        menuId = "kits_menu";
        previewMenuId = "kits_preview_menu";

        try {
            JsonObject root = ConfigManager.getInstance().getConfig(ConfigManager.KITS_CONFIG);
            JsonObject section = extractSection(root);
            if (section == null) {
                return;
            }

            enabled = getBoolean(section, "enabled", enabled);
            fallbackToChatIfMenuFails = getBoolean(section, "fallbackToChatIfMenuFails",
                getBoolean(section, "fallback-to-chat-if-menu-fails", fallbackToChatIfMenuFails));
            autoRefreshOpenMenus = getBoolean(section, "autoRefreshOpenMenus",
                getBoolean(section, "auto-refresh-open-menus", autoRefreshOpenMenus));
            menuId = getString(section, "menuId", getString(section, "menu-id", menuId));
            if (menuId == null || menuId.isBlank()) {
                menuId = "kits_menu";
            }
            previewMenuId = getString(section, "previewMenuId", getString(section, "preview-menu-id", previewMenuId));
            if (previewMenuId == null || previewMenuId.isBlank()) {
                previewMenuId = "kits_preview_menu";
            }
        } catch (Exception ignored) {
            // Safe fallback to defaults
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isFallbackToChatIfMenuFails() {
        return fallbackToChatIfMenuFails;
    }

    public static boolean isAutoRefreshOpenMenus() {
        return autoRefreshOpenMenus;
    }

    public static String getMenuId() {
        return menuId;
    }

    public static String getPreviewMenuId() {
        return previewMenuId;
    }

    public static JsonObject createDefaultMenuConfig() {
        JsonObject menu = new JsonObject();
        menu.addProperty("enabled", true);
        menu.addProperty("fallbackToChatIfMenuFails", true);
        menu.addProperty("autoRefreshOpenMenus", true);
        menu.addProperty("menuId", "kits_menu");
        menu.addProperty("previewMenuId", "kits_preview_menu");
        return menu;
    }

    private static JsonObject extractSection(JsonObject root) {
        if (root == null) {
            return null;
        }
        if (root.has("menu") && root.get("menu").isJsonObject()) {
            return root.getAsJsonObject("menu");
        }
        if (root.has("kits-menu") && root.get("kits-menu").isJsonObject()) {
            return root.getAsJsonObject("kits-menu");
        }
        if (root.has("kit-menu") && root.get("kit-menu").isJsonObject()) {
            return root.getAsJsonObject("kit-menu");
        }
        return null;
    }

    private static boolean getBoolean(JsonObject section, String key, boolean def) {
        return section.has(key) ? section.get(key).getAsBoolean() : def;
    }

    private static String getString(JsonObject section, String key, String def) {
        return section.has(key) ? section.get(key).getAsString() : def;
    }
}
