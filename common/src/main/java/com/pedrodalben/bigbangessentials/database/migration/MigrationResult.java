package com.pedrodalben.bigbangessentials.database.migration;

/**
 * Result of a single migration execution.
 */
public record MigrationResult(
    long version,
    String description,
    boolean success,
    long executionMs,
    String errorMessage
) {}
