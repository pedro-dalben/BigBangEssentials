package com.pedrodalben.bigbangessentials.objectives;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Context for a single player activity event. Used by objective/progression systems
 * to match tasks and apply anti-exploit policies consistently.
 */
public class ObjectiveEventContext {
    private final ServerPlayer player;
    private final ObjectiveActionType actionType;
    private final Object target;
    private final String registryId;
    private final String dimension;
    private final BlockPos pos;
    private final boolean cancelled;
    private final boolean fakePlayer;
    private final boolean playerPlacedBlock;
    private final boolean spawnerSpawned;
    private final boolean automationBlocked;
    private final boolean duplicateInTick;

    private ObjectiveEventContext(Builder builder) {
        this.player = builder.player;
        this.actionType = builder.actionType;
        this.target = builder.target;
        this.registryId = builder.registryId;
        this.dimension = builder.dimension;
        this.pos = builder.pos;
        this.cancelled = builder.cancelled;
        this.fakePlayer = builder.fakePlayer;
        this.playerPlacedBlock = builder.playerPlacedBlock;
        this.spawnerSpawned = builder.spawnerSpawned;
        this.automationBlocked = builder.automationBlocked;
        this.duplicateInTick = builder.duplicateInTick;
    }

    public UUID getPlayerUuid() {
        return player != null ? player.getUUID() : null;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ObjectiveActionType getActionType() {
        return actionType;
    }

    public Object getTarget() {
        return target;
    }

    public String getRegistryId() {
        return registryId;
    }

    public String getDimension() {
        return dimension;
    }

    @Nullable
    public BlockPos getPos() {
        return pos;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isFakePlayer() {
        return fakePlayer;
    }

    public boolean isPlayerPlacedBlock() {
        return playerPlacedBlock;
    }

    public boolean isSpawnerSpawned() {
        return spawnerSpawned;
    }

    public boolean isAutomationBlocked() {
        return automationBlocked;
    }

    public boolean isDuplicateInTick() {
        return duplicateInTick;
    }

    public static Builder builder(ServerPlayer player, ObjectiveActionType actionType) {
        return new Builder(player, actionType);
    }

    public static class Builder {
        private final ServerPlayer player;
        private final ObjectiveActionType actionType;
        private Object target;
        private String registryId = "";
        private String dimension = "";
        private BlockPos pos;
        private boolean cancelled;
        private boolean fakePlayer;
        private boolean playerPlacedBlock;
        private boolean spawnerSpawned;
        private boolean automationBlocked;
        private boolean duplicateInTick;

        private Builder(ServerPlayer player, ObjectiveActionType actionType) {
            this.player = player;
            this.actionType = actionType;
        }

        public Builder target(Object target) {
            this.target = target;
            return this;
        }

        public Builder registryId(String registryId) {
            this.registryId = registryId != null ? registryId : "";
            return this;
        }

        public Builder dimension(String dimension) {
            this.dimension = dimension != null ? dimension : "";
            return this;
        }

        public Builder pos(BlockPos pos) {
            this.pos = pos;
            return this;
        }

        public Builder cancelled(boolean cancelled) {
            this.cancelled = cancelled;
            return this;
        }

        public Builder fakePlayer(boolean fakePlayer) {
            this.fakePlayer = fakePlayer;
            return this;
        }

        public Builder playerPlacedBlock(boolean playerPlacedBlock) {
            this.playerPlacedBlock = playerPlacedBlock;
            return this;
        }

        public Builder spawnerSpawned(boolean spawnerSpawned) {
            this.spawnerSpawned = spawnerSpawned;
            return this;
        }

        public Builder automationBlocked(boolean automationBlocked) {
            this.automationBlocked = automationBlocked;
            return this;
        }

        public Builder duplicateInTick(boolean duplicateInTick) {
            this.duplicateInTick = duplicateInTick;
            return this;
        }

        public ObjectiveEventContext build() {
            return new ObjectiveEventContext(this);
        }
    }
}
