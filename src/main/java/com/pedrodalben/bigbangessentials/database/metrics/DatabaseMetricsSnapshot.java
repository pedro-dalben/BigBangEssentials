package com.pedrodalben.bigbangessentials.database.metrics;

/**
 * Immutable snapshot of database performance metrics.
 */
public record DatabaseMetricsSnapshot(
    long executedQueries,
    long failedQueries,
    long slowQueries,
    long activeTransactions,
    long queuedTasks,
    long rejectedTasks,
    long averageExecutionTimeMs,
    long maximumExecutionTimeMs
) {}
