package com.pedrodalben.bigbangessentials.pokemarket.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketNotificationRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();
    public CompletableFuture<Integer> create(UUID player, String type, String title, String message, String referenceType, String referenceId, String metadata) {
        return database.getExecutor().executeUpdate("pokemarket.notification.create", "INSERT INTO bbe_pokemarket_notifications (id,player_uuid,type,title_key,message_key,reference_type,reference_id,status,created_at,metadata_json) VALUES (?,?,?,?,?,?,?,?,?,?)", s -> { s.setString(1, UUID.randomUUID().toString()); s.setString(2, player.toString()); s.setString(3, type); s.setString(4, title); s.setString(5, message); s.setString(6, referenceType); s.setString(7, referenceId); s.setString(8, NotificationStatus.UNREAD.name()); s.setLong(9, System.currentTimeMillis()); s.setString(10, metadata); });
    }

    /** Stable event receipt: retries of the same market event create one notification. */
    public CompletableFuture<Integer> createOnce(UUID player, String eventKey, String type, String title, String message, String referenceType, String referenceId, String metadata) {
        UUID id = UUID.nameUUIDFromBytes((player + ":" + eventKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return database.getExecutor().executeUpdate("pokemarket.notification.create.once", "INSERT INTO bbe_pokemarket_notifications (id,player_uuid,type,title_key,message_key,reference_type,reference_id,status,created_at,metadata_json) VALUES (?,?,?,?,?,?,?,?,?,?)", s -> { s.setString(1, id.toString()); s.setString(2, player.toString()); s.setString(3, type); s.setString(4, title); s.setString(5, message); s.setString(6, referenceType); s.setString(7, referenceId); s.setString(8, NotificationStatus.UNREAD.name()); s.setLong(9, System.currentTimeMillis()); s.setString(10, metadata); }).exceptionallyCompose(error -> database.getExecutor().querySingle("pokemarket.notification.existing", "SELECT 1 FROM bbe_pokemarket_notifications WHERE id=?", s -> s.setString(1, id.toString()), r -> 1).thenCompose(found -> found.isPresent() ? CompletableFuture.completedFuture(1) : CompletableFuture.failedFuture(error)));
    }
    public CompletableFuture<List<PokeMarketNotification>> find(UUID player, int page, int size) {
        return database.getExecutor().queryList("pokemarket.notification.find", "SELECT id,player_uuid,type,title_key,message_key,reference_type,reference_id,status,created_at FROM bbe_pokemarket_notifications WHERE player_uuid=? ORDER BY created_at DESC LIMIT ? OFFSET ?", s -> { s.setString(1, player.toString()); s.setInt(2, Math.max(1, Math.min(size, 100))); s.setInt(3, Math.max(0, page) * Math.max(1, Math.min(size, 100))); }, r -> new PokeMarketNotification(UUID.fromString(r.getString(1)), UUID.fromString(r.getString(2)), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), NotificationStatus.valueOf(r.getString(8)), r.getLong(9)));
    }
    public CompletableFuture<Integer> markDelivered(UUID player) { return database.getExecutor().executeUpdate("pokemarket.notification.delivered", "UPDATE bbe_pokemarket_notifications SET status='DELIVERED',delivered_at=? WHERE player_uuid=? AND status='UNREAD'", s -> { s.setLong(1, System.currentTimeMillis()); s.setString(2, player.toString()); }); }
    public CompletableFuture<Integer> markRead(UUID player, UUID id) { return database.getExecutor().executeUpdate("pokemarket.notification.read", "UPDATE bbe_pokemarket_notifications SET status='READ',read_at=? WHERE id=? AND player_uuid=? AND status<>'READ'", s -> { s.setLong(1, System.currentTimeMillis()); s.setString(2, id.toString()); s.setString(3, player.toString()); }); }
    public CompletableFuture<Integer> markAllRead(UUID player) { return database.getExecutor().executeUpdate("pokemarket.notification.read.all", "UPDATE bbe_pokemarket_notifications SET status='READ',read_at=? WHERE player_uuid=? AND status<>'READ'", s -> { s.setLong(1, System.currentTimeMillis()); s.setString(2, player.toString()); }); }
    public CompletableFuture<Long> unread(UUID player) { return database.getExecutor().queryOne("pokemarket.notification.unread", "SELECT COUNT(*) FROM bbe_pokemarket_notifications WHERE player_uuid=? AND status<>'READ'", s -> s.setString(1, player.toString()), r -> r.getLong(1)); }
}
