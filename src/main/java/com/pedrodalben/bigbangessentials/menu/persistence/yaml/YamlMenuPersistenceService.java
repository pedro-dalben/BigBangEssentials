package com.pedrodalben.bigbangessentials.menu.persistence.yaml;

import com.pedrodalben.bigbangessentials.menu.api.MenuPersistenceService;
import com.pedrodalben.bigbangessentials.menu.persistence.*;
import com.pedrodalben.bigbangessentials.menu.model.*;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YamlMenuPersistenceService implements MenuPersistenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlMenuPersistenceService.class);

    private final Path menusDir;
    private final MenuRegistry registry;
    private final YamlMenuParser parser = new YamlMenuParser();

    public YamlMenuPersistenceService(Path menusDir, MenuRegistry registry) {
        this.menusDir = menusDir;
        this.registry = registry;
    }

    @Override
    public LoadReport loadAllMenus() {
        if (!Files.exists(menusDir)) {
            try { Files.createDirectories(menusDir); } catch (Exception ignored) {}
            return new LoadReport();
        }

        try (Stream<Path> stream = Files.walk(menusDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                  .forEach(this::loadMenu);
        } catch (Exception e) {
            LOGGER.error("Failed to load menus from directory", e);
        }

        // Run cross-menu validation after all individual files are loaded
        validateAll();

        return new LoadReport();
    }

    @Override
    public LoadReport loadMenu(Path file) {
        String fileName = file.getFileName().toString().replace(".yml", "").replace(".yaml", "");
        try {
            MenuDefinition def = parser.parse(file);
            registry.registerMenu(def);
            LOGGER.info("Loaded menu: {}", def.id());
        } catch (YamlMenuParser.MenuValidationException e) {
            LOGGER.error("Failed to validate menu file {}: {}", file.getFileName(), e.getMessage());
            registry.registerInvalidMenu(fileName, e.getErrors());
        } catch (Exception e) {
            LOGGER.error("Failed to parse menu file {}: {}", file.getFileName(), e.getMessage());
            registry.registerInvalidMenu(fileName, List.of(e.getMessage() != null ? e.getMessage() : "Unknown parse error"));
        }
        return new LoadReport();
    }

    @Override
    public SaveReport saveMenu(String menuId) { return new SaveReport(); }

    @Override
    public SaveReport saveAllDirtyMenus() { return new SaveReport(); }

    @Override
    public ValidationReport validateAll() {
        List<String> validMenuIds = new ArrayList<>(registry.getMenus().stream().map(MenuDefinition::id).toList());
        
        for (String menuId : validMenuIds) {
            Optional<MenuDefinition> menuOpt = registry.getMenu(menuId);
            if (menuOpt.isEmpty()) continue;
            MenuDefinition menu = menuOpt.get();
            
            List<String> errors = new ArrayList<>();
            for (MenuPageDefinition page : menu.pages().values()) {
                for (MenuItemDefinition item : page.items().values()) {
                    validateActionsCrossReferences(menu, page, item, item.actions(), errors);
                    validateActionsCrossReferences(menu, page, item, item.denyActions(), errors);
                }
            }
            
            if (!errors.isEmpty()) {
                LOGGER.error("Cross-validation failed for menu '{}': {}", menuId, errors);
                registry.registerInvalidMenu(menuId, errors);
            }
        }
        return new ValidationReport();
    }

    private void validateActionsCrossReferences(MenuDefinition menu, MenuPageDefinition page, MenuItemDefinition item, List<ActionSpec> actions, List<String> errors) {
        for (ActionSpec action : actions) {
            if (action.type().equals("open_menu")) {
                Object menuIdObj = action.params().get("menu-id");
                if (menuIdObj == null) {
                    menuIdObj = action.params().get("menu");
                }
                
                if (menuIdObj != null) {
                    String targetMenuId = String.valueOf(menuIdObj);
                    if (!targetMenuId.contains("{")) {
                        // Check if target menu exists
                        if (registry.getMenu(targetMenuId).isEmpty() && !registry.getInvalidMenus().containsKey(targetMenuId)) {
                            errors.add("Item '" + item.id() + "' in page '" + page.id() + "' references non-existent menu '" + targetMenuId + "' in open_menu action");
                        }
                    }
                }
            } else if (action.type().equals("go_to_page")) {
                Object pageIdObj = action.params().get("page");
                if (pageIdObj == null) {
                    pageIdObj = action.params().get("page_id");
                }
                
                if (pageIdObj != null) {
                    String targetPage = String.valueOf(pageIdObj);
                    if (!targetPage.contains("{")) {
                        if (!menu.pages().containsKey(targetPage)) {
                            errors.add("Item '" + item.id() + "' in page '" + page.id() + "' references non-existent page '" + targetPage + "' in go_to_page action");
                        }
                    }
                }
            }
            
            // Validate nested actions
            validateActionsCrossReferences(menu, page, item, action.onSuccess(), errors);
            validateActionsCrossReferences(menu, page, item, action.onFailure(), errors);
            validateActionsCrossReferences(menu, page, item, action.onDeny(), errors);
        }
    }

    @Override
    public ValidationReport validateMenu(String menuId) { return new ValidationReport(); }

    @Override
    public ReloadReport reloadAll() {
        registry.clear();
        loadAllMenus();
        return new ReloadReport();
    }

    @Override
    public ReloadReport reloadMenu(String menuId) { return new ReloadReport(); }
}
