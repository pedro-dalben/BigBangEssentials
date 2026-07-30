package com.pedrodalben.bigbangessentials.adminshop.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.crates.domain.ItemSerializer;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.*;

public final class AdminShopCatalogV2 {
    private static final Gson GSON = new Gson();
    public int version = 2;
    public Map<String, StoreDef> stores = new LinkedHashMap<>();
    public Map<String, CategoryDef> categories = new LinkedHashMap<>();
    public Map<String, ProductDef> products = new LinkedHashMap<>();
    public Map<String, String> messages = new LinkedHashMap<>();

    private final Map<String, ProductDef> productsById = new LinkedHashMap<>();
    private final Map<String, CategoryDef> categoriesById = new LinkedHashMap<>();
    private final Map<String, List<ProductDef>> productsByCategory = new LinkedHashMap<>();
    private final Map<String, List<CategoryDef>> categoriesByStore = new LinkedHashMap<>();

    public static final class StoreDef {
        public String currency;
        public String title;
        public List<String> categories = new ArrayList<>();
    }

    public static final class CategoryDef {
        public String title;
        public String icon = "minecraft:chest";
        public int order;
    }

    public static final class ProductDef {
        public String store;
        public String category;
        public String displayName;
        public String itemId;
        public Map<String, Object> item;
        public Quantity quantity = new Quantity();
        public Price price = new Price();
        public long stock = -1;
        public long limit = -1;
        public String permission;
        public String command;
        public boolean buyEnabled = true;
        public boolean sellEnabled = true;
        public int order = 100;

        public boolean isCommand() { return command != null && !command.isBlank(); }

        public ItemStack stack(int count) {
            JsonObject json;
            if (item != null && !item.isEmpty()) {
                json = GSON.toJsonTree(item).getAsJsonObject();
            } else {
                json = new JsonObject();
                json.addProperty("item", itemId == null ? "minecraft:stone" : itemId);
            }
            json.addProperty("count", count);
            return ItemSerializer.deserialize(json);
        }

        public String effectiveItemId() {
            if (item != null && item.containsKey("item")) {
                Object raw = item.get("item");
                if (raw instanceof String s && !s.isBlank()) return s;
            }
            if (itemId != null && !itemId.isBlank()) return itemId;
            return "minecraft:stone";
        }

        public BigDecimal buyPrice() { return price.buy; }
        public BigDecimal sellPrice() { return price.sell; }

        public boolean hasDynamic() {
            return price.dynamic != null && price.dynamic.enabled;
        }
    }

    public static final class Quantity {
        public int defaultQuantity = 1;
        public List<Integer> options = new ArrayList<>(List.of(1, 16, 64));
        public int max = 64;
    }

    public static final class Price {
        public BigDecimal buy;
        public BigDecimal sell;
        public DynamicPriceDef dynamic;
    }

    public static final class DynamicPriceDef {
        public boolean enabled;
        public BigDecimal step = new BigDecimal("0.05");
        public BigDecimal minMultiplier = new BigDecimal("0.50");
        public BigDecimal maxMultiplier = new BigDecimal("2.00");
    }

    public AdminShopCatalogV2 index() {
        productsById.clear();
        categoriesById.clear();
        productsByCategory.clear();
        categoriesByStore.clear();

        for (var e : products.entrySet()) {
            ProductDef p = e.getValue();
            String id = e.getKey();
            productsById.put(id, p);
            if (p.category != null) {
                productsByCategory.computeIfAbsent(p.category, k -> new ArrayList<>()).add(p);
            }
        }

        for (var e : categories.entrySet()) {
            categoriesById.put(e.getKey(), e.getValue());
        }

        for (var e : stores.entrySet()) {
            StoreDef s = e.getValue();
            List<CategoryDef> cats = new ArrayList<>();
            for (String catId : s.categories) {
                CategoryDef cat = categoriesById.get(catId);
                if (cat != null) cats.add(cat);
            }
            cats.sort(Comparator.comparingInt(c -> c.order));
            categoriesByStore.put(e.getKey(), cats);
        }

        return this;
    }

    public ProductDef product(String id) { return productsById.get(id); }

    public CategoryDef category(String id) { return categoriesById.get(id); }

    public StoreDef store(String id) { return stores.get(id); }

    public String currency(String productId) {
        ProductDef p = productsById.get(productId);
        if (p == null || p.store == null) return null;
        StoreDef s = stores.get(p.store);
        return s != null ? s.currency : null;
    }

    public Collection<ProductDef> productsByCurrency(String currency) {
        List<ProductDef> result = new ArrayList<>();
        for (var e : stores.entrySet()) {
            if (currency.equals(e.getValue().currency)) {
                for (CategoryDef cat : categoriesByStore.getOrDefault(e.getKey(), List.of())) {
                    result.addAll(productsByCategoryId(cat));
                }
            }
        }
        return result;
    }

    public List<CategoryDef> categoriesByStoreId(String storeId) {
        return categoriesByStore.getOrDefault(storeId, List.of());
    }

    public List<ProductDef> productsByCategoryId(String categoryId) {
        List<ProductDef> list = productsByCategory.getOrDefault(categoryId, List.of());
        list.sort(Comparator.comparingInt(p -> p.order));
        return list;
    }

    List<ProductDef> productsByCategoryId(CategoryDef cat) {
        return productsByCategoryId(categoriesById.entrySet().stream()
                .filter(e -> e.getValue() == cat)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null));
    }

    public String storeIdForCategory(String categoryId) {
        for (var e : stores.entrySet()) {
            if (e.getValue().categories.contains(categoryId)) return e.getKey();
        }
        return null;
    }
}
