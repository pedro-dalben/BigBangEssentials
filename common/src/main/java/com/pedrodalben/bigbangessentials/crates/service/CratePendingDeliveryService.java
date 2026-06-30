package com.pedrodalben.bigbangessentials.crates.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.crates.domain.ItemSerializer;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CratePendingDeliveryService extends JdbcRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(CratePendingDeliveryService.class);
    private static final CratePendingDeliveryService INSTANCE = new CratePendingDeliveryService();

    private static final String TABLE = "crate_pending_deliveries";
    private static final String INSERT = "INSERT INTO " + TABLE + " (id, player_uuid, item_json, source, created_at) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_BY_PLAYER = "SELECT id, item_json FROM " + TABLE + " WHERE player_uuid = ? ORDER BY created_at ASC";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE id = ?";

    private final Gson gson = new Gson();
    private boolean tableCreated = false;

    private CratePendingDeliveryService() {
        ensureTable();
    }

    public static CratePendingDeliveryService getInstance() {
        return INSTANCE;
    }

    private synchronized void ensureTable() {
        if (tableCreated) return;
        try {
            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "id VARCHAR(36) NOT NULL, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "item_json TEXT NOT NULL, " +
                "source VARCHAR(64) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (id)" +
                ")", null).join();
            getDatabase().executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_delivery_player ON " + TABLE + " (player_uuid)", null).join();
            tableCreated = true;
            LOGGER.debug("Ensured table {} exists", TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create table {}: {}", TABLE, e.getMessage(), e);
        }
    }

    public void deliverOrStore(ServerPlayer player, ItemStack stack, String source) {
        if (stack == null || stack.isEmpty()) return;
        boolean added = player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            storePending(player.getUUID(), stack.copy(), source);
            player.sendSystemMessage(Component.literal("§eYour inventory was full! " + stack.getCount() + "x items were sent to your crate mailbox. Use §6/crates claim §eto retrieve them."));
            stack.setCount(0);
        }
    }

    public void storePending(UUID playerUuid, ItemStack stack, String source) {
        if (stack == null || stack.isEmpty()) return;
        try {
            JsonObject json = ItemSerializer.serialize(stack);
            String id = UUID.randomUUID().toString();
            getDatabase().executeUpdate(INSERT, stmt -> {
                stmt.setString(1, id);
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, gson.toJson(json));
                stmt.setString(4, source != null ? source : "UNKNOWN");
                stmt.setLong(5, System.currentTimeMillis());
            }).join();
            LOGGER.info("Stored pending delivery {} for player {} (source: {})", id, playerUuid, source);
        } catch (Exception e) {
            LOGGER.error("Failed to store pending delivery for player {}: {}", playerUuid, e.getMessage(), e);
        }
    }

    public int claimDeliveries(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        record PendingItem(String id, ItemStack stack) {}
        List<PendingItem> pending = new ArrayList<>();
        try {
            getDatabase().queryList(SELECT_BY_PLAYER, stmt -> stmt.setString(1, playerUuid.toString()), rs -> {
                String id = rs.getString("id");
                String jsonStr = rs.getString("item_json");
                try {
                    JsonObject obj = gson.fromJson(jsonStr, JsonObject.class);
                    ItemStack s = ItemSerializer.deserialize(obj);
                    if (!s.isEmpty()) pending.add(new PendingItem(id, s));
                } catch (Exception ex) {
                    LOGGER.warn("Corrupt pending delivery item {}: {}", id, ex.getMessage());
                }
                return null;
            }).join();
        } catch (Exception e) {
            LOGGER.error("Failed to query pending deliveries for player {}: {}", playerUuid, e.getMessage(), e);
            return 0;
        }

        if (pending.isEmpty()) return 0;

        int claimed = 0;
        for (PendingItem item : pending) {
            ItemStack stack = item.stack();
            int origCount = stack.getCount();
            player.getInventory().add(stack);
            if (stack.isEmpty()) {
                deleteDelivery(item.id());
                claimed += origCount;
            } else {
                int accepted = origCount - stack.getCount();
                if (accepted > 0) {
                    updateDelivery(item.id(), stack);
                    claimed += accepted;
                }
                break; // Inventory full again
            }
        }
        return claimed;
    }

    private void deleteDelivery(String id) {
        try {
            getDatabase().executeUpdate(DELETE, stmt -> stmt.setString(1, id)).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete pending delivery {}: {}", id, e.getMessage(), e);
        }
    }

    private void updateDelivery(String id, ItemStack remaining) {
        try {
            JsonObject json = ItemSerializer.serialize(remaining);
            getDatabase().executeUpdate("UPDATE " + TABLE + " SET item_json = ? WHERE id = ?", stmt -> {
                stmt.setString(1, gson.toJson(json));
                stmt.setString(2, id);
            }).join();
        } catch (Exception e) {
            LOGGER.error("Failed to update pending delivery {}: {}", id, e.getMessage(), e);
        }
    }
}
