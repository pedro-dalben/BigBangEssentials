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
    private final LongAdder totalQueueWaitTimeMs = new LongAdder();
    private final LongAdder totalConnectionWaitTimeMs = new LongAdder();
    private final LongAdder totalSqlTimeMs = new LongAdder();
    private final LongAdder totalCommitTimeMs = new LongAdder();
    private final AtomicLong maximumExecutionTimeMs = new AtomicLong(0);
    private final AtomicLong peakQueuedTasks = new AtomicLong(0);
    private final LongAdder transactionRetries = new LongAdder();

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

    public void recordQueueWait(long timeMs, long queuedTasks) {
        totalQueueWaitTimeMs.add(Math.max(0, timeMs));
        peakQueuedTasks.accumulateAndGet(Math.max(0, queuedTasks), Math::max);
    }

    public void recordConnectionWait(long timeMs) { totalConnectionWaitTimeMs.add(Math.max(0, timeMs)); }
    public void recordSqlTime(long timeMs) { totalSqlTimeMs.add(Math.max(0, timeMs)); }
    public void recordCommitTime(long timeMs) { totalCommitTimeMs.add(Math.max(0, timeMs)); }
    public void incrementTransactionRetries() { transactionRetries.increment(); }

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
            maximumExecutionTimeMs.get(),
            executed == 0 ? 0 : totalQueueWaitTimeMs.sum() / executed,
            executed == 0 ? 0 : totalConnectionWaitTimeMs.sum() / executed,
            executed == 0 ? 0 : totalSqlTimeMs.sum() / executed,
            executed == 0 ? 0 : totalCommitTimeMs.sum() / executed,
            peakQueuedTasks.get(),
            transactionRetries.sum()
        );
    }

    public void reset() {
        executedQueries.reset();
        failedQueries.reset();
        slowQueries.reset();
        activeTransactions.reset();
        rejectedTasks.reset();
        totalExecutionTimeMs.reset();
        totalQueueWaitTimeMs.reset();
        totalConnectionWaitTimeMs.reset();
        totalSqlTimeMs.reset();
        totalCommitTimeMs.reset();
        maximumExecutionTimeMs.set(0);
        peakQueuedTasks.set(0);
        transactionRetries.reset();
    }
}
