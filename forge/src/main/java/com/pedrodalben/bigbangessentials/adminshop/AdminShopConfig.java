package com.pedrodalben.bigbangessentials.adminshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogLoader;
import com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogV2;
import com.pedrodalben.bigbangessentials.crates.domain.ItemSerializer;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminShopConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminShopConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int version;
    public Map<String, Store> stores = new LinkedHashMap<>();
    public Map<String, String> messages = new LinkedHashMap<>();
    public Map<String, Category> categories = new LinkedHashMap<>();

    public static final class Store {
        public String currency;
        public String title;
        public List<String> categories = new ArrayList<>();
        public List<Product> products = new ArrayList<>();
    }

    public static final class Category {
        public String title;
        public String icon;
        public int order;
    }

    public static final class Product {
        public String id;
        public String store;
        public String displayName;
        public String itemId;
        public JsonObject item;
        public String category;
        public int quantity = 1;
        public List<Integer> quantityOptions;
        public int maxQuantity = 64;
        public BigDecimal buyPrice;
        public BigDecimal sellPrice;
        public boolean buyEnabled = true;
        public boolean sellEnabled = true;
        public long stock = -1;
        public long limit = -1;
        public String permission;
        public String command;
        public int page = 1;
        public int slot = -1;
        public int order = 100;
        public DynamicPrice dynamic;

        public boolean isCommand() { return command != null && !command.isBlank(); }

        public ItemStack stack(int count) {
            if (item != null) {
                ItemStack result = ItemSerializer.deserialize(item);
                result.setCount(count);
                return result;
            }
            JsonObject json = new JsonObject();
            json.addProperty("item", itemId == null ? "minecraft:stone" : itemId);
            json.addProperty("count", count);
            return ItemSerializer.deserialize(json);
        }

        public String effectiveItemId() {
            if (item != null && item.has("item")) {
                String raw = item.get("item").getAsString();
                if (raw != null && !raw.isBlank()) return raw;
            }
            return itemId != null && !itemId.isBlank() ? itemId : "minecraft:stone";
        }

        public List<Integer> resolvedQuantityOptions() {
            if (quantityOptions != null && !quantityOptions.isEmpty()) return quantityOptions;
            int def = Math.max(1, quantity);
            ItemStack stack = stack(def);
            if (stack.isEmpty()) return List.of(1);
            int maxStack = stack.getMaxStackSize();
            if (maxStack == 1) return List.of(1);
            if (maxStack == 16) return List.of(1, 8, 16);
            if (def < 32 && maxStack >= 64) return List.of(def, 32, 64);
            return List.of(def, Math.min(def * 16, maxStack), maxStack);
        }
    }

    public static final class DynamicPrice {
        public boolean enabled;
        public BigDecimal step = new BigDecimal("0.05");
        public BigDecimal minMultiplier = new BigDecimal("0.50");
        public BigDecimal maxMultiplier = new BigDecimal("2.00");
    }

    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Map<String, String> productCurrencies = new ConcurrentHashMap<>();
    private final Map<String, Category> categoriesById = new ConcurrentHashMap<>();
    private final Map<String, List<String>> storeCategories = new ConcurrentHashMap<>();
    private final Map<String, List<Product>> productsByCategory = new ConcurrentHashMap<>();

    public static Path path() { return ResourceUtil.getConfigPath("adminshop.yml"); }

    public static AdminShopConfig load() {
        try {
            AdminShopCatalogV2 catalog = AdminShopCatalogLoader.load();
            return fromCatalog(catalog);
        } catch (Exception e) {
            LOGGER.error("Failed to load adminshop config", e);
            return new AdminShopConfig().index();
        }
    }

    @SuppressWarnings("unchecked")
    static AdminShopConfig fromCatalog(AdminShopCatalogV2 catalog) {
        AdminShopConfig config = new AdminShopConfig();
        config.version = catalog.version;
        if (catalog.messages != null) config.messages.putAll(catalog.messages);

        if (catalog.categories != null) {
            for (var e : catalog.categories.entrySet()) {
                AdminShopCatalogV2.CategoryDef def = e.getValue();
                Category cat = new Category();
                cat.title = def.title;
                cat.icon = def.icon;
                cat.order = def.order;
                config.categories.put(e.getKey(), cat);
            }
        }

        if (catalog.stores != null) {
            for (var e : catalog.stores.entrySet()) {
                AdminShopCatalogV2.StoreDef def = e.getValue();
                Store store = new Store();
                store.currency = def.currency != null ? def.currency : (e.getKey().equalsIgnoreCase("gems") ? "gems" : "money");
                store.title = def.title;
                store.categories = def.categories != null ? new ArrayList<>(def.categories) : new ArrayList<>();
                store.products = new ArrayList<>();
                config.stores.put(e.getKey(), store);
            }
        }

        if (catalog.products != null) {
            for (var e : catalog.products.entrySet()) {
                AdminShopCatalogV2.ProductDef def = e.getValue();
                Product p = new Product();
                p.id = e.getKey();
                p.store = def.store;
                p.displayName = def.displayName;
                p.itemId = def.itemId;
                p.category = def.category;
                p.quantity = def.quantity.defaultQuantity;
                p.quantityOptions = def.quantity.options != null ? new ArrayList<>(def.quantity.options) : null;
                p.maxQuantity = def.quantity.max;
                p.buyPrice = def.price.buy;
                p.sellPrice = def.price.sell;
                p.buyEnabled = def.buyEnabled;
                p.sellEnabled = def.sellEnabled;
                p.stock = def.stock;
                p.limit = def.limit;
                p.permission = def.permission;
                p.command = def.command;
                p.order = def.order;

                if (def.item != null && !def.item.isEmpty()) {
                    p.item = GSON.toJsonTree(def.item).getAsJsonObject();
                }

                if (def.price.dynamic != null && def.price.dynamic.enabled) {
                    p.dynamic = new DynamicPrice();
                    p.dynamic.enabled = true;
                    p.dynamic.step = def.price.dynamic.step;
                    p.dynamic.minMultiplier = def.price.dynamic.minMultiplier;
                    p.dynamic.maxMultiplier = def.price.dynamic.maxMultiplier;
                }

                Store store = config.stores.get(def.store);
                if (store != null) {
                    store.products.add(p);
                }
            }
        }

        return config.index();
    }

    AdminShopConfig index() {
        products.clear();
        productCurrencies.clear();
        categoriesById.clear();
        storeCategories.clear();
        productsByCategory.clear();

        if (stores == null) stores = new LinkedHashMap<>();
        if (categories == null) categories = new LinkedHashMap<>();

        for (var e : categories.entrySet()) {
            categoriesById.put(e.getKey(), e.getValue());
        }

        for (var entry : stores.entrySet()) {
            Store store = entry.getValue();
            if (store == null) continue;
            String storeKey = entry.getKey();
            String currency = store.currency != null ? store.currency
                    : storeKey.equalsIgnoreCase("gems") ? "gems" : "money";
            store.currency = currency;

            storeCategories.put(storeKey, store.categories != null ? new ArrayList<>(store.categories) : new ArrayList<>());

            if (store.products == null) {
                store.products = new ArrayList<>();
                continue;
            }

            for (Product product : store.products) {
                if (product == null || product.id == null || product.id.isBlank()
                        || products.putIfAbsent(product.id, product) != null) {
                    LOGGER.warn("Ignoring invalid/duplicate admin shop product in store {}", storeKey);
                    continue;
                }
                productCurrencies.put(product.id, currency);
                if (product.category == null) {
                    for (String catId : store.categories) {
                        product.category = catId;
                        break;
                    }
                }
                productsByCategory.computeIfAbsent(product.category, k -> new ArrayList<>()).add(product);

                if (product.quantity < 1 || product.buyPrice != null && product.buyPrice.signum() < 0
                        || product.sellPrice != null && product.sellPrice.signum() < 0) {
                    products.remove(product.id);
                    productCurrencies.remove(product.id);
                    if (product.category != null) {
                        productsByCategory.getOrDefault(product.category, List.of()).remove(product);
                    }
                    LOGGER.warn("Ignoring invalid admin shop product {}", product.id);
                }
            }
        }

        return this;
    }

    public Product product(String id) { return products.get(id); }

    public String findStoreId(String requestedId) {
        if (requestedId == null || requestedId.isBlank()) return null;
        for (String storeId : stores.keySet()) {
            if (requestedId.equalsIgnoreCase(storeId)) return storeId;
        }
        for (var entry : stores.entrySet()) {
            Store store = entry.getValue();
            if (store != null && requestedId.equalsIgnoreCase(store.currency)) return entry.getKey();
        }
        return null;
    }

    public String currency(String id) { return productCurrencies.get(id); }

    public String currencyForStore(String storeId) {
        String resolvedStoreId = findStoreId(storeId);
        Store s = resolvedStoreId == null ? null : stores.get(resolvedStoreId);
        return s != null ? s.currency : null;
    }

    public Collection<Product> products(String currency) {
        return products.entrySet().stream()
                .filter(e -> currency.equals(productCurrencies.get(e.getKey())))
                .map(Map.Entry::getValue).toList();
    }

    public Collection<Product> productsByCategory(String categoryId) {
        return productsByCategory.getOrDefault(categoryId, List.of());
    }

    public Collection<Category> categoriesByStore(String storeId) {
        String resolvedStoreId = findStoreId(storeId);
        List<String> catIds = resolvedStoreId == null ? List.of()
                : storeCategories.getOrDefault(resolvedStoreId, List.of());
        List<Category> result = new ArrayList<>();
        for (String id : catIds) {
            Category c = categoriesById.get(id);
            if (c != null) result.add(c);
        }
        result.sort(Comparator.comparingInt(c -> c.order));
        return result;
    }

    public Category category(String id) { return categoriesById.get(id); }

    public String storeIdForCategory(String categoryId) {
        for (var e : storeCategories.entrySet()) {
            if (e.getValue().contains(categoryId)) return e.getKey();
        }
        return null;
    }

    public String message(String key) {
        return messages.getOrDefault(key, "§cErro desconhecido.");
    }

    public String message(String key, Map<String, String> replacements) {
        String msg = messages.getOrDefault(key, "§cErro desconhecido.");
        for (var e : replacements.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return msg;
    }

    public JsonObject toJson() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }
}
