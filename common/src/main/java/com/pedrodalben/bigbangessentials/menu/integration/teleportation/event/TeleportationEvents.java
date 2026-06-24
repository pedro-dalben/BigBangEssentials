package com.pedrodalben.bigbangessentials.menu.integration.teleportation.event;

import net.neoforged.bus.api.Event;
import java.util.UUID;

public class TeleportationEvents {
    
    public static class WarpCreatedEvent extends Event {
        private final String warpName;
        public WarpCreatedEvent(String warpName) {
            this.warpName = warpName;
        }
        public String getWarpName() { return warpName; }
    }

    public static class WarpDeletedEvent extends Event {
        private final String warpName;
        public WarpDeletedEvent(String warpName) {
            this.warpName = warpName;
        }
        public String getWarpName() { return warpName; }
    }

    public static class WarpUpdatedEvent extends Event {
        private final String warpName;
        public WarpUpdatedEvent(String warpName) {
            this.warpName = warpName;
        }
        public String getWarpName() { return warpName; }
    }

    public static class HomeCreatedEvent extends Event {
        private final UUID playerId;
        private final String homeName;
        public HomeCreatedEvent(UUID playerId, String homeName) {
            this.playerId = playerId;
            this.homeName = homeName;
        }
        public UUID getPlayerId() { return playerId; }
        public String getHomeName() { return homeName; }
    }

    public static class HomeDeletedEvent extends Event {
        private final UUID playerId;
        private final String homeName;
        public HomeDeletedEvent(UUID playerId, String homeName) {
            this.playerId = playerId;
            this.homeName = homeName;
        }
        public UUID getPlayerId() { return playerId; }
        public String getHomeName() { return homeName; }
    }

    public static class HomeUpdatedEvent extends Event {
        private final UUID playerId;
        private final String homeName;
        public HomeUpdatedEvent(UUID playerId, String homeName) {
            this.playerId = playerId;
            this.homeName = homeName;
        }
        public UUID getPlayerId() { return playerId; }
        public String getHomeName() { return homeName; }
    }

    public static class PlayerWarpCreatedEvent extends Event {
        private final UUID ownerId;
        private final String warpName;
        public PlayerWarpCreatedEvent(UUID ownerId, String warpName) {
            this.ownerId = ownerId;
            this.warpName = warpName;
        }
        public UUID getOwnerId() { return ownerId; }
        public String getWarpName() { return warpName; }
    }

    public static class PlayerWarpDeletedEvent extends Event {
        private final UUID ownerId;
        private final String warpName;
        public PlayerWarpDeletedEvent(UUID ownerId, String warpName) {
            this.ownerId = ownerId;
            this.warpName = warpName;
        }
        public UUID getOwnerId() { return ownerId; }
        public String getWarpName() { return warpName; }
    }

    public static class PlayerWarpUpdatedEvent extends Event {
        private final UUID ownerId;
        private final String warpName;
        public PlayerWarpUpdatedEvent(UUID ownerId, String warpName) {
            this.ownerId = ownerId;
            this.warpName = warpName;
        }
        public UUID getOwnerId() { return ownerId; }
        public String getWarpName() { return warpName; }
    }
}
