package com.pedrodalben.bigbangessentials.adminshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.nio.file.StandardOpenOption;

public final class AdminShopManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminShopManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AdminShopManager INSTANCE = new AdminShopManager();
    private AdminShopConfig config = new AdminShopConfig();
    final State state = new State();
    final AdminShopSqlStore sql = new AdminShopSqlStore();
    private AdminShopManager() {}
    public static AdminShopManager getInstance() { return INSTANCE; }
    public synchronized void initialize() { reload(); }
    public synchronized void reload() { config = AdminShopConfig.load(); loadState(); if (!sql.load(state)) sql.save(state); }
    public AdminShopConfig config() { return config; }
    public List<String> reconcile() { return sql.reconcile(); }
    public Path statePath() { return ResourceUtil.getDataPath("adminshop_state.json"); }
    synchronized void saveState() {
        try {
            Files.createDirectories(statePath().getParent());
            Path tmp = statePath().resolveSibling("adminshop_state.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(state, w); }
            sql.save(state);
            Files.move(tmp, statePath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (Exception e) { throw new IllegalStateException("Could not persist admin shop state", e); }
    }

    synchronized void saveStateDelta(String productId, long oldRemaining, long remaining, UUID player,
                                      long oldUsed, long used, long oldDemand, long demand,
                                      boolean hadRemaining, boolean hadLimit, boolean hadDemand,
                                      boolean hasRemaining, boolean hasLimit, boolean hasDemand) {
        try {
            if (!sql.saveDelta(productId, oldRemaining, remaining, player, oldUsed, used, oldDemand, demand,
                    hadRemaining, hadLimit, hadDemand, hasRemaining, hasLimit, hasDemand)) {
                throw new IllegalStateException("Concurrent AdminShop state change");
            }
            Files.createDirectories(statePath().getParent());
            Path tmp = statePath().resolveSibling("adminshop_state.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(state, w); }
            Files.move(tmp, statePath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { sql.saveDelta(productId, remaining, oldRemaining, player, used, oldUsed, demand, oldDemand,
                    hasRemaining, hasLimit, hasDemand, hadRemaining, hadLimit, hadDemand); }
            catch (Exception ignored) { }
            throw new IllegalStateException("Could not persist admin shop state delta", e);
        }
    }

    public synchronized void saveCatalog() {
        try {
            Path yml = AdminShopConfig.path();
            Files.createDirectories(yml.getParent());
            Path tmp = yml.resolveSibling("adminshop.yml.tmp");
            Files.writeString(tmp, toYaml(config), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, yml, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogLoader.syncCategoryFiles();
        } catch (Exception e) {
            LOGGER.error("Failed to save adminshop.yml", e);
            throw new IllegalStateException("Could not persist admin shop catalog", e);
        }
    }

    private static String toYaml(AdminShopConfig cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("version: 2\n\n");
        sb.append("stores:\n");
        for (var se : cfg.stores.entrySet()) {
            AdminShopConfig.Store s = se.getValue();
            sb.append("  ").append(se.getKey()).append(":\n");
            sb.append("    currency: ").append(s.currency).append("\n");
            sb.append("    title: \"").append(escape(s.title)).append("\"\n");
            if (!s.categories.isEmpty()) {
                sb.append("    categories: [");
                for (int i = 0; i < s.categories.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(s.categories.get(i));
                }
                sb.append("]\n");
            }
        }
        sb.append("\ncategories:\n");
        for (var ce : cfg.categories.entrySet()) {
            AdminShopConfig.Category cat = ce.getValue();
            sb.append("  ").append(ce.getKey()).append(":\n");
            sb.append("    title: \"").append(escape(cat.title)).append("\"\n");
            sb.append("    icon: \"").append(cat.icon).append("\"\n");
            sb.append("    order: ").append(cat.order).append("\n");
        }
        sb.append("\nproducts:\n");
        for (var se : cfg.stores.entrySet()) {
            AdminShopConfig.Store s = se.getValue();
            for (AdminShopConfig.Product p : s.products) {
                sb.append("  ").append(p.id).append(":\n");
                sb.append("    store: ").append(se.getKey()).append("\n");
                if (p.category != null) sb.append("    category: ").append(p.category).append("\n");
                if (p.displayName != null) sb.append("    displayName: \"").append(escape(p.displayName)).append("\"\n");
                if (p.item != null) {
                    sb.append("    item:\n");
                    for (var ie : p.item.entrySet()) {
                        sb.append("      ").append(ie.getKey()).append(": ").append(formatYamlValue(ie.getValue())).append("\n");
                    }
                } else if (p.itemId != null) {
                    sb.append("    itemId: \"").append(p.itemId).append("\"\n");
                }
                sb.append("    quantity:\n");
                sb.append("      defaultQuantity: ").append(p.quantity).append("\n");
                if (p.quantityOptions != null && !p.quantityOptions.isEmpty()) {
                    sb.append("      options: [");
                    for (int i = 0; i < p.quantityOptions.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(p.quantityOptions.get(i));
                    }
                    sb.append("]\n");
                }
                sb.append("      max: ").append(p.maxQuantity).append("\n");
                sb.append("    price:\n");
                if (p.buyPrice != null) sb.append("      buy: ").append(p.buyPrice).append("\n");
                if (p.sellPrice != null) sb.append("      sell: ").append(p.sellPrice).append("\n");
                if (p.dynamic != null && p.dynamic.enabled) {
                    sb.append("      dynamic:\n");
                    sb.append("        enabled: true\n");
                    sb.append("        step: ").append(p.dynamic.step).append("\n");
                    sb.append("        minMultiplier: ").append(p.dynamic.minMultiplier).append("\n");
                    sb.append("        maxMultiplier: ").append(p.dynamic.maxMultiplier).append("\n");
                }
                if (p.stock >= 0) sb.append("    stock: ").append(p.stock).append("\n");
                if (p.limit >= 0) sb.append("    limit: ").append(p.limit).append("\n");
                if (p.permission != null && !p.permission.isBlank()) sb.append("    permission: \"").append(escape(p.permission)).append("\"\n");
                if (p.command != null && !p.command.isBlank()) sb.append("    command: \"").append(escape(p.command)).append("\"\n");
                sb.append("    buyEnabled: ").append(p.buyEnabled).append("\n");
                sb.append("    sellEnabled: ").append(p.sellEnabled).append("\n");
                sb.append("    order: ").append(p.order).append("\n");
            }
        }
        sb.append("\nmessages:\n");
        for (var me : cfg.messages.entrySet()) {
            sb.append("  ").append(me.getKey()).append(": \"").append(escape(me.getValue())).append("\"\n");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatYamlValue(com.google.gson.JsonElement el) {
        if (el.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isNumber()) return p.getAsString();
            return "\"" + escape(p.getAsString()) + "\"";
        }
        return el.toString();
    }

    private synchronized void loadState() {
        state.remaining.clear(); state.limits.clear(); state.demand.clear(); state.processed.clear();
        try { if (!Files.exists(statePath())) return; try (Reader r = Files.newBufferedReader(statePath())) { State loaded = GSON.fromJson(r, State.class); if (loaded != null) { if (loaded.remaining != null) state.remaining.putAll(loaded.remaining); if (loaded.limits != null) state.limits.putAll(loaded.limits); if (loaded.demand != null) state.demand.putAll(loaded.demand); if (loaded.processed != null) state.processed.addAll(loaded.processed); } } }
        catch (Exception e) { LOGGER.error("Failed to load admin shop state; starting with empty runtime state", e); }
    }
    static final class State { Map<String, Long> remaining = new HashMap<>(); Map<String, Long> limits = new HashMap<>(); Map<String, Long> demand = new HashMap<>(); Set<String> processed = new HashSet<>(); }
}
