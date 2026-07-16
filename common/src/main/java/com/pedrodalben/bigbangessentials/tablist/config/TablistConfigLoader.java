package com.pedrodalben.bigbangessentials.tablist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TablistConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistConfigLoader.class);
    private static final Gson GSON = new GsonBuilder().create();
    
    private static TablistConfig currentConfig = new TablistConfig();

    public static boolean load() {
        try {
            JsonObject configJson = ConfigManager.getInstance().getConfig(ConfigManager.TABLIST_CONFIG);
            
            // Legacy fallback if needed (from old versions)
            if (configJson == null || (!configJson.has("tablist") && !configJson.has("enabled"))) {
                configJson = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            }

            if (configJson == null) {
                LOGGER.info("No tablist config found, using defaults.");
                return true;
            }
            
            // Check if we need migration
            if (!configJson.has("_configVersion") || configJson.get("_configVersion").getAsInt() < 2) {
                JsonObject migrated = TablistConfigMigrator.migrate(configJson);
                // Save migrated json to disk (only if migration produced new root)
                com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().saveConfig(com.pedrodalben.bigbangessentials.config.ConfigManager.TABLIST_CONFIG, migrated);
                configJson = migrated;
            }

            TablistConfig loadedConfig = GSON.fromJson(configJson, TablistConfig.class);
            if (TablistConfigValidator.validate(loadedConfig)) {
                currentConfig = loadedConfig;
                return true;
            } else {
                LOGGER.error("Failed to validate new tablist config. Keeping previous valid configuration.");
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Exception while loading tablist config: {}", e.getMessage(), e);
            return false;
        }
    }

    public static TablistConfig getConfig() {
        return currentConfig;
    }
}
