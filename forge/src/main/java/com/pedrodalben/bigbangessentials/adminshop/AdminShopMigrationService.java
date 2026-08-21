package com.pedrodalben.bigbangessentials.adminshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogLoader;
import com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class AdminShopMigrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminShopMigrationService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Yaml YAML;

    static {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        YAML = new Yaml(opts);
    }

    private AdminShopMigrationService() {}

    public static void migrate(Path jsonPath, Path yamlPath) {
        try {
            AdminShopConfig legacy = GSON.fromJson(Files.readString(jsonPath), AdminShopConfig.class);
            if (legacy == null) {
                LOGGER.warn("Empty adminshop.json, generating default catalog");
                Files.deleteIfExists(jsonPath);
                AdminShopCatalogLoader.load();
                return;
            }

            AdminShopCatalogV2 catalog = new AdminShopCatalogV2();
            catalog.version = 2;

            if (legacy.messages != null) {
                catalog.messages.putAll(legacy.messages);
            }

            Map<String, String> productCategories = new LinkedHashMap<>();

            int storeIdx = 0;
            for (var entry : legacy.stores.entrySet()) {
                String storeKey = entry.getKey();
                AdminShopConfig.Store legacyStore = entry.getValue();
                if (legacyStore == null || legacyStore.products == null) continue;

                String currency = legacyStore.currency != null ? legacyStore.currency
                        : storeKey.equalsIgnoreCase("gems") ? "gems" : "money";

                AdminShopCatalogV2.StoreDef storeDef = new AdminShopCatalogV2.StoreDef();
                storeDef.currency = currency;
                storeDef.title = legacyStore.title != null ? legacyStore.title : (currency.equals("gems") ? "§dCash Shop" : "§2Admin Shop");

                String defaultCategoryId = storeKey + "_default";
                storeDef.categories.add(defaultCategoryId);

                AdminShopCatalogV2.CategoryDef defaultCat = new AdminShopCatalogV2.CategoryDef();
                defaultCat.title = "§eGeral";
                defaultCat.icon = "minecraft:chest";
                defaultCat.order = storeIdx * 100;
                catalog.categories.put(defaultCategoryId, defaultCat);

                for (AdminShopConfig.Product legacyProduct : legacyStore.products) {
                    if (legacyProduct.id == null || legacyProduct.id.isBlank()) continue;

                    AdminShopCatalogV2.ProductDef p = new AdminShopCatalogV2.ProductDef();
                    p.store = storeKey;
                    p.category = defaultCategoryId;
                    p.displayName = legacyProduct.displayName != null ? legacyProduct.displayName : legacyProduct.id;
                    p.itemId = legacyProduct.itemId;
                    p.stock = legacyProduct.stock;
                    p.limit = legacyProduct.limit;
                    p.permission = legacyProduct.permission;
                    p.command = legacyProduct.command;
                    p.buyEnabled = legacyProduct.buyEnabled;
                    p.sellEnabled = legacyProduct.sellEnabled;

                    if (legacyProduct.item != null) {
                        p.item = GSON.fromJson(legacyProduct.item.toString(),
                                new TypeToken<Map<String, Object>>(){}.getType());
                    }

                    p.quantity = new AdminShopCatalogV2.Quantity();
                    p.quantity.defaultQuantity = legacyProduct.quantity;
                    p.quantity.max = legacyProduct.quantity;

                    p.price = new AdminShopCatalogV2.Price();
                    p.price.buy = legacyProduct.buyPrice;
                    p.price.sell = legacyProduct.sellPrice;

                    if (legacyProduct.dynamic != null && legacyProduct.dynamic.enabled) {
                        p.price.dynamic = new AdminShopCatalogV2.DynamicPriceDef();
                        p.price.dynamic.enabled = true;
                        p.price.dynamic.step = legacyProduct.dynamic.step;
                        p.price.dynamic.minMultiplier = legacyProduct.dynamic.minMultiplier;
                        p.price.dynamic.maxMultiplier = legacyProduct.dynamic.maxMultiplier;
                    }

                    p.order = legacyProduct.page * 100 + (legacyProduct.slot < 0 ? 99 : legacyProduct.slot);

                    catalog.products.put(legacyProduct.id, p);
                    productCategories.put(legacyProduct.id, defaultCategoryId);
                }
                catalog.stores.put(storeKey, storeDef);
                storeIdx++;
            }

            catalog.index();

            Files.createDirectories(yamlPath.getParent());
            String dumped = YAML.dumpAs(catalog, null, DumperOptions.FlowStyle.BLOCK);
            Files.writeString(yamlPath, dumped);

            Path backup = jsonPath.resolveSibling("adminshop.json.v1.bak");
            Files.move(jsonPath, backup, StandardCopyOption.REPLACE_EXISTING);

            LOGGER.info("Migration complete: {} products, {} stores migrated to adminshop.yml. Backup at {}",
                    catalog.products.size(), catalog.stores.size(), backup.getFileName());
        } catch (Exception e) {
            LOGGER.error("Migration failed", e);
            throw new IllegalStateException("Failed to migrate adminshop.json to adminshop.yml", e);
        }
    }
}
