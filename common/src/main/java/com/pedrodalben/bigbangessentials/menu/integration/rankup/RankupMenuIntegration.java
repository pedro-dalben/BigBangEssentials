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
            title: "<gold>RankUp Progression"

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
                  - "<gray>LuckPerms Group: <white>{rank_luckperms_group}"
                  - "<gray>Money: <white>{rank_money}"
                  - "<gray>Gems: <white>{rank_gems}"
                  - "<gray>Tasks: <white>{rank_task_count}"
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
                      display-name: "<gold>Your Progress"
                      lore:
                        - "<gray>Current Rank: <white>{rankup:current_name}"
                        - "<gray>Next Rank: <white>{rankup:next_name}"
                        - ""
                        - "<gray>Progress: <white>{rankup:progress_percent}%"
                        - "<gray>Tasks: <white>{rankup:tasks_completed} / {rankup:tasks_total}"
                        - "<gray>Money: {rankup:money_status} <white>{rankup:money_balance} / {rankup:money_required}"
                        - "<gray>Gems: {rankup:gems_status} <white>{rankup:gems_balance} / {rankup:gems_required}"
                  refresh_btn:
                    slot: 8
                    item:
                      material-id: "minecraft:clock"
                      display-name: "<yellow>Refresh"
                      lore:
                        - "<gray>Click to refresh balances"
                        - "<gray>and task progress."
                    actions:
                      - type: "refresh_page"
                  promote_btn:
                    slot: 22
                    item:
                      material-id: "minecraft:diamond"
                      display-name: "<green><b>Promote!"
                      lore:
                        - "<gray>Click to attempt promotion"
                        - "<gray>to the next rank."
                        - ""
                        - "<red>Requires:"
                        - "<gray>  - All tasks completed"
                        - "<gray>  - Enough money & gems"
                    actions:
                      - type: "rankup_promote"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Close"
                    actions:
                      - type: "close_menu"
                  prev_btn:
                    slot: 48
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Previous Page"
                    actions:
                      - type: "previous_page"
                  next_btn:
                    slot: 50
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Next Page"
                    actions:
                      - type: "next_page"
            """;
    }

    private String getRankupRankDetailsMenuYaml() {
        return """
            id: "rankup_rank_details_menu"
            size: 54
            schema-version: 2
            title: "<gold>Rank Tasks: {context:rank_display_name}"

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
                  - "<gray>Type: <white>{task_type}"
                  - "<gray>Progress: <white>{task_progress} / {task_target}"
                  - "<gray>Completion: <white>{task_percentage}%"
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
                        - "<gray>Tasks Required: <white>{context:rank_task_count}"
                  back_btn:
                    slot: 45
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<red>Back to Progression"
                    actions:
                      - type: "open_menu"
                        params:
                          menu-id: "rankup_menu"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Close"
                    actions:
                      - type: "close_menu"
                  prev_btn:
                    slot: 48
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Previous Page"
                    actions:
                      - type: "previous_page"
                  next_btn:
                    slot: 50
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Next Page"
                    actions:
                      - type: "next_page"
            """;
    }

    private String getAdminHomeMenuYaml() {
        return """
            id: "rankup_admin_home_menu"
            size: 54
            schema-version: 2
            title: "<gold>RankUp Admin"

            pagination:
              enabled: true
              source: "rankup.ranks"
              content-slots: [10,11,12,13,14,19,20,21,22,23,24,25,28,29,30,31,32,33,34]

            dynamic-item-template:
              item:
                material-id: "{rank_icon}"
                display-name: "{rank_status_color}{rank_display_name}"
                lore:
                  - "<gray>Order: <white>{rank_order}"
                  - "<gray>LuckPerms Group: <white>{rank_luckperms_group}"
                  - "<gray>Money: <white>{rank_money}"
                  - "<gray>Gems: <white>{rank_gems}"
                  - "<gray>Tasks: <white>{rank_task_count}"
                  - "<gray>Enabled: <white>{rank_enabled}"
                  - ""
                  - "<green>Click to edit this rank."
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
                      display-name: "<green><b>Create New Rank"
                      lore:
                        - "<gray>Add a new rank to the ladder."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "create_rank"
                  save_draft:
                    slot: 15
                    item:
                      material-id: "minecraft:slime_ball"
                      display-name: "<green><b>Save Draft"
                      lore:
                        - "<gray>Validate and save changes."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "save_draft"
                  discard_draft:
                    slot: 16
                    item:
                      material-id: "minecraft:gunpowder"
                      display-name: "<red><b>Discard Draft"
                      lore:
                        - "<gray>Discard all unsaved changes."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "discard_draft"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Close"
                    actions:
                      - type: "close_menu"
                  prev_btn:
                    slot: 48
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Previous Page"
                    actions:
                      - type: "previous_page"
                  next_btn:
                    slot: 50
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Next Page"
                    actions:
                      - type: "next_page"
            """;
    }

    private String getAdminRankEditMenuYaml() {
        return """
            id: "rankup_admin_rank_edit_menu"
            size: 54
            schema-version: 2
            title: "<gold>Edit: {context:rank_id}"

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
                      display-name: "<yellow>Set Display Name"
                      lore:
                        - "<gray>Change the display name."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_field"
                          field: "display-name"
                  set_icon:
                    slot: 11
                    item:
                      material-id: "minecraft:painting"
                      display-name: "<yellow>Set Icon"
                      lore:
                        - "<gray>Change the icon item ID."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_field"
                          field: "icon"
                  set_luckperms_group:
                    slot: 12
                    item:
                      material-id: "minecraft:command_block"
                      display-name: "<yellow>Set LP Group"
                      lore:
                        - "<gray>Change the LuckPerms group."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_field"
                          field: "luckperms-group"
                  set_money:
                    slot: 14
                    item:
                      material-id: "minecraft:gold_ingot"
                      display-name: "<yellow>Set Money Requirement"
                      lore:
                        - "<gray>Change money needed."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_money"
                  set_gems:
                    slot: 15
                    item:
                      material-id: "minecraft:emerald"
                      display-name: "<yellow>Set Gems Requirement"
                      lore:
                        - "<gray>Change gems needed."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "set_gems"
                  toggle_rank:
                    slot: 16
                    item:
                      material-id: "minecraft:lever"
                      display-name: "<yellow>Toggle Rank"
                      lore:
                        - "<gray>Enable/Disable this rank."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "toggle_rank"
                  duplicate_rank:
                    slot: 21
                    item:
                      material-id: "minecraft:paper"
                      display-name: "<yellow>Duplicate Rank"
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "duplicate_rank"
                  move_up:
                    slot: 22
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<yellow>Move Up"
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "move_up"
                  move_down:
                    slot: 23
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<yellow>Move Down"
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "move_down"
                  delete_rank:
                    slot: 40
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red><b>Delete Rank"
                      lore:
                        - "<red>This cannot be undone."
                    actions:
                      - type: "rankup_admin"
                        params:
                          action: "delete_rank"
                  back_btn:
                    slot: 45
                    item:
                      material-id: "minecraft:arrow"
                      display-name: "<red>Back to Admin Home"
                    actions:
                      - type: "open_menu"
                        params:
                          menu-id: "rankup_admin_home_menu"
                  close_btn:
                    slot: 49
                    item:
                      material-id: "minecraft:barrier"
                      display-name: "<red>Close"
                    actions:
                      - type: "close_menu"
            """;
    }
}
