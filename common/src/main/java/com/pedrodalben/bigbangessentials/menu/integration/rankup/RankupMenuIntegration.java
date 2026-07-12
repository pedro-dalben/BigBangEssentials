package com.pedrodalben.bigbangessentials.menu.integration.rankup;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.integration.rankup.action.*;
import com.pedrodalben.bigbangessentials.menu.integration.rankup.placeholder.*;
import com.pedrodalben.bigbangessentials.menu.integration.rankup.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RankupMenuIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupMenuIntegration.class);
    private static RankupMenuIntegration instance;

    public static synchronized RankupMenuIntegration getInstance() {
        if (instance == null) {
            instance = new RankupMenuIntegration();
        }
        return instance;
    }

    public void register(Path configDir) {
        setupDefaultMenus(configDir);

        MenuSystem menuSystem = MenuSystem.getInstance();

        menuSystem.getDataProviderRegistry().registerProvider("rankup.ranks", new RankupRankDataProvider());
        menuSystem.getDataProviderRegistry().registerProvider("rankup.tasks", new RankupTaskDataProvider());

        menuSystem.getActionRegistry().registerActionHandler("rankup_promote", new RankupPromoteAction());
        menuSystem.getActionRegistry().registerActionHandler("rankup_admin", new RankupAdminAction());
        menuSystem.getActionRegistry().registerActionHandler("rankup_rank_click", new RankupRankClickAction());

        menuSystem.getPlaceholderRegistry().registerPlaceholder("rankup", new RankupPlaceholderResolver());

        LOGGER.info("RankUp menu integration registered successfully.");
    }

    private void setupDefaultMenus(Path configDir) {
        try {
            Files.createDirectories(configDir);
            LOGGER.info("RankUp menu config directory: {}", configDir.toAbsolutePath().normalize());
            String[] menus = new String[]{"rankup_menu.yml", "rankup_rank_details_menu.yml", "rankup_admin_home_menu.yml", "rankup_admin_rank_edit_menu.yml"};
            for (String menu : menus) {
                Path dest = configDir.resolve(menu);
                String hardcodedContent = getHardcodedDefault(menu);
                if (!Files.exists(dest)) {
                    Files.writeString(dest, hardcodedContent, StandardCharsets.UTF_8);
                    LOGGER.info("Created default RankUp menu: {} -> {}", menu, dest.toAbsolutePath().normalize());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to copy/setup default RankUp menus in directory {}: {}", configDir, e.getMessage(), e);
        }
    }

    private String getHardcodedDefault(String filename) {
        if ("rankup_menu.yml".equals(filename)) {
            return getRankupMenuYaml();
        } else if ("rankup_rank_details_menu.yml".equals(filename)) {
            return getRankupRankDetailsMenuYaml();
        } else if ("rankup_admin_home_menu.yml".equals(filename)) {
            return getAdminHomeMenuYaml();
        } else if ("rankup_admin_rank_edit_menu.yml".equals(filename)) {
            return getAdminRankEditMenuYaml();
        }
        return "";
    }

    private String getRankupMenuYaml() {
        return """
            id: "rankup_menu"
            size: 54
            schema-version: 2
            title: "<gold>Progress\\u00e3o de RankUp"

            pagination:
              enabled: true
              source: "rankup.ranks"
              content-slots: [10,11,12,13,14,15,16,19,20,21,23,24,25,28,29,30,31,32,33,34]

            dynamic-item-template:
              item:
                material-id: "{rank_icon}"
                display-name: "{rank_status_color}{rank_display_name}"
                lore:
                  - "<gray>{rank_description}"
                  - ""
                  - "<gray>Grupo LuckPerms: <white>{rank_luckperms_group}"
                  - "<gray>Dinheiro: <white>{rank_money}"
                  - "<gray>Gemas: <white>{rank_gems}"
                  - "<gray>Tarefas: <white>{rank_task_count}"
                  - "<gray>Status: {rank_status_color}{rank_status}"
                  - ""
                  - "<green>Status do rank para refer\\u00eancia."
              actions:
                - type: "rankup_rank_click"
                  params:
                    rank_id: "{rank_id}"
                  clicks: ["LEFT"]

            pages:
              main:
                default-page: true
                items:
                  summary:
                    slot: 4
                    item:
                      material-id: "minecraft:nether_star"
                      display-name: "<gold>Seu Progresso"
                      lore:
                        - "<gray>Rank Atual: <white>{rankup:current_name}"
                        - "<gray>Pr\\u00f3ximo Rank: <white>{rankup:next_name}"
                        - ""
                        - "<gray>Progresso: <white>{rankup:progress_percent}%"
                        - "<gray>Tarefas: <white>{rankup:tasks_completed} / {rankup:tasks_total}"
                        - "<gray>Dinheiro: {rankup:money_status} <white>{rankup:money_balance} / {rankup:money_required}"
                        - "<gray>Gemas: {rankup:gems_status} <white>{rankup:gems_balance} / {rankup:gems_required}"
                  refresh_btn:
                    slot: 8
                    item:
                      material-id: "minecraft:clock"
                      display-name: "<yellow>Atualizar"
                      lore:
                        - "<gray>Clique para atualizar saldos"
                        - "<gray>e progresso de tarefas."
                    actions:
                      - type: "refresh_page"
                  promote_btn:
                    slot: 22
                    item:
                      material-id: "minecraft:diamond"
                      display-name: "<green><b>Promover!"
                      lore:
                        - "<gray>Clique para tentar promo\\u00e7\\u00e3o"
                        - "<gray>ao pr\\u00f3ximo rank."
                        - ""
                        - "<red>Requer:"
                        - "<gray>  - Todas as tarefas conclu\\u00eddas"
                        - "<gray>  - Dinheiro e gemas suficientes"
                    actions:
                      - type: "rankup_promote"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Fechar"
                    actions:
                      - type: "close_menu"
                  prev_btn:
                    slot: 48
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>P\\u00e1gina Anterior"
                    actions:
                      - type: "previous_page"
                  next_btn:
                    slot: 50
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Pr\\u00f3xima P\\u00e1gina"
                    actions:
                      - type: "next_page"
            """;
    }

    private String getRankupRankDetailsMenuYaml() {
        return """
            id: "rankup_rank_details_menu"
            size: 54
            schema-version: 2
            title: "<gold>Tarefas do Rank: {context:rank_display_name}"

            pagination:
              enabled: true
              source: "rankup.tasks"
              content-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34]

            dynamic-item-template:
              item:
                material-id: "minecraft:paper"
                display-name: "{task_symbol} <yellow>{task_display_name}"
                lore:
                  - "<gray>{task_description}"
                  - ""
                  - "<gray>Tipo: <white>{task_type}"
                  - "<gray>Progresso: <white>{task_progress} / {task_target}"
                  - "<gray>Conclus\\u00e3o: <white>{task_percentage}%"
                  - "<gray>Status: {task_symbol}"

            pages:
              main:
                default-page: true
                items:
                  rank_info:
                    slot: 4
                    item:
                      material-id: "{context:rank_icon}"
                      display-name: "{context:rank_status_color}{context:rank_display_name}"
                      lore:
                        - "<gray>{context:rank_description}"
                        - ""
                        - "<gray>Tarefas Requeridas: <white>{context:rank_task_count}"
                  back_btn:
                    slot: 45
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<red>Voltar \\u00e0 Progress\\u00e3o"
                    actions:
                      - type: "open_menu"
                        params:
                          menu-id: "rankup_menu"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Fechar"
                    actions:
                      - type: "close_menu"
                  prev_btn:
                    slot: 48
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>P\\u00e1gina Anterior"
                    actions:
                      - type: "previous_page"
                  next_btn:
                    slot: 50
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Pr\\u00f3xima P\\u00e1gina"
                    actions:
                      - type: "next_page"
            """;
    }

    private String getAdminHomeMenuYaml() {
        return """
            id: "rankup_admin_home_menu"
            size: 54
            schema-version: 2
            title: "<gold>Admin RankUp"

            pagination:
              enabled: true
              source: "rankup.ranks"
              content-slots: [10,11,12,13,14,19,20,21,22,23,24,25,28,29,30,31,32,33,34]

            dynamic-item-template:
              item:
                material-id: "{rank_icon}"
                display-name: "{rank_status_color}{rank_display_name}"
                lore:
                  - "<gray>Ordem: <white>{rank_order}"
                  - "<gray>Grupo LuckPerms: <white>{rank_luckperms_group}"
                  - "<gray>Dinheiro: <white>{rank_money}"
                  - "<gray>Gemas: <white>{rank_gems}"
                  - "<gray>Tarefas: <white>{rank_task_count}"
                  - "<gray>Ativado: <white>{rank_enabled}"
                  - ""
                  - "<green>Clique para editar este rank."
              actions:
                - type: "rankup_admin"
                  params:
                    action: "select_rank_admin"
                    rank_id: "{rank_id}"
                  clicks: ["LEFT"]

            pages:
              main:
                default-page: true
                items:
                  create_rank:
                    slot: 0
                    item:
                      material-id: "minecraft:green_wool"
                      display-name: "<green><b>Criar Novo Rank"
                      lore:
                        - "<gray>Adicionar um novo rank \\u00e0 escada."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "create_rank"
                  save_draft:
                    slot: 15
                    item:
                      material-id: "minecraft:slime_ball"
                      display-name: "<green><b>Salvar Rascunho"
                      lore:
                        - "<gray>Validar e salvar altera\\u00e7\\u00f5es."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "save_draft"
                  discard_draft:
                    slot: 16
                    item:
                      material-id: "minecraft:gunpowder"
                      display-name: "<red><b>Descartar Rascunho"
                      lore:
                        - "<gray>Descartar todas as altera\\u00e7\\u00f5es n\\u00e3o salvas."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "discard_draft"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Fechar"
                    actions:
                      - type: "close_menu"
                  prev_btn:
                    slot: 48
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>P\\u00e1gina Anterior"
                    actions:
                      - type: "previous_page"
                  next_btn:
                    slot: 50
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Pr\\u00f3xima P\\u00e1gina"
                    actions:
                      - type: "next_page"
            """;
    }

    private String getAdminRankEditMenuYaml() {
        return """
            id: "rankup_admin_rank_edit_menu"
            size: 54
            schema-version: 2
            title: "<gold>Editar: {context:rank_id}"

            pages:
              main:
                default-page: true
                items:
                  rank_info:
                    slot: 4
                    item:
                      material-id: "minecraft:book"
                      display-name: "<gold>{context:rank_display_name}"
                      lore:
                        - "<gray>ID: <white>{context:rank_id}"
                  set_display_name:
                    slot: 10
                    item:
                      material-id: "minecraft:name_tag"
                      display-name: "<yellow>Definir Nome"
                      lore:
                        - "<gray>Alterar o nome de exibi\\u00e7\\u00e3o."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_field"
                          field: "display-name"
                  set_icon:
                    slot: 11
                    item:
                      material-id: "minecraft:painting"
                      display-name: "<yellow>Definir \\u00cdcone"
                      lore:
                        - "<gray>Alterar o ID do item do \\u00edcone."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_field"
                          field: "icon"
                  set_luckperms_group:
                    slot: 12
                    item:
                      material-id: "minecraft:command_block"
                      display-name: "<yellow>Definir Grupo LP"
                      lore:
                        - "<gray>Alterar o grupo LuckPerms."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_field"
                          field: "luckperms-group"
                  set_money:
                    slot: 14
                    item:
                      material-id: "minecraft:gold_ingot"
                      display-name: "<yellow>Definir Requisito de Dinheiro"
                      lore:
                        - "<gray>Alterar dinheiro necess\\u00e1rio."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_money"
                  set_gems:
                    slot: 15
                    item:
                      material-id: "minecraft:emerald"
                      display-name: "<yellow>Definir Requisito de Gemas"
                      lore:
                        - "<gray>Alterar gemas necess\\u00e1rias."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_gems"
                  toggle_rank:
                    slot: 16
                    item:
                      material-id: "minecraft:lever"
                      display-name: "<yellow>Alternar Rank"
                      lore:
                        - "<gray>Ativar/Desativar este rank."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "toggle_rank"
                  duplicate_rank:
                    slot: 21
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Duplicar Rank"
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "duplicate_rank"
                  move_up:
                    slot: 22
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<yellow>Mover para Cima"
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "move_up"
                  move_down:
                    slot: 23
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<yellow>Mover para Baixo"
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "move_down"
                  delete_rank:
                    slot: 40
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red><b>Deletar Rank"
                      lore:
                        - "<red>Isso n\\u00e3o pode ser desfeito."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "delete_rank"
                  back_btn:
                    slot: 45
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<red>Voltar ao Admin"
                    actions:
                      - type: "open_menu"
                        params:
                          menu-id: "rankup_admin_home_menu"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Fechar"
                    actions:
                      - type: "close_menu"
            """;
    }
}
