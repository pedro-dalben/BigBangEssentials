package com.pedrodalben.bigbangessentials.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import com.pedrodalben.bigbangessentials.shop.model.ShopData;
import net.minecraft.world.item.ItemStack;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Small durable saga journal for ChestShop; ambiguous entries stay blocked for admin reconciliation. */
public final class ChestShopTransactionJournal {
    enum Status { PENDING, COMPLETED, CANCELLED, ROLLED_BACK, RECOVERY_REQUIRED }
    record Entry(String operation, String shop, UUID participant, BigDecimalValue price, int quantity,
                 ItemStackSnapshot item, Status status) {}
    record BigDecimalValue(String value) {}
    record ItemStackSnapshot(String value) {}

    private static final ChestShopTransactionJournal INSTANCE = new ChestShopTransactionJournal();
    private static final Gson GSON = new GsonBuilder().create();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Path path = ResourceUtil.getDataPath("chestshop_transactions.json");
    private boolean loaded;

    public static ChestShopTransactionJournal getInstance() { return INSTANCE; }

    static boolean blocks(Status status) {
        return status == Status.PENDING || status == Status.RECOVERY_REQUIRED;
    }

    static Status statusOf(String status) {
        return switch (status) {
            case "COMPLETED" -> Status.COMPLETED;
            case "CANCELLED" -> Status.CANCELLED;
            case "ROLLED_BACK" -> Status.ROLLED_BACK;
            case "RECOVERY_REQUIRED" -> Status.RECOVERY_REQUIRED;
            default -> Status.PENDING;
        };
    }

    synchronized boolean hasPending(String shop, UUID participant) {
        load();
        return entries.values().stream().anyMatch(entry -> blocks(entry.status())
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

    /**
     * Durable async journal entry. SQL is authoritative for DATABASE/SQLITE;
     * JSON remains the compatibility store for the legacy non-database backend.
     */
    CompletableFuture<Boolean> beginDurable(String id, String operation, ShopData shop, UUID participant,
                                            java.math.BigDecimal price, ItemStack item, String financialKey,
                                            String compensationKey, String itemId) {
        if (ConfigManager.getEconomyBackend().equalsIgnoreCase("DATABASE")) {
            DatabaseManager database = DatabaseManager.getInstance();
            if (!database.isReady()) return CompletableFuture.completedFuture(false);
            String snapshot = snapshot(item);
            try {
                return database.getExecutor().transaction("chestshop.journal.begin", connection -> {
                    String lock = database.getType() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL ? " FOR UPDATE" : "";
                    try (var select = connection.prepareStatement("SELECT operation,shop_key,participant_uuid,amount,quantity,item_snapshot,financial_key FROM bbe_chestshop_operations WHERE transaction_id=?" + lock)) {
                        select.setString(1, id);
                        try (var rows = select.executeQuery()) {
                            if (rows.next()) {
                                return operation.equals(rows.getString(1))
                                        && shop.toKey().equals(rows.getString(2))
                                        && participant.toString().equals(rows.getString(3))
                                        && price.compareTo(rows.getBigDecimal(4)) == 0
                                        && item.getCount() == rows.getInt(5)
                                        && snapshot.equals(rows.getString(6))
                                        && financialKey.equals(rows.getString(7));
                            }
                        }
                    }
                    try (var insert = connection.prepareStatement("INSERT INTO bbe_chestshop_operations (transaction_id,operation,shop_key,dimension_key,sign_x,sign_y,sign_z,chest_dimension_key,chest_x,chest_y,chest_z,participant_uuid,owner_uuid,admin_shop,amount,quantity,item_id,item_snapshot,financial_key,compensation_key,status,inventory_checkpoint,money_checkpoint,created_at,updated_at,recovery_attempts,last_error,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                        int i = 1;
                        insert.setString(i++, id); insert.setString(i++, operation); insert.setString(i++, shop.toKey());
                        insert.setString(i++, shop.signDimension); insert.setInt(i++, shop.signX); insert.setInt(i++, shop.signY); insert.setInt(i++, shop.signZ);
                        if (shop.chestDimension == null) insert.setNull(i++, java.sql.Types.VARCHAR); else insert.setString(i++, shop.chestDimension);
                        if (shop.hasChest) { insert.setInt(i++, shop.chestX); insert.setInt(i++, shop.chestY); insert.setInt(i++, shop.chestZ); }
                        else { insert.setNull(i++, java.sql.Types.INTEGER); insert.setNull(i++, java.sql.Types.INTEGER); insert.setNull(i++, java.sql.Types.INTEGER); }
                        insert.setString(i++, participant.toString());
                        if (shop.ownerUUID == null) insert.setNull(i++, java.sql.Types.VARCHAR); else insert.setString(i++, shop.ownerUUID.toString());
                        insert.setBoolean(i++, shop.isAdminShop()); insert.setBigDecimal(i++, price); insert.setInt(i++, item.getCount());
                        insert.setString(i++, itemId); insert.setString(i++, snapshot); insert.setString(i++, financialKey); insert.setString(i++, compensationKey);
                        insert.setString(i++, "CREATED"); insert.setString(i++, "PENDING"); insert.setString(i++, "PENDING");
                        long now = System.currentTimeMillis(); insert.setLong(i++, now); insert.setLong(i++, now); insert.setInt(i++, 0); insert.setNull(i++, java.sql.Types.VARCHAR); insert.setLong(i, 0);
                        insert.executeUpdate();
                        return true;
                    }
                });
            } catch (RuntimeException unavailable) {
                return CompletableFuture.completedFuture(false);
            }
        }
        return CompletableFuture.supplyAsync(() -> begin(id, operation, shop, participant, price, item));
    }

    CompletableFuture<Boolean> hasPendingDurable(String shop, UUID participant) {
        if (ConfigManager.getEconomyBackend().equalsIgnoreCase("DATABASE")) {
            DatabaseManager database = DatabaseManager.getInstance();
            if (!database.isReady()) return CompletableFuture.completedFuture(true);
            try {
                return database.getExecutor().querySingle("chestshop.journal.pending",
                        "SELECT 1 FROM bbe_chestshop_operations WHERE shop_key=? AND participant_uuid=? AND status NOT IN ('COMPLETED','ROLLED_BACK','CANCELLED') LIMIT 1",
                        statement -> { statement.setString(1, shop); statement.setString(2, participant.toString()); }, row -> true)
                        .thenApply(Optional::isPresent);
            } catch (RuntimeException unavailable) {
                return CompletableFuture.completedFuture(true);
            }
        }
        return CompletableFuture.supplyAsync(() -> hasPending(shop, participant));
    }

    CompletableFuture<Boolean> checkpointDurable(String id, String status, String inventory, String money, String error) {
        if (ConfigManager.getEconomyBackend().equalsIgnoreCase("DATABASE")) {
            DatabaseManager database = DatabaseManager.getInstance();
            if (!database.isReady()) return CompletableFuture.completedFuture(false);
            try {
                return database.getExecutor().executeUpdate("chestshop.journal.checkpoint",
                        "UPDATE bbe_chestshop_operations SET status=?,inventory_checkpoint=?,money_checkpoint=?,last_error=?,updated_at=?,version=version+1 WHERE transaction_id=?",
                        statement -> { statement.setString(1, status); statement.setString(2, inventory); statement.setString(3, money); statement.setString(4, error); statement.setLong(5, System.currentTimeMillis()); statement.setString(6, id); })
                        .thenApply(updated -> updated == 1);
            } catch (RuntimeException unavailable) {
                return CompletableFuture.completedFuture(false);
            }
        }
        return CompletableFuture.supplyAsync(() -> update(id, status, inventory, money, error));
    }

    CompletableFuture<Boolean> completeDurable(String id) {
        return checkpointDurable(id, "COMPLETED", "APPLIED", "COMPLETED", null);
    }

    CompletableFuture<Boolean> recoveryDurable(String id, String error) {
        return checkpointDurable(id, "RECOVERY_REQUIRED", "UNKNOWN", "UNKNOWN", error);
    }

    public CompletableFuture<Long> pendingCount() {
        if (!ConfigManager.getEconomyBackend().equalsIgnoreCase("DATABASE")) {
            return CompletableFuture.supplyAsync(() -> {
                synchronized (this) { load(); return entries.values().stream().filter(entry -> entry.status() == Status.PENDING || entry.status() == Status.RECOVERY_REQUIRED).count(); }
            });
        }
        DatabaseManager database = DatabaseManager.getInstance();
        if (!database.isReady()) return CompletableFuture.completedFuture(-1L);
        return database.getExecutor().queryOne("chestshop.journal.pending.count",
                "SELECT COUNT(*) FROM bbe_chestshop_operations WHERE status NOT IN ('COMPLETED','ROLLED_BACK','CANCELLED')",
                null, row -> row.getLong(1));
    }

    private boolean update(String id, Status status) {
        load();
        Entry entry = entries.get(id);
        if (entry != null) entries.put(id, new Entry(entry.operation(), entry.shop(), entry.participant(), entry.price(), entry.quantity(), entry.item(), status));
        return save();
    }

    private boolean update(String id, String status, String inventory, String money, String error) {
        load();
        Entry entry = entries.get(id);
        if (entry == null) return false;
        entries.put(id, new Entry(entry.operation(), entry.shop(), entry.participant(), entry.price(), entry.quantity(), entry.item(), statusOf(status)));
        return save();
    }

    private static String snapshot(ItemStack item) {
        return com.pedrodalben.bigbangessentials.crates.domain.ItemSerializer.serialize(item).toString();
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(path)) return;
            Map<String, Entry> stored = GSON.fromJson(Files.readString(path),
                    new com.google.gson.reflect.TypeToken<Map<String, Entry>>(){}.getType());
            if (stored != null) entries.putAll(stored);
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger(ChestShopTransactionJournal.class).error("ChestShop journal is corrupt: {}", path, error);
        }
    }

    private boolean save() {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) { GSON.toJson(entries, writer); }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger(ChestShopTransactionJournal.class).error("ChestShop journal write failed: {}", path, error);
        }
        return false;
    }
}
