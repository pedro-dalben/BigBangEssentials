package com.pedrodalben.bigbangessentials.adminshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

/** External, server-owned catalog. ChestShop never reads this file. */
public final class AdminShopConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminShopConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public Map<String, Store> stores = new LinkedHashMap<>();
    public Map<String, String> messages = new LinkedHashMap<>();

    public static final class Store {
        public String currency;
        public String title;
        public List<Product> products = new ArrayList<>();
    }

    public static final class Product {
        public String id;
        public String displayName;
        public String itemId;
        public com.google.gson.JsonObject item;
        public int quantity = 1;
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
        public DynamicPrice dynamic;

        public boolean isCommand() { return command != null && !command.isBlank(); }
        public ItemStack stack(int count) {
            if (item != null) {
                ItemStack result = ItemSerializer.deserialize(com.google.gson.JsonParser.parseString(item.toString()).getAsJsonObject());
                result.setCount(count);
                return result;
            }
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("item", itemId == null ? "minecraft:stone" : itemId);
            json.addProperty("count", count);
            return ItemSerializer.deserialize(json);
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

    public static Path path() { return ResourceUtil.getConfigPath("adminshop.json"); }

    public static AdminShopConfig load() {
        try {
            Files.createDirectories(path().getParent());
            if (!Files.exists(path())) {
                AdminShopConfig fresh = defaults();
                try (Writer writer = Files.newBufferedWriter(path())) { GSON.toJson(fresh, writer); }
                return fresh.index();
            }
            try (Reader reader = Files.newBufferedReader(path())) {
                AdminShopConfig config = GSON.fromJson(reader, AdminShopConfig.class);
                return (config == null ? defaults() : config).index();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load adminshop.json", e);
            return new AdminShopConfig().index();
        }
    }

    private AdminShopConfig index() {
        products.clear(); productCurrencies.clear();
        if (stores == null) stores = new LinkedHashMap<>();
        for (var entry : stores.entrySet()) {
            Store store = entry.getValue();
            if (store == null || store.products == null) continue;
            String currency = entry.getKey().equalsIgnoreCase("gems") ? "gems" : "money";
            for (Product product : store.products) {
                if (product == null || product.id == null || product.id.isBlank() || products.putIfAbsent(product.id, product) != null) {
                    LOGGER.warn("Ignoring invalid/duplicate admin shop product in store {}", entry.getKey());
                    continue;
                }
                productCurrencies.put(product.id, currency);
                if (product.quantity < 1 || product.buyPrice != null && product.buyPrice.signum() < 0 || product.sellPrice != null && product.sellPrice.signum() < 0) {
                    products.remove(product.id); productCurrencies.remove(product.id);
                    LOGGER.warn("Ignoring invalid admin shop product {}", product.id);
                }
            }
        }
        return this;
    }

    public Product product(String id) { return products.get(id); }
    public String currency(String id) { return productCurrencies.get(id); }
    public Collection<Product> products(String currency) {
        return products.entrySet().stream().filter(e -> currency.equals(productCurrencies.get(e.getKey()))).map(Map.Entry::getValue).toList();
    }

    private static AdminShopConfig defaults() {
        AdminShopConfig c = new AdminShopConfig();
        c.messages.put("no-funds", "§cSaldo insuficiente.");
        c.messages.put("no-item", "§cVocê não possui os itens necessários.");
        c.messages.put("success", "§aTransação concluída: §f{product} §7({price} {currency})");
        Store money = new Store(); money.currency = "money"; money.title = "§2Admin Shop";
        Store gems = new Store(); gems.currency = "gems"; gems.title = "§dCash Shop";
        String[][] vanilla = {{"coal","minecraft:coal","2.00","0.50"},{"iron_ingot","minecraft:iron_ingot","8.00","2.00"},{"gold_ingot","minecraft:gold_ingot","16.00","4.00"},{"diamond","minecraft:diamond","100.00","25.00"},{"emerald","minecraft:emerald","80.00","20.00"},{"redstone","minecraft:redstone","3.00","0.75"},{"lapis_lazuli","minecraft:lapis_lazuli","4.00","1.00"},{"oak_log","minecraft:oak_log","3.00","0.75"},{"cobblestone","minecraft:cobblestone","1.00","0.25"},{"sand","minecraft:sand","1.50","0.35"},{"wheat","minecraft:wheat","2.00","0.50"},{"bread","minecraft:bread","4.00","1.00"},{"coal_block","minecraft:coal_block","18.00","4.50"},{"iron_block","minecraft:iron_block","72.00","18.00"},{"gold_block","minecraft:gold_block","144.00","36.00"},{"diamond_block","minecraft:diamond_block","900.00","225.00"}};
        for (int i = 0; i < vanilla.length; i++) { Product p = new Product(); p.id = vanilla[i][0]; p.displayName = vanilla[i][0]; p.itemId = vanilla[i][1]; p.buyPrice = new BigDecimal(vanilla[i][2]); p.sellPrice = new BigDecimal(vanilla[i][3]); p.slot = i % 45; p.dynamic = new DynamicPrice(); p.dynamic.enabled = true; money.products.add(p); }
        Product beacon = new Product(); beacon.id = "beacon"; beacon.displayName = "Beacon"; beacon.itemId = "minecraft:beacon"; beacon.buyPrice = new BigDecimal("500"); beacon.sellEnabled = false; beacon.slot = 0; gems.products.add(beacon);
        c.stores.put("money", money); c.stores.put("gems", gems); return c;
    }
}
