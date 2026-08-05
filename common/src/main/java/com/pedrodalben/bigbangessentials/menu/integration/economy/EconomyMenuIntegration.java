package com.pedrodalben.bigbangessentials.menu.integration.economy;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.integration.economy.provider.GemsTopMenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.integration.economy.provider.MoneyTopMenuDataProvider;
import com.pedrodalben.bigbangessentials.util.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EconomyMenuIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(EconomyMenuIntegration.class);
    private static final List<String> TOP_MENUS = List.of("money_top_menu.yml", "gems_top_menu.yml");

    public void initialize() {
        Path menusDir = Platform.getConfigDir().resolve("bigbangessentials").resolve("menus");
        setupDefaultMenus(menusDir);

        for (String menu : TOP_MENUS) {
            Path path = menusDir.resolve(menu);
            if (Files.exists(path) && MenuSystem.getInstance().getRegistry().getMenu(menu.replace(".yml", "")).isEmpty()) {
                MenuSystem.getInstance().getPersistenceService().loadMenu(path);
            }
        }

        // Register data providers
        MenuSystem.getInstance().getDataProviderRegistry().registerProvider("economy.top.money", new MoneyTopMenuDataProvider());
        MenuSystem.getInstance().getDataProviderRegistry().registerProvider("economy.top.gems", new GemsTopMenuDataProvider());
    }

    private void setupDefaultMenus(Path menusDir) {
        try {
            Files.createDirectories(menusDir);
            for (String menu : TOP_MENUS) {
                Path destination = menusDir.resolve(menu);
                if (Files.exists(destination)) {
                    continue;
                }
                try (var input = getClass().getResourceAsStream("/default-config/bigbangessentials/menus/" + menu)) {
                    if (input != null) {
                        Files.copy(input, destination);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to set up economy menus in {}: {}", menusDir, e.getMessage(), e);
        }
    }
}
