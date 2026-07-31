package com.pedrodalben.bigbangessentials.adminshop.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.adminshop.AdminShopMigrationService;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;

public final class AdminShopCatalogLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminShopCatalogLoader.class);
    private static final Yaml YAML = new Yaml();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path YAML_PATH = ResourceUtil.getConfigPath("adminshop.yml");
    private static final Path JSON_PATH = ResourceUtil.getConfigPath("adminshop.json");
    private static final Path SHOPS_PATH = ResourceUtil.getConfigPath("shops");
    private static final Path MODULAR_MARKER = SHOPS_PATH.resolve(".modular");

    private AdminShopCatalogLoader() {}

    public static AdminShopCatalogV2 load() {
        Path yml = YAML_PATH;
        Path json = JSON_PATH;

        try {
            AdminShopCatalogV2 catalog;
            if (Files.exists(yml)) {
                catalog = loadYaml(yml);
            } else if (Files.exists(json)) {
                LOGGER.info("adminshop.json v1 detected; auto-migrating to adminshop.yml v2");
                AdminShopMigrationService.migrate(json, yml);
                catalog = loadYaml(yml);
            } else {
                catalog = defaults(yml);
            }
            ensureCategoryFiles(catalog);
            return catalog.index();
        } catch (Exception e) {
            LOGGER.error("Failed to load admin shop catalog", e);
            try {
                LOGGER.warn("Using bundled admin shop catalog until the configured catalog is repaired");
                return loadBundledDefault().index();
            } catch (Exception fallbackError) {
                LOGGER.error("Failed to load bundled admin shop catalog", fallbackError);
                return new AdminShopCatalogV2().index();
            }
        }
    }

    private static AdminShopCatalogV2 loadBundledDefault() throws Exception {
        try (java.io.InputStream resource = AdminShopCatalogLoader.class.getResourceAsStream(
                "/default-config/bigbangessentials/adminshop.yml")) {
            if (resource == null) throw new IllegalStateException("Bundled adminshop.yml is missing");
            return YAML.loadAs(resource, AdminShopCatalogV2.class);
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
            loadCategoryFiles(catalog);
            return catalog;
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

    /** Rebuilds the editable one-file-per-category catalog after an admin edit. */
    public static void syncCategoryFiles() throws Exception {
        if (!Files.exists(YAML_PATH)) return;
        AdminShopCatalogV2 catalog = YAML.loadAs(Files.readString(YAML_PATH), AdminShopCatalogV2.class);
        if (catalog == null) catalog = new AdminShopCatalogV2();
        writeCategoryFiles(catalog);
    }

    private static void ensureCategoryFiles(AdminShopCatalogV2 catalog) throws Exception {
        if (Files.exists(MODULAR_MARKER)) return;
        writeCategoryFiles(catalog);
        Files.writeString(MODULAR_MARKER, "Generated by BigBangEssentials. Edit shops/<store>/<category>.yml.\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void loadCategoryFiles(AdminShopCatalogV2 catalog) throws Exception {
        List<Path> files = categoryFiles();
        if (files.isEmpty()) return;

        Set<String> modularStores = new HashSet<>();
        for (Path file : files) modularStores.add(file.getParent().getFileName().toString());
        if (Files.exists(MODULAR_MARKER)) {
            for (String storeId : modularStores) {
                AdminShopCatalogV2.StoreDef store = catalog.stores.get(storeId);
                if (store != null) store.categories.clear();
                catalog.products.entrySet().removeIf(e -> e.getValue() != null
                        && storeId.equals(e.getValue().store));
            }
        }

        for (Path file : files) {
            String storeId = file.getParent().getFileName().toString();
            String categoryId = file.getFileName().toString()
                    .replaceFirst("\\.(yml|yaml)$", "");
            Map<?, ?> root = YAML.load(Files.readString(file));
            if (root == null) continue;

            AdminShopCatalogV2.StoreDef store = catalog.stores.computeIfAbsent(storeId, ignored -> {
                AdminShopCatalogV2.StoreDef value = new AdminShopCatalogV2.StoreDef();
                value.currency = storeId.equalsIgnoreCase("gems") ? "gems" : "money";
                value.title = storeId.equalsIgnoreCase("gems") ? "§dCash Shop" : "§2Admin Shop";
                return value;
            });
            if (store.categories == null) store.categories = new ArrayList<>();
            if (!store.categories.contains(categoryId)) store.categories.add(categoryId);

            AdminShopCatalogV2.CategoryDef category = catalog.categories.computeIfAbsent(categoryId,
                    ignored -> new AdminShopCatalogV2.CategoryDef());
            if (root.get("title") instanceof String title) category.title = title;
            if (root.get("icon") instanceof String icon) category.icon = icon;
            if (root.get("order") instanceof Number order) category.order = order.intValue();

            Object rawProducts = root.get("products");
            if (!(rawProducts instanceof Map<?, ?> products)) continue;
            for (var entry : products.entrySet()) {
                if (!(entry.getKey() instanceof String productId)
                        || !(entry.getValue() instanceof Map<?, ?> rawProduct)) continue;
                JsonObject merged = catalog.products.containsKey(productId)
                        ? GSON.toJsonTree(catalog.products.get(productId)).getAsJsonObject()
                        : new JsonObject();
                merge(merged, GSON.toJsonTree(rawProduct));
                AdminShopCatalogV2.ProductDef product = GSON.fromJson(merged,
                        AdminShopCatalogV2.ProductDef.class);
                if (product.store == null) product.store = storeId;
                if (product.category == null) product.category = categoryId;
                catalog.products.put(productId, product);
            }
        }
    }

    private static List<Path> categoryFiles() throws Exception {
        if (!Files.isDirectory(SHOPS_PATH)) return List.of();
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stores = Files.list(SHOPS_PATH)) {
            for (Path store : stores.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(store)) {
                    result.addAll(files.filter(path -> path.getFileName().toString().matches(".+\\.(yml|yaml)"))
                            .sorted().toList());
                }
            }
        }
        return result;
    }

    private static void writeCategoryFiles(AdminShopCatalogV2 catalog) throws Exception {
        Files.createDirectories(SHOPS_PATH);
        for (Path file : categoryFiles()) Files.deleteIfExists(file);

        for (var storeEntry : catalog.stores.entrySet()) {
            String storeId = storeEntry.getKey();
            AdminShopCatalogV2.StoreDef store = storeEntry.getValue();
            if (store == null || store.categories == null) continue;
            Path storePath = SHOPS_PATH.resolve(storeId);
            Files.createDirectories(storePath);
            for (String categoryId : store.categories) {
                AdminShopCatalogV2.CategoryDef category = catalog.categories.get(categoryId);
                if (category == null) continue;
                Map<String, Object> root = new LinkedHashMap<>();
                root.put("store", storeId);
                root.put("category", categoryId);
                root.put("title", category.title);
                root.put("icon", category.icon);
                root.put("order", category.order);
                Map<String, Object> products = new LinkedHashMap<>();
                for (var productEntry : catalog.products.entrySet()) {
                    AdminShopCatalogV2.ProductDef product = productEntry.getValue();
                    if (product != null && storeId.equals(product.store) && categoryId.equals(product.category)) {
                        products.put(productEntry.getKey(), GSON.fromJson(GSON.toJson(product), Map.class));
                    }
                }
                root.put("products", products);
                Files.writeString(storePath.resolve(categoryId + ".yml"), YAML.dump(root),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
    }

    private static void merge(JsonObject target, JsonElement source) {
        if (!source.isJsonObject()) return;
        for (var entry : source.getAsJsonObject().entrySet()) {
            JsonElement current = target.get(entry.getKey());
            if (current != null && current.isJsonObject() && entry.getValue().isJsonObject()) {
                merge(current.getAsJsonObject(), entry.getValue());
            } else {
                target.add(entry.getKey(), entry.getValue());
            }
        }
    }
}
