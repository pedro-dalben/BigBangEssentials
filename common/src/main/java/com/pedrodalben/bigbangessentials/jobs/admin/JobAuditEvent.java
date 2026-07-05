package com.pedrodalben.bigbangessentials.jobs.admin;

import java.util.UUID;

/**
 * Record representing an audited job progression or administrative action event.
 */
public record JobAuditEvent(
        String eventId,
        UUID targetUuid,
        String eventType,
        String jobId,
        String slotType,
        UUID actorUuid,
        String reason,
        long createdAt,
        String metadata
) {}
