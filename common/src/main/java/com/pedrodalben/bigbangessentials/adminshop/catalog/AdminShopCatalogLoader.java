package com.pedrodalben.bigbangessentials.adminshop.catalog;

import com.pedrodalben.bigbangessentials.adminshop.AdminShopMigrationService;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AdminShopCatalogLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminShopCatalogLoader.class);
    private static final Yaml YAML = new Yaml();
    private static final Path YAML_PATH = ResourceUtil.getConfigPath("adminshop.yml");
    private static final Path JSON_PATH = ResourceUtil.getConfigPath("adminshop.json");

    private AdminShopCatalogLoader() {}

    public static AdminShopCatalogV2 load() {
        Path yml = YAML_PATH;
        Path json = JSON_PATH;

        try {
            if (Files.exists(yml)) {
                return loadYaml(yml);
            }
            if (Files.exists(json)) {
                LOGGER.info("adminshop.json v1 detected; auto-migrating to adminshop.yml v2");
                AdminShopMigrationService.migrate(json, yml);
                return loadYaml(yml);
            }
            return defaults(yml);
        } catch (Exception e) {
            LOGGER.error("Failed to load admin shop catalog", e);
            return new AdminShopCatalogV2().index();
        }
    }

    private static AdminShopCatalogV2 loadYaml(Path path) {
        try {
            String content = Files.readString(path);
            AdminShopCatalogV2 catalog = YAML.loadAs(content, AdminShopCatalogV2.class);
            if (catalog == null) catalog = new AdminShopCatalogV2();
            if (catalog.version < 2) {
                LOGGER.warn("Catalog version {} — running migration", catalog.version);
            }
            return catalog.index();
        } catch (Exception e) {
            LOGGER.error("Failed to parse adminshop.yml", e);
            throw new IllegalStateException("Invalid adminshop.yml", e);
        }
    }

    private static AdminShopCatalogV2 defaults(Path yml) throws Exception {
        java.io.InputStream resource = AdminShopCatalogLoader.class.getResourceAsStream(
                "/default-config/bigbangessentials/adminshop.yml");
        if (resource != null) {
            Files.createDirectories(yml.getParent());
            Files.copy(resource, yml, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            resource.close();
            return loadYaml(yml);
        }
        LOGGER.warn("No default adminshop.yml resource found, generating minimal catalog");
        AdminShopCatalogV2 catalog = new AdminShopCatalogV2();
        catalog.messages.put("no-funds", "§cSaldo insuficiente.");
        catalog.messages.put("success", "§aTransação concluída: §f{product} §7({price} {currency})");
        AdminShopCatalogV2.StoreDef store = new AdminShopCatalogV2.StoreDef();
        store.currency = "money";
        store.title = "§2Admin Shop";
        store.categories = java.util.List.of("default");
        catalog.stores.put("money", store);
        AdminShopCatalogV2.CategoryDef cat = new AdminShopCatalogV2.CategoryDef();
        cat.title = "§eGeral";
        cat.icon = "minecraft:chest";
        cat.order = 10;
        catalog.categories.put("default", cat);
        Files.createDirectories(yml.getParent());
        String dumped = YAML.dumpAs(catalog, null, org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        Files.writeString(yml, dumped);
        return catalog.index();
    }
}
