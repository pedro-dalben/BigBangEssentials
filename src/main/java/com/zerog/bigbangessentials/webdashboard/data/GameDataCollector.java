package com.zerog.bigbangessentials.webdashboard.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Game Data Collector
 * Collects game events and statistics for the Dashboard API
 * 
 * Endpoints served:
 * - Game Events (real-time event stream)
 * - Game Statistics (global game stats)
 * - Game Activity (player actions, block changes)
 */
@EventBusSubscriber
public class GameDataCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameDataCollector.class);
    private static final int MAX_EVENTS = 1000; // Maximum events to keep in memory
    
    @SuppressWarnings("unused") // Reserved for future use (e.g., real-time event streaming)
    private final MinecraftServer server;
    private static final Object EVENT_LOCK = new Object();
    private static final ArrayDeque<GameEvent> eventQueue = new ArrayDeque<>();
    private static volatile boolean collectionEnabled = false;
    
    public GameDataCollector(MinecraftServer server) {
        this.server = server;
    }

    public void setCollectionEnabled(boolean enabled) {
        collectionEnabled = enabled;
    }
    
    /**
     * Get recent game events
     * Endpoint: GET /api/game/events?limit=100
     */
    public JsonObject getGameEvents(int limit) {
        JsonObject response = new JsonObject();
        JsonArray events = new JsonArray();

        if (!collectionEnabled) {
            response.add("events", events);
            response.addProperty("count", 0);
            response.addProperty("total", 0);
            return response;
        }

        if (limit <= 0) {
            response.add("events", events);
            response.addProperty("count", 0);
            response.addProperty("total", 0);
            return response;
        }

        List<GameEvent> recentEvents;
        synchronized (EVENT_LOCK) {
            recentEvents = new ArrayList<>(eventQueue);
        }

        if (recentEvents.size() > limit) {
            recentEvents = recentEvents.subList(recentEvents.size() - limit, recentEvents.size());
        }

        recentEvents.forEach(event -> events.add(event.toJson()));
        
        response.add("events", events);
        response.addProperty("count", events.size());
        int total;
        synchronized (EVENT_LOCK) {
            total = eventQueue.size();
        }
        response.addProperty("total", total);
        
        return response;
    }
    
    /**
     * Get game statistics
     * Endpoint: GET /api/game/statistics
     */
    public JsonObject getGameStatistics() {
        JsonObject stats = new JsonObject();
        
        // FUTURE: Implement persistent statistics tracking across server restarts
        // This would require database integration or file-based storage
        
        JsonObject players = new JsonObject();
        players.addProperty("totalPlayers", 0); // FUTURE: Track total unique players via database
        players.addProperty("totalPlayTime", 0); // FUTURE: Track total play time via event listeners
        stats.add("players", players);
        
        JsonObject blocks = new JsonObject();
        blocks.addProperty("totalBroken", 0); // FUTURE: Track from BlockBreakEvent
        blocks.addProperty("totalPlaced", 0); // FUTURE: Track from BlockPlaceEvent
        stats.add("blocks", blocks);
        
        JsonObject combat = new JsonObject();
        combat.addProperty("totalKills", 0); // FUTURE: Track from LivingDeathEvent
        combat.addProperty("totalDeaths", 0); // FUTURE: Track from player death events
        stats.add("combat", combat);
        
        return stats;
    }
    
    /**
     * Get game activity summary
     * Endpoint: GET /api/game/activity
     */
    public JsonObject getGameActivity() {
        JsonObject activity = new JsonObject();

        if (!collectionEnabled) {
            activity.addProperty("playerJoins", 0);
            activity.addProperty("playerLeaves", 0);
            activity.addProperty("blockBreaks", 0);
            activity.addProperty("blockPlaces", 0);
            activity.addProperty("deaths", 0);
            activity.addProperty("period", "last_hour");
            return activity;
        }
        
        // Count events by type in the last hour
        long oneHourAgo = Instant.now().toEpochMilli() - (60 * 60 * 1000);
        
        int playerJoins = 0;
        int playerLeaves = 0;
        int blockBreaks = 0;
        int blockPlaces = 0;
        int deaths = 0;
        
        synchronized (EVENT_LOCK) {
            for (GameEvent event : eventQueue) {
                if (event.timestamp < oneHourAgo) continue;

                switch (event.type) {
                    case "player.join" -> playerJoins++;
                    case "player.leave" -> playerLeaves++;
                    case "block.break" -> blockBreaks++;
                    case "block.place" -> blockPlaces++;
                    case "player.death" -> deaths++;
                }
            }
        }
        
        activity.addProperty("playerJoins", playerJoins);
        activity.addProperty("playerLeaves", playerLeaves);
        activity.addProperty("blockBreaks", blockBreaks);
        activity.addProperty("blockPlaces", blockPlaces);
        activity.addProperty("deaths", deaths);
        activity.addProperty("period", "last_hour");
        
        return activity;
    }
    
    /**
     * Get top played blocks
     * Endpoint: GET /api/game/top-blocks
     */
    public JsonObject getTopBlocks() {
        JsonObject response = new JsonObject();
        
        // FUTURE: Track block statistics from break/place events
        JsonArray topBroken = new JsonArray();
        JsonArray topPlaced = new JsonArray();
        
        response.add("topBroken", topBroken);
        response.add("topPlaced", topPlaced);
        
        return response;
    }
    
    // Event Listeners
    
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!collectionEnabled) return;
        addEvent(new GameEvent(
            "player.join",
            event.getEntity().getName().getString() + " joined the game",
            event.getEntity().getUUID().toString()
        ));
    }
    
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!collectionEnabled) return;
        addEvent(new GameEvent(
            "player.leave",
            event.getEntity().getName().getString() + " left the game",
            event.getEntity().getUUID().toString()
        ));
    }
    
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!collectionEnabled) return;
        String blockName = event.getState().getBlock().getName().getString();
        String playerName = event.getPlayer().getName().getString();
        
        JsonObject data = new JsonObject();
        data.addProperty("block", blockName);
        data.addProperty("x", event.getPos().getX());
        data.addProperty("y", event.getPos().getY());
        data.addProperty("z", event.getPos().getZ());
        
        addEvent(new GameEvent(
            "block.break",
            playerName + " broke " + blockName,
            event.getPlayer().getUUID().toString(),
            data
        ));
    }
    
    // Helper methods
    
    private static void addEvent(GameEvent event) {
        if (!collectionEnabled) {
            return;
        }

        synchronized (EVENT_LOCK) {
            if (eventQueue.size() >= MAX_EVENTS) {
                eventQueue.removeFirst();
            }
            eventQueue.addLast(event);
        }
    }
    
    /**
     * Clear event history (admin action)
     * Endpoint: DELETE /api/game/events
     */
    public void clearEvents() {
        synchronized (EVENT_LOCK) {
            eventQueue.clear();
        }
        LOGGER.info("Game event history cleared");
    }
    
    // Inner class for game events
    
    public static class GameEvent {
        public final String type;
        public final String message;
        public final String playerUuid;
        public final long timestamp;
        public final JsonObject data;
        
        public GameEvent(String type, String message, String playerUuid) {
            this(type, message, playerUuid, new JsonObject());
        }
        
        public GameEvent(String type, String message, String playerUuid, JsonObject data) {
            this.type = type;
            this.message = message;
            this.playerUuid = playerUuid;
            this.timestamp = Instant.now().toEpochMilli();
            this.data = data;
        }
        
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", type);
            json.addProperty("message", message);
            json.addProperty("playerUuid", playerUuid);
            json.addProperty("timestamp", timestamp);
            if (data != null && !data.isEmpty()) {
                json.add("data", data);
            }
            return json;
        }
    }
}
