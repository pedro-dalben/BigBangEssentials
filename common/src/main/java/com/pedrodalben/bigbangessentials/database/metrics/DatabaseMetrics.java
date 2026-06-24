package com.pedrodalben.bigbangessentials.database.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe collector for database performance metrics.
 */
public class DatabaseMetrics {
    private final LongAdder executedQueries = new LongAdder();
    private final LongAdder failedQueries = new LongAdder();
    private final LongAdder slowQueries = new LongAdder();
    private final LongAdder activeTransactions = new LongAdder();
    private final LongAdder rejectedTasks = new LongAdder();
    
    private final LongAdder totalExecutionTimeMs = new LongAdder();
    private final AtomicLong maximumExecutionTimeMs = new AtomicLong(0);

    public void recordExecution(long timeMs, boolean success, boolean slow) {
        executedQueries.increment();
        totalExecutionTimeMs.add(timeMs);
        
        // Update maximum execution time thread-safely
        long currentMax;
        do {
            currentMax = maximumExecutionTimeMs.get();
            if (timeMs <= currentMax) {
                break;
            }
        } while (!maximumExecutionTimeMs.compareAndSet(currentMax, timeMs));

        if (!success) {
            failedQueries.increment();
        }
        if (slow) {
            slowQueries.increment();
        }
    }

    public void incrementActiveTransactions() {
        activeTransactions.increment();
    }

    public void decrementActiveTransactions() {
        activeTransactions.decrement();
    }

    public void incrementRejectedTasks() {
        rejectedTasks.increment();
    }

    public DatabaseMetricsSnapshot getSnapshot(long queuedTasks) {
        long executed = executedQueries.sum();
        long avgTime = executed == 0 ? 0 : totalExecutionTimeMs.sum() / executed;
        return new DatabaseMetricsSnapshot(
            executed,
            failedQueries.sum(),
            slowQueries.sum(),
            activeTransactions.sum(),
            queuedTasks,
            rejectedTasks.sum(),
            avgTime,
            maximumExecutionTimeMs.get()
        );
    }

    public void reset() {
        executedQueries.reset();
        failedQueries.reset();
        slowQueries.reset();
        activeTransactions.reset();
        rejectedTasks.reset();
        totalExecutionTimeMs.reset();
        maximumExecutionTimeMs.set(0);
    }
}
