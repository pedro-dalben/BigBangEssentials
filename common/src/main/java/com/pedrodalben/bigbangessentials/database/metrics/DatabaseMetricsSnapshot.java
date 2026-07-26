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
    long maximumExecutionTimeMs,
    long averageQueueWaitTimeMs,
    long averageConnectionWaitTimeMs,
    long averageSqlTimeMs,
    long averageCommitTimeMs,
    long peakQueuedTasks,
    long transactionRetries
) {
    public DatabaseMetricsSnapshot(long executedQueries, long failedQueries, long slowQueries, long activeTransactions,
                                   long queuedTasks, long rejectedTasks, long averageExecutionTimeMs, long maximumExecutionTimeMs) {
        this(executedQueries, failedQueries, slowQueries, activeTransactions, queuedTasks, rejectedTasks,
                averageExecutionTimeMs, maximumExecutionTimeMs, 0, 0, 0, 0, queuedTasks, 0);
    }
}
