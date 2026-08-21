package com.pedrodalben.bigbangessentials.tablist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TablistConfigValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistConfigValidator.class);

    public static boolean validate(TablistConfig config) {
        if (config == null || config.tablist == null) {
            LOGGER.error("Tablist config is null or missing 'tablist' section.");
            return false;
        }
        
        boolean isValid = true;
        TablistConfig.TablistSection tablist = config.tablist;

        if (tablist.performance.fallbackRefreshTicks <= 0) {
            LOGGER.warn("Invalid 'tablist.performance.fallbackRefreshTicks': {}. Fallback to 100.", tablist.performance.fallbackRefreshTicks);
            tablist.performance.fallbackRefreshTicks = 100;
        }

        if (tablist.performance.maxPacketUpdatesPerTick <= 0) {
            LOGGER.warn("Invalid 'tablist.performance.maxPacketUpdatesPerTick': {}. Fallback to 250.", tablist.performance.maxPacketUpdatesPerTick);
            tablist.performance.maxPacketUpdatesPerTick = 250;
        }

        if (tablist.performance.permissionRefreshTicks <= 0) {
            LOGGER.warn("Invalid 'tablist.performance.permissionRefreshTicks': {}. Fallback to 100.", tablist.performance.permissionRefreshTicks);
            tablist.performance.permissionRefreshTicks = 100;
        }

        // Warn about unimplemented config sections
        if (tablist.performance.componentCacheSize > 0) {
            LOGGER.info("'tablist.performance.componentCacheSize' is loaded but not yet implemented. The value is preserved for future use.");
        }
        if (tablist.diagnostics.enabled) {
            LOGGER.info("'tablist.diagnostics' section is loaded but not yet implemented. Slow-update warnings and packet statistics are planned features.");
        }
        if (tablist.objectives.belowName.enabled) {
            LOGGER.error("'tablist.objectives.belowName.enabled' is true but belowName objective is NOT IMPLEMENTED. This setting will be ignored.");
            tablist.objectives.belowName.enabled = false;
        }

        if (tablist.headerFooter.enabled && !tablist.headerFooter.designs.isEmpty()) {
            boolean hasDefault = false;
            for (TablistConfig.DesignSection design : tablist.headerFooter.designs) {
                if (design.isDefault) { hasDefault = true; break; }
            }
            if (!hasDefault) {
                LOGGER.warn("No default design found in 'tablist.headerFooter.designs'. Auto-selecting first design as fallback.");
                tablist.headerFooter.designs.get(0).isDefault = true;
            }
        }

        if (tablist.playerList.enabled && tablist.playerList.defaultFormat == null) {
            LOGGER.warn("'tablist.playerList.defaultFormat' is null. Fallback to '{prefix}{tag}{name}{suffix}{afk}'.");
            tablist.playerList.defaultFormat = "{prefix}{tag}{name}{suffix}{afk}";
        }

        if (tablist.sorting.enabled && (tablist.sorting.rules == null || tablist.sorting.rules.isEmpty())) {
            LOGGER.warn("'tablist.sorting' is enabled but no rules are defined. Sorting will use default alphabetical order.");
        }

        if (tablist.visibility.hideVanished && (tablist.visibility.vanishBypassPermission == null || tablist.visibility.vanishBypassPermission.isEmpty())) {
            LOGGER.warn("'tablist.visibility.vanishBypassPermission' is empty. Vanished players will be hidden from ALL viewers.");
        }

        return isValid;
    }
}
