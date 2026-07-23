package com.pedrodalben.bigbangessentials.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import com.pedrodalben.bigbangessentials.shop.model.ShopData;
import net.minecraft.world.item.ItemStack;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small durable saga journal for ChestShop; ambiguous entries stay blocked for admin reconciliation. */
final class ChestShopTransactionJournal {
    enum Status { PENDING, COMPLETED, RECOVERY_REQUIRED }
    record Entry(String operation, String shop, UUID participant, BigDecimalValue price, int quantity,
                 ItemStackSnapshot item, Status status) {}
    record BigDecimalValue(String value) {}
    record ItemStackSnapshot(String value) {}

    private static final ChestShopTransactionJournal INSTANCE = new ChestShopTransactionJournal();
    private static final Gson GSON = new GsonBuilder().create();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Path path = ResourceUtil.getDataPath("chestshop_transactions.json");
    private boolean loaded;

    static ChestShopTransactionJournal getInstance() { return INSTANCE; }

    synchronized boolean hasPending(String shop, UUID participant) {
        load();
        return entries.values().stream().anyMatch(entry -> entry.status() == Status.PENDING
                && entry.shop().equals(shop) && entry.participant().equals(participant));
    }

    synchronized boolean begin(String id, String operation, ShopData shop, UUID participant, java.math.BigDecimal price, ItemStack item) {
        load();
        entries.putIfAbsent(id, new Entry(operation, shop.toKey(), participant,
                new BigDecimalValue(price.toPlainString()), item.getCount(),
                new ItemStackSnapshot(com.pedrodalben.bigbangessentials.crates.domain.ItemSerializer.serialize(item).toString()), Status.PENDING));
        return save();
    }

    synchronized boolean complete(String id) { return update(id, Status.COMPLETED); }
    synchronized boolean recoveryRequired(String id) { return update(id, Status.RECOVERY_REQUIRED); }

    private boolean update(String id, Status status) {
        load();
        Entry entry = entries.get(id);
        if (entry != null) entries.put(id, new Entry(entry.operation(), entry.shop(), entry.participant(), entry.price(), entry.quantity(), entry.item(), status));
        return save();
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(path)) return;
            Map<String, Entry> stored = GSON.fromJson(Files.readString(path),
                    new com.google.gson.reflect.TypeToken<Map<String, Entry>>(){}.getType());
            if (stored != null) entries.putAll(stored);
        } catch (Exception ignored) { }
    }

    private boolean save() {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) { GSON.toJson(entries, writer); }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception ignored) { }
        return false;
    }
}
