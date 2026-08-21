package com.pedrodalben.bigbangessentials.jobs.pipeline;

import java.util.UUID;

public record RawJobEvent(
    UUID playerId,
    String loader,        // "NEOFORGE" or "FABRIC"
    String eventSource,   // "BLOCK_BREAK", "BLOCK_PLACE", "ENTITY_DEATH", etc.
    long serverTick,
    String dimension,
    String position,
    String beforeState,   // block state string before
    String afterState,    // block state string after
    String registryId,
    String itemUsed,      // tool registry id
    String toolTags,      // tool tags comma-separated
    int amount,
    String entityTargetId, // entity registry id killed
    UUID entityUuid,
    String spawnReason,
    boolean cancelled,
    boolean autoAction,   // auto vs manual
    String pokemonSpecies,
    String pokemonForm,
    String correlationId  // physical event fingerprint
) {
    public RawJobEvent {
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        if (loader == null) loader = "UNKNOWN";
        if (eventSource == null) eventSource = "UNKNOWN";
        if (dimension == null) dimension = "";
        if (position == null) position = "";
        if (beforeState == null) beforeState = "";
        if (afterState == null) afterState = "";
        if (registryId == null) registryId = "";
        if (itemUsed == null) itemUsed = "";
        if (toolTags == null) toolTags = "";
        if (entityTargetId == null) entityTargetId = "";
        if (spawnReason == null) spawnReason = "";
        if (pokemonSpecies == null) pokemonSpecies = "";
        if (pokemonForm == null) pokemonForm = "";
        if (correlationId == null) correlationId = UUID.randomUUID().toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID playerId;
        private String loader = "UNKNOWN";
        private String eventSource = "UNKNOWN";
        private long serverTick;
        private String dimension = "";
        private String position = "";
        private String beforeState = "";
        private String afterState = "";
        private String registryId = "";
        private String itemUsed = "";
        private String toolTags = "";
        private int amount = 1;
        private String entityTargetId = "";
        private UUID entityUuid;
        private String spawnReason = "";
        private boolean cancelled;
        private boolean autoAction;
        private String pokemonSpecies = "";
        private String pokemonForm = "";
        private String correlationId;

        public Builder playerId(UUID v) { this.playerId = v; return this; }
        public Builder loader(String v) { this.loader = v; return this; }
        public Builder eventSource(String v) { this.eventSource = v; return this; }
        public Builder serverTick(long v) { this.serverTick = v; return this; }
        public Builder dimension(String v) { this.dimension = v; return this; }
        public Builder position(String v) { this.position = v; return this; }
        public Builder beforeState(String v) { this.beforeState = v; return this; }
        public Builder afterState(String v) { this.afterState = v; return this; }
        public Builder registryId(String v) { this.registryId = v; return this; }
        public Builder itemUsed(String v) { this.itemUsed = v; return this; }
        public Builder toolTags(String v) { this.toolTags = v; return this; }
        public Builder amount(int v) { this.amount = v; return this; }
        public Builder entityTargetId(String v) { this.entityTargetId = v; return this; }
        public Builder entityUuid(UUID v) { this.entityUuid = v; return this; }
        public Builder spawnReason(String v) { this.spawnReason = v; return this; }
        public Builder cancelled(boolean v) { this.cancelled = v; return this; }
        public Builder autoAction(boolean v) { this.autoAction = v; return this; }
        public Builder pokemonSpecies(String v) { this.pokemonSpecies = v; return this; }
        public Builder pokemonForm(String v) { this.pokemonForm = v; return this; }
        public Builder correlationId(String v) { this.correlationId = v; return this; }

        public RawJobEvent build() {
            return new RawJobEvent(playerId, loader, eventSource, serverTick, dimension, position,
                    beforeState, afterState, registryId, itemUsed, toolTags, amount,
                    entityTargetId, entityUuid, spawnReason, cancelled, autoAction,
                    pokemonSpecies, pokemonForm, correlationId);
        }
    }
}
