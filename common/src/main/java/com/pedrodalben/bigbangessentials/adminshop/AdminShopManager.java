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
    public java.util.List<String> reconcile() { return sql.reconcile(); }
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
    private synchronized void loadState() {
        state.remaining.clear(); state.limits.clear(); state.demand.clear(); state.processed.clear();
        try { if (!Files.exists(statePath())) return; try (Reader r = Files.newBufferedReader(statePath())) { State loaded = GSON.fromJson(r, State.class); if (loaded != null) { if (loaded.remaining != null) state.remaining.putAll(loaded.remaining); if (loaded.limits != null) state.limits.putAll(loaded.limits); if (loaded.demand != null) state.demand.putAll(loaded.demand); if (loaded.processed != null) state.processed.addAll(loaded.processed); } } }
        catch (Exception e) { LOGGER.error("Failed to load admin shop state; starting with empty runtime state", e); }
    }
    static final class State { Map<String, Long> remaining = new HashMap<>(); Map<String, Long> limits = new HashMap<>(); Map<String, Long> demand = new HashMap<>(); Set<String> processed = new HashSet<>(); }
}
