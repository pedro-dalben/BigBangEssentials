package com.pedrodalben.bigbangessentials.menu.integration.teleportation;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.action.*;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.condition.*;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.placeholder.*;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.provider.*;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public class TeleportMenuIntegration {
    private static TeleportMenuIntegration instance;

    public static synchronized TeleportMenuIntegration getInstance() {
        if (instance == null) {
            instance = new TeleportMenuIntegration();
        }
        return instance;
    }

    public void register(Path configDir) {
        // Load configurations
        TeleportMenuConfig.load();

        // Write default menus to config directory if they don't exist
        setupDefaultMenus(configDir);

        MenuSystem menuSystem = MenuSystem.getInstance();

        // 1. Register Data Providers
        menuSystem.getDataProviderRegistry().registerProvider("warps.global", new GlobalWarpsMenuDataProvider());
        menuSystem.getDataProviderRegistry().registerProvider("homes.player", new PlayerHomesMenuDataProvider());
        menuSystem.getDataProviderRegistry().registerProvider("pwarps.public", new PublicPlayerWarpsMenuDataProvider());
        menuSystem.getDataProviderRegistry().registerProvider("pwarps.own", new OwnPlayerWarpsMenuDataProvider());

        // 2. Register Actions
        menuSystem.getActionRegistry().registerActionHandler("teleport_warp", new TeleportToWarpMenuAction());
        menuSystem.getActionRegistry().registerActionHandler("teleport_home", new TeleportToHomeMenuAction());
        menuSystem.getActionRegistry().registerActionHandler("teleport_pwarp", new TeleportToPlayerWarpMenuAction());
        menuSystem.getActionRegistry().registerActionHandler("delete_home", new DeleteHomeMenuAction());
        menuSystem.getActionRegistry().registerActionHandler("delete_pwarp", new DeletePlayerWarpMenuAction());

        // 3. Register Conditions
        menuSystem.getConditionRegistry().registerConditionHandler("warp_exists", new WarpExistsCondition());
        menuSystem.getConditionRegistry().registerConditionHandler("home_exists", new HomeExistsCondition());
        menuSystem.getConditionRegistry().registerConditionHandler("pwarp_exists", new PlayerWarpExistsCondition());
        menuSystem.getConditionRegistry().registerConditionHandler("is_pwarp_owner", new IsPlayerWarpOwnerCondition());
        menuSystem.getConditionRegistry().registerConditionHandler("can_use_teleport_menu", new CanUseTeleportMenuCondition());

        // 4. Register Placeholders
        menuSystem.getPlaceholderRegistry().registerPlaceholder("teleport", new TeleportPlaceholderResolver());

        // 5. Register Event Listener
        com.pedrodalben.bigbangessentials.util.Platform.registerEventListener(this);
    }

    private void setupDefaultMenus(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            String[] menus = new String[]{"teleports_main_menu.yml", "warps_menu.yml", "homes_menu.yml", "pwarps_menu.yml", "confirm_delete_home.yml", "confirm_delete_pwarp.yml"};
            for (String menu : menus) {
                Path dest = dir.resolve(menu);
                if (!java.nio.file.Files.exists(dest)) {
                    try (java.io.InputStream in = getClass().getResourceAsStream("/default-config/bigbangessentials/menus/" + menu)) {
                        if (in != null) {
                            java.nio.file.Files.copy(in, dest);
                        } else {
                            writeDefaultMenu(dest, getHardcodedDefault(menu));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to copy/setup default menus in directory {}: {}", dir, e.getMessage(), e);
        }
    }

    private String getHardcodedDefault(String filename) {
        switch (filename) {
            case "teleports_main_menu.yml": return getTeleportsMainMenuYaml();
            case "warps_menu.yml": return getWarpsMenuYaml();
            case "homes_menu.yml": return getHomesMenuYaml();
            case "pwarps_menu.yml": return getPwarpsMenuYaml();
            case "confirm_delete_home.yml": return getConfirmDeleteHomeYaml();
            case "confirm_delete_pwarp.yml": return getConfirmDeletePwarpYaml();
            default: return "";
        }
    }

    private void writeDefaultMenu(Path file, String content) throws java.io.IOException {
        if (!java.nio.file.Files.exists(file)) {
            java.nio.file.Files.writeString(file, content);
        }
    }

    private String getTeleportsMainMenuYaml() {
        return "id: \"teleports_main_menu\"\n" +
               "size: 27\n" +
               "title: \"<gold>Menu de Teleportes\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      warps_btn:\n" +
               "        slot: 11\n" +
               "        item:\n" +
               "          material-id: \"minecraft:emerald\"\n" +
               "          display-name: \"<yellow>Global Warps\"\n" +
               "          lore:\n" +
               "            - \"<gray>Clique para ver todos os\"\n" +
               "            - \"<gray>warps públicos do servidor.\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"warps_menu\"\n" +
               "      homes_btn:\n" +
               "        slot: 13\n" +
               "        item:\n" +
               "          material-id: \"minecraft:red_bed\"\n" +
               "          display-name: \"<yellow>Minhas Homes\"\n" +
               "          lore:\n" +
               "            - \"<gray>Clique para gerenciar ou\"\n" +
               "            - \"<gray>teleportar para suas homes.\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"homes_menu\"\n" +
               "      pwarps_btn:\n" +
               "        slot: 15\n" +
               "        item:\n" +
               "          material-id: \"minecraft:player_head\"\n" +
               "          display-name: \"<yellow>Player Warps\"\n" +
               "          lore:\n" +
               "            - \"<gray>Clique para ver os warps\"\n" +
               "            - \"<gray>públicos criados por players.\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"pwarps_menu\"\n";
    }

    private String getWarpsMenuYaml() {
        return "id: \"warps_menu\"\n" +
               "size: 54\n" +
               "title: \"<gold>Warps do Servidor\"\n\n" +
               "pagination:\n" +
               "  enabled: true\n" +
               "  source: \"warps.global\"\n" +
               "  content-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34]\n\n" +
               "dynamic-item-template:\n" +
               "  item:\n" +
               "    material-id: \"{warp_icon}\"\n" +
               "    display-name: \"<yellow>{warp_name}\"\n" +
               "    lore:\n" +
               "      - \"<gray>Mundo: <white>{warp_world}\"\n" +
               "      - \"<gray>Coordenadas: <white>{warp_x}, {warp_y}, {warp_z}\"\n" +
               "      - \"\"\n" +
               "      - \"<green>Clique para teleportar.\"\n" +
               "  actions:\n" +
               "    - type: \"teleport_warp\"\n" +
               "      params:\n" +
               "        warp-id: \"{warp_name}\"\n" +
               "    - type: \"close_menu\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      back_btn:\n" +
               "        slot: 45\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<red>Voltar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"teleports_main_menu\"\n" +
               "      prev_btn:\n" +
               "        slot: 48\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Página Anterior\"\n" +
               "        actions:\n" +
               "          - type: \"previous_page\"\n" +
               "      next_btn:\n" +
               "        slot: 50\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Próxima Página\"\n" +
               "        actions:\n" +
               "          - type: \"next_page\"\n";
    }

    private String getHomesMenuYaml() {
        return "id: \"homes_menu\"\n" +
               "size: 54\n" +
               "title: \"<gold>Minhas Homes\"\n\n" +
               "pagination:\n" +
               "  enabled: true\n" +
               "  source: \"homes.player\"\n" +
               "  content-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34]\n\n" +
               "dynamic-item-template:\n" +
               "  item:\n" +
               "    material-id: \"{home_icon}\"\n" +
               "    display-name: \"<yellow>{home_name}\"\n" +
               "    lore:\n" +
               "      - \"<gray>Mundo: <white>{home_world}\"\n" +
               "      - \"<gray>Coordenadas: <white>{home_x}, {home_y}, {home_z}\"\n" +
               "      - \"\"\n" +
               "      - \"<green>Clique Esquerdo para teleportar.\"\n" +
               "      - \"<red>Clique Direito para deletar.\"\n" +
               "  actions:\n" +
               "    - type: \"teleport_home\"\n" +
               "      params:\n" +
               "        home-name: \"{home_name}\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      back_btn:\n" +
               "        slot: 45\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<red>Voltar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"teleports_main_menu\"\n" +
               "      prev_btn:\n" +
               "        slot: 48\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Página Anterior\"\n" +
               "        actions:\n" +
               "          - type: \"previous_page\"\n" +
               "      next_btn:\n" +
               "        slot: 50\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Próxima Página\"\n" +
               "        actions:\n" +
               "          - type: \"next_page\"\n";
    }

    private String getPwarpsMenuYaml() {
        return "id: \"pwarps_menu\"\n" +
               "size: 54\n" +
               "title: \"<gold>Player Warps Públicos\"\n\n" +
               "pagination:\n" +
               "  enabled: true\n" +
               "  source: \"pwarps.public\"\n" +
               "  content-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34]\n\n" +
               "dynamic-item-template:\n" +
               "  item:\n" +
               "    material-id: \"{pwarp_icon}\"\n" +
               "    display-name: \"<yellow>{pwarp_name}\"\n" +
               "    lore:\n" +
               "      - \"<gray>Dono: <white>{pwarp_owner_name}\"\n" +
               "      - \"<gray>Mundo: <white>{pwarp_world}\"\n" +
               "      - \"<gray>Coordenadas: <white>{pwarp_x}, {pwarp_y}, {pwarp_z}\"\n" +
               "      - \"<gray>Visitas: <gold>{pwarp_visits}\"\n" +
               "      - \"\"\n" +
               "      - \"<green>Clique Esquerdo para teleportar.\"\n" +
               "      - \"<red>Clique Direito (Dono) para deletar.\"\n" +
               "  actions:\n" +
               "    - type: \"teleport_pwarp\"\n" +
               "      params:\n" +
               "        pwarp-name: \"{pwarp_name}\"\n" +
               "        pwarp-owner-uuid: \"{pwarp_owner_uuid}\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      back_btn:\n" +
               "        slot: 45\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<red>Voltar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"teleports_main_menu\"\n" +
               "      prev_btn:\n" +
               "        slot: 48\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Página Anterior\"\n" +
               "        actions:\n" +
               "          - type: \"previous_page\"\n" +
               "      next_btn:\n" +
               "        slot: 50\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Próxima Página\"\n" +
               "        actions:\n" +
               "          - type: \"next_page\"\n";
    }

    private String getConfirmDeleteHomeYaml() {
        return "id: \"confirm_delete_home\"\n" +
               "size: 27\n" +
               "title: \"<red>Confirmar Exclusão\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      text_info:\n" +
               "        slot: 13\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Excluir Home?\"\n" +
               "          lore:\n" +
               "            - \"<gray>Home: <white>{context:home_name}\"\n" +
               "            - \"\"\n" +
               "            - \"<red>Esta ação não pode ser desfeita!\"\n" +
               "      confirm_btn:\n" +
               "        slot: 11\n" +
               "        item:\n" +
               "          material-id: \"minecraft:green_wool\"\n" +
               "          display-name: \"<green>Confirmar Exclusão\"\n" +
               "        actions:\n" +
               "          - type: \"delete_home\"\n" +
               "            params:\n" +
               "              home-name: \"{context:home_name}\"\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"homes_menu\"\n" +
               "      cancel_btn:\n" +
               "        slot: 15\n" +
               "        item:\n" +
               "          material-id: \"minecraft:red_wool\"\n" +
               "          display-name: \"<red>Cancelar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"homes_menu\"\n";
    }

    private String getConfirmDeletePwarpYaml() {
        return "id: \"confirm_delete_pwarp\"\n" +
               "size: 27\n" +
               "title: \"<red>Confirmar Exclusão\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      text_info:\n" +
               "        slot: 13\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Excluir Player Warp?\"\n" +
               "          lore:\n" +
               "            - \"<gray>Warp: <white>{context:pwarp_name}\"\n" +
               "            - \"\"\n" +
               "            - \"<red>Esta ação não pode ser desfeita!\"\n" +
               "      confirm_btn:\n" +
               "        slot: 11\n" +
               "        item:\n" +
               "          material-id: \"minecraft:green_wool\"\n" +
               "          display-name: \"<green>Confirmar Exclusão\"\n" +
               "        actions:\n" +
               "          - type: \"delete_pwarp\"\n" +
               "            params:\n" +
               "              pwarp-name: \"{context:pwarp_name}\"\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"pwarps_menu\"\n" +
               "      cancel_btn:\n" +
               "        slot: 15\n" +
               "        item:\n" +
               "          material-id: \"minecraft:red_wool\"\n" +
               "          display-name: \"<red>Cancelar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"pwarps_menu\"\n";
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onWarpCreated(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.WarpCreatedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("warps.global");
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onWarpDeleted(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.WarpDeletedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("warps.global");
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onWarpUpdated(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.WarpUpdatedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("warps.global");
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onHomeCreated(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.HomeCreatedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(event.getPlayerId());
                if (player != null) {
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                }
            }
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onHomeDeleted(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.HomeDeletedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(event.getPlayerId());
                if (player != null) {
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                }
            }
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onHomeUpdated(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.HomeUpdatedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(event.getPlayerId());
                if (player != null) {
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                }
            }
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onPlayerWarpCreated(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.PlayerWarpCreatedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("pwarps.public");
            net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
            if (server != null) {
                ServerPlayer owner = server.getPlayerList().getPlayer(event.getOwnerId());
                if (owner != null) {
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(owner);
                }
            }
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onPlayerWarpDeleted(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.PlayerWarpDeletedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("pwarps.public");
            net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
            if (server != null) {
                ServerPlayer owner = server.getPlayerList().getPlayer(event.getOwnerId());
                if (owner != null) {
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(owner);
                }
            }
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onPlayerWarpUpdated(com.pedrodalben.bigbangessentials.menu.integration.teleportation.event.TeleportationEvents.PlayerWarpUpdatedEvent event) {
        if (TeleportMenuConfig.isAutoRefreshOpenMenus()) {
            MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("pwarps.public");
            net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
            if (server != null) {
                ServerPlayer owner = server.getPlayerList().getPlayer(event.getOwnerId());
                if (owner != null) {
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(owner);
                }
            }
        }
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TeleportMenuIntegration.class);
    private static final java.util.Map<String, Long> lastErrorLogs = new java.util.concurrent.ConcurrentHashMap<>();

    public static void logMenuFailure(java.util.UUID playerUuid, String menuId, String sourceCommand, String reason, Throwable exception) {
        logMenuFailure(playerUuid, menuId, sourceCommand, reason, null, exception);
    }

    public static void logMenuFailure(java.util.UUID playerUuid, String menuId, String sourceCommand, String reason, java.util.UUID correlationId, Throwable exception) {
        String key = playerUuid + ":" + menuId + ":" + reason;
        long now = System.currentTimeMillis();
        Long lastLog = lastErrorLogs.get(key);
        
        // Rate limit: log at most once every 10 seconds per unique player/menu/reason combo
        if (lastLog == null || (now - lastLog) > 10000) {
            lastErrorLogs.put(key, now);
            String corrStr = correlationId != null ? correlationId.toString() : "N/A";
            if (exception != null) {
                LOGGER.warn("Teleport Menu Fallback: Menu '{}' failed to open for player {} (Command: {}, CorrelationID: {}). Reason: {}. Exception: {}",
                        menuId, playerUuid, sourceCommand, corrStr, reason, exception.getMessage());
            } else {
                LOGGER.warn("Teleport Menu Fallback: Menu '{}' failed to open for player {} (Command: {}, CorrelationID: {}). Reason: {}.",
                        menuId, playerUuid, sourceCommand, corrStr, reason);
            }
        }
    }
}
