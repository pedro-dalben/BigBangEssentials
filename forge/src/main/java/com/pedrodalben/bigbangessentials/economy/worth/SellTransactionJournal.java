package com.pedrodalben.bigbangessentials.economy.worth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.pedrodalben.bigbangessentials.crates.domain.ItemSerializer;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Durable escrow marker for /sell; pending entries are for administrative recovery after a crash. */
final class SellTransactionJournal {
    private record Entry(String player, String amount, JsonArray items, String status) {}
    private static final SellTransactionJournal INSTANCE = new SellTransactionJournal();
    private static final Gson GSON = new GsonBuilder().create();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Path path = ResourceUtil.getDataPath("sell_transactions.json");
    private boolean loaded;

    static SellTransactionJournal getInstance() { return INSTANCE; }

    synchronized boolean hasPending(UUID player) {
        load();
        return entries.values().stream().anyMatch(entry -> entry.player().equals(player.toString()) && "ESCROW".equals(entry.status()));
    }

    synchronized boolean begin(String id, UUID player, java.math.BigDecimal amount, java.util.List<ItemStack> items) {
        load();
        JsonArray serialized = ItemSerializer.serializeList(items);
        entries.putIfAbsent(id, new Entry(player.toString(), amount.toPlainString(), serialized, "ESCROW"));
        return save();
    }

    synchronized boolean complete(String id) { return mark(id, "COMPLETED"); }
    synchronized boolean recoveryRequired(String id) { return mark(id, "RECOVERY_REQUIRED"); }
    synchronized boolean failed(String id) { return mark(id, "FAILED"); }

    private boolean mark(String id, String status) {
        load();
        Entry old = entries.get(id);
        if (old == null) return false;
        entries.put(id, new Entry(old.player(), old.amount(), old.items(), status));
        return save();
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(path)) {
                Map<String, Entry> stored = GSON.fromJson(Files.readString(path), new com.google.gson.reflect.TypeToken<Map<String, Entry>>(){}.getType());
                if (stored != null) entries.putAll(stored);
            }
        } catch (Exception ignored) { }
    }

    private boolean save() {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(entries));
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception ignored) { return false; }
    }
}
