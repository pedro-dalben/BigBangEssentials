package com.pedrodalben.bigbangessentials.jobs;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a normalized job action occurring in the game.
 * Safe for logging, testing, and persistence without holding live heavy entity/block references.
 */
public record JobAction(
    UUID actionId,
    UUID playerId,
    JobActionType type,
    String source,
    String targetId,
    Instant occurredAt,
    JobActionContext context
) {
    public JobAction {
        Objects.requireNonNull(actionId, "actionId cannot be null");
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
    }

    public static JobAction create(UUID playerId, JobActionType type, String source, String targetId, JobActionContext context) {
        return new JobAction(
            UUID.randomUUID(),
            playerId,
            type,
            source,
            targetId,
            Instant.now(),
            context
        );
    }

    public static JobAction createWithId(UUID actionId, UUID playerId, JobActionType type, String source, String targetId, JobActionContext context) {
        return new JobAction(
            actionId,
            playerId,
            type,
            source,
            targetId,
            Instant.now(),
            context
        );
    }
}
