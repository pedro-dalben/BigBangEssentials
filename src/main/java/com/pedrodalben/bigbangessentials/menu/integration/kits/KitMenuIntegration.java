package com.pedrodalben.bigbangessentials.menu.integration.kits;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class KitMenuIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(KitMenuIntegration.class);
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private static KitMenuIntegration instance;
    private int tickCounter = 0;
    private boolean eventBusRegistered = false;

    public static synchronized KitMenuIntegration getInstance() {
        if (instance == null) {
            instance = new KitMenuIntegration();
        }
        return instance;
    }

    public void register(Path configDir) {
        KitMenuConfig.load();
        setupDefaultMenu(configDir);

        MenuSystem menuSystem = MenuSystem.getInstance();
        menuSystem.getDataProviderRegistry().registerProvider("kits.all", new KitMenuDataProvider());
        menuSystem.getDataProviderRegistry().registerProvider("kits.preview", new KitPreviewMenuDataProvider());
        menuSystem.getActionRegistry().registerActionHandler("claim_kit", new KitClaimMenuAction());
        menuSystem.getActionRegistry().registerActionHandler("open_kit_preview", new OpenKitPreviewMenuAction());
        menuSystem.getPlaceholderRegistry().registerPlaceholder("kits", new KitPlaceholderResolver());

        if (!eventBusRegistered) {
            NeoForge.EVENT_BUS.register(this);
            eventBusRegistered = true;
        }
        LOGGER.info("Kit menu integration registered (menuId={}, autoRefresh={})",
            KitMenuConfig.getMenuId(), KitMenuConfig.isAutoRefreshOpenMenus());
    }

    public static void refreshOpenMenus() {
        try {
            MenuSystem menuSystem = MenuSystem.getInstance();
            if (menuSystem != null && menuSystem.getMenuService() != null) {
                menuSystem.getMenuService().refreshSessionsUsingSource("kits.all");
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to refresh kit menus: {}", e.getMessage());
        }
    }

    private void setupDefaultMenu(Path configDir) {
        try {
            Files.createDirectories(configDir);
            copyDefaultMenu(configDir.resolve("kits_menu.yml"), "/default-config/bigbangessentials/menus/kits_menu.yml", getHardcodedDefaultMenu());
            copyDefaultMenu(configDir.resolve(KitMenuConfig.getPreviewMenuId() + ".yml"),
                "/default-config/bigbangessentials/menus/kits_preview_menu.yml", getHardcodedPreviewMenu());
        } catch (Exception e) {
            LOGGER.error("Failed to set up default kits menu in {}: {}", configDir, e.getMessage(), e);
        }
    }

    private void copyDefaultMenu(Path destination, String resourcePath, String fallbackContent) throws Exception {
        if (Files.exists(destination)) {
            return;
        }

        try (java.io.InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                Files.copy(in, destination);
            } else {
                Files.writeString(destination, fallbackContent, StandardCharsets.UTF_8);
            }
        }
    }

    private String getHardcodedDefaultMenu() {
        return """
            id: "kits_menu"
            size: 54
            title: "<gold>Kits do Servidor <gray>(<white>{kits:available}<gray>/<white>{kits:total}<gray>)"

            pagination:
              enabled: true
              source: "kits.all"
              content-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34]

            dynamic-item-template:
              item:
                material-id: "{kit_icon}"
                display-name: "{kit_status_color}{kit_display_name}"
                lore:
                  - "<gray>Nome: <white>{kit_name}"
                  - "<gray>Itens: <white>{kit_items}"
                  - "<gray>Uso: <white>{kit_usage_display}"
                  - "<gray>Cooldown: <white>{kit_cooldown_display}"
                  - "<gray>Tempo restante: <white>{kit_remaining_display}"
                  - "<gray>Status: {kit_status}"
                  - ""
                  - "<green>Esquerdo: pegar o kit."
                  - "<yellow>Direito: ver detalhes."
              actions:
                - type: "claim_kit"
                  params:
                    kit-name: "{kit_name}"
                  clicks: ["LEFT"]
                - type: "open_kit_preview"
                  params:
                    kit-name: "{kit_name}"
                  clicks: ["RIGHT"]

            pages:
              main:
                default-page: true
                items:
                  summary:
                    slot: 4
                    item:
                      material-id: "minecraft:book"
                      display-name: "<yellow>Resumo dos Kits"
                      lore:
                        - "<gray>Total: <white>{kits:total}"
                        - "<gray>Disponíveis: <green>{kits:available}"
                        - "<gray>Em cooldown: <yellow>{kits:cooldown}"
                        - "<gray>Bloqueados: <red>{kits:locked}"
                        - "<gray>Desativados: <dark_gray>{kits:disabled}"
                        - "<gray>Esgotados: <gray>{kits:used}"
                  back_btn:
                    slot: 45
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<red>Voltar"
                    actions:
                      - type: "back_menu"
                  prev_btn:
                    slot: 48
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Página Anterior"
                    actions:
                      - type: "previous_page"
                  next_btn:
                    slot: 50
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Próxima Página"
                    actions:
                      - type: "next_page"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Fechar"
                    actions:
                      - type: "close_menu"
            """;
    }

    private String getHardcodedPreviewMenu() {
        return """
            id: "kits_preview_menu"
            size: 54
            title: "<gold>Preview: <white>{kit_display_name}"

            pagination:
              enabled: true
              source: "kits.preview"
              content-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34]

            dynamic-item-template:
              item:
                material-id: "{kit_item_material}"
                display-name: "{kit_item_display_name}"
                lore:
                  - "<gray>Quantidade: <white>{kit_item_count}"
                  - "<gray>Posição: <white>{kit_item_index}/{kit_items}"
                  - "<gray>ID: <white>{kit_item_material}"
                  - ""
                  - "<yellow>Visualização do conteúdo do kit."

            pages:
              main:
                default-page: true
                items:
                  info:
                    slot: 4
                    item:
                      material-id: "minecraft:chest"
                      display-name: "<yellow>{kit_display_name}"
                      lore:
                        - "<gray>Nome: <white>{kit_name}"
                        - "<gray>Itens: <white>{kit_items}"
                        - "<gray>Cooldown: <white>{kit_cooldown_display}"
                        - "<gray>Tempo restante: <white>{kit_remaining_display}"
                        - "<gray>Status: {kit_status}"
                        - ""
                        - "<gray>{kit_description_short}"
                  back_btn:
                    slot: 45
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<red>Voltar"
                    actions:
                      - type: "open_menu"
                        params:
                          menu-id: "kits_menu"
                  take_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:emerald"
                      display-name: "<green>Pegar"
                    actions:
                      - type: "claim_kit"
                        params:
                          kit-name: "{kit_name}"
                  prev_btn:
                    slot: 48
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Página Anterior"
                    actions:
                      - type: "previous_page"
                  next_btn:
                    slot: 50
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Próxima Página"
                    actions:
                      - type: "next_page"
                  close_btn:
                    slot: 53
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Fechar"
                    actions:
                      - type: "close_menu"
            """;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!KitMenuConfig.isAutoRefreshOpenMenus()) {
            return;
        }

        tickCounter++;
        if (tickCounter < REFRESH_INTERVAL_TICKS) {
            return;
        }

        tickCounter = 0;
        refreshOpenMenus();
    }
}
