package com.pedrodalben.bigbangessentials.database;

import java.time.Instant;

/**
 * Represents the health status of the database connection.
 */
public record DatabaseHealth(
    DatabaseState state,
    DatabaseType type,
    boolean connected,
    long latencyMs,
    long schemaVersion,
    String message,
    Instant checkedAt
) {}
