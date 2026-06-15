package com.pedrodalben.bigbangessentials.menu.persistence.yaml;

import com.pedrodalben.bigbangessentials.menu.api.MenuPersistenceService;
import com.pedrodalben.bigbangessentials.menu.persistence.*;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
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
        return new LoadReport();
    }

    @Override
    public LoadReport loadMenu(Path file) {
        try {
            MenuDefinition def = parser.parse(file);
            registry.registerMenu(def);
            LOGGER.info("Loaded menu: {}", def.id());
        } catch (Exception e) {
            LOGGER.error("Failed to load menu: {}", file, e);
        }
        return new LoadReport();
    }

    @Override
    public SaveReport saveMenu(String menuId) { return new SaveReport(); }

    @Override
    public SaveReport saveAllDirtyMenus() { return new SaveReport(); }

    @Override
    public ValidationReport validateAll() { return new ValidationReport(); }

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
