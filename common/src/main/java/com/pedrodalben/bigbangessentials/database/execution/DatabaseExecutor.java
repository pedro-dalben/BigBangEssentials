package com.pedrodalben.bigbangessentials.database.execution;

import com.zaxxer.hikari.HikariDataSource;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfig;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseException;
import com.pedrodalben.bigbangessentials.database.metrics.DatabaseMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated thread pool and execution manager for database queries.
 */
public class DatabaseExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseExecutor.class);

    private final HikariDataSource dataSource;
    private final DatabaseConfig config;
    private final DatabaseType type;
    private final DatabaseMetrics metrics;
    private final ThreadPoolExecutor threadPool;

    public DatabaseExecutor(HikariDataSource dataSource, DatabaseConfig config, DatabaseType type, DatabaseMetrics metrics) {
        this.dataSource = dataSource;
        this.config = config;
        this.type = type;
        this.metrics = metrics;

        // Bounded queue
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(config.getExecutor().getQueueCapacity());

        // Named thread factory creating daemon threads
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "BigBangEssentials-Database-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        // Explicit rejection policy: increment metrics and throw exception
        RejectedExecutionHandler rejectionHandler = (r, executor) -> {
            metrics.incrementRejectedTasks();
            throw new RejectedExecutionException("Database execution queue is full");
        };

        this.threadPool = new ThreadPoolExecutor(
            config.getExecutor().getThreads(),
            config.getExecutor().getThreads(),
            0L, TimeUnit.MILLISECONDS,
            queue,
            threadFactory,
            rejectionHandler
        );
    }

    /**
     * Internal helper to submit task to executor and return CompletableFuture.
     */
    private <T> CompletableFuture<T> submit(String operationName, DatabaseTask<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            threadPool.execute(() -> {
                long startTime = System.currentTimeMillis();
                boolean success = false;
                T result = null;
                Throwable error = null;
                try {
                    result = task.execute();
                    success = true;
                } catch (Throwable e) {
                    error = e;
                } finally {
                    long duration = System.currentTimeMillis() - startTime;
                    boolean slow = duration >= config.getDebug().getSlowQueryThresholdMs();
                    metrics.recordExecution(duration, success, slow);

                    if (slow && config.getDebug().isLogSlowQueries()) {
                        LOGGER.warn("Slow database operation: {}\nDuration: {}ms\nDatabase: {}", 
                            operationName, duration, type);
                    }
                    if (config.getDebug().isLogQueries()) {
                        LOGGER.info("Database query executed: {} [{}ms] (success={})", 
                            operationName, duration, success);
                    }
                    if (success) {
                        future.complete(result);
                    } else {
                        future.completeExceptionally(error);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(new DatabaseException("Database executor queue is full", e));
        }
        return future;
    }

    /**
     * Executes a ping check on the database.
     */
    public CompletableFuture<Boolean> ping() {
        return submit("ping", () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /**
     * Executes an update query (INSERT, UPDATE, DELETE).
     */
    public CompletableFuture<Integer> executeUpdate(String sql, StatementBinder binder) {
        return executeUpdate(sql, sql, binder);
    }

    public CompletableFuture<Integer> executeUpdate(String operationName, String sql, StatementBinder binder) {
        return submit(operationName, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (binder != null) {
                    binder.bind(stmt);
                }
                return stmt.executeUpdate();
            }
        });
    }

    /**
     * Executes a query returning an Optional of the mapped single row.
     */
    public <T> CompletableFuture<java.util.Optional<T>> querySingle(String sql, StatementBinder binder, RowMapper<T> mapper) {
        return querySingle(sql, sql, binder, mapper);
    }

    public <T> CompletableFuture<java.util.Optional<T>> querySingle(String operationName, String sql, StatementBinder binder, RowMapper<T> mapper) {
        return submit(operationName, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (binder != null) {
                    binder.bind(stmt);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        T result = mapper.map(rs);
                        return java.util.Optional.ofNullable(result);
                    }
                    return java.util.Optional.empty();
                }
            }
        });
    }

    /**
     * Executes a query mapping a single row.
     */
    public <T> CompletableFuture<T> queryOne(String sql, StatementBinder binder, RowMapper<T> mapper) {
        return queryOne(sql, sql, binder, mapper);
    }

    public <T> CompletableFuture<T> queryOne(String operationName, String sql, StatementBinder binder, RowMapper<T> mapper) {
        return submit(operationName, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (binder != null) {
                    binder.bind(stmt);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapper.map(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Executes a query mapping multiple rows.
     */
    public <T> CompletableFuture<List<T>> queryList(String sql, StatementBinder binder, RowMapper<T> mapper) {
        return queryList(sql, sql, binder, mapper);
    }

    public <T> CompletableFuture<List<T>> queryList(String operationName, String sql, StatementBinder binder, RowMapper<T> mapper) {
        return submit(operationName, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (binder != null) {
                    binder.bind(stmt);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    List<T> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapper.map(rs));
                    }
                    return list;
                }
            }
        });
    }

    /**
     * Executes a batch update operation.
     */
    public CompletableFuture<int[]> executeBatch(String sql, List<StatementBinder> binders) {
        return executeBatch(sql, sql, binders);
    }

    public CompletableFuture<int[]> executeBatch(String operationName, String sql, List<StatementBinder> binders) {
        return submit(operationName, () -> {
            try (Connection conn = dataSource.getConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (StatementBinder binder : binders) {
                        binder.bind(stmt);
                        stmt.addBatch();
                    }
                    int[] results = stmt.executeBatch();
                    conn.commit();
                    return results;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            }
        });
    }

    /**
     * Executes a transaction callback on a single connection.
     */
    public <T> CompletableFuture<T> transaction(TransactionCallback<T> callback) {
        return transaction("transaction", callback);
    }

    public <T> CompletableFuture<T> transaction(String operationName, TransactionCallback<T> callback) {
        return submit(operationName, () -> {
            metrics.incrementActiveTransactions();
            try (Connection conn = dataSource.getConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    T result = callback.doInTransaction(conn);
                    conn.commit();
                    return result;
                } catch (Throwable e) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        LOGGER.error("Failed to rollback transaction", rollbackEx);
                    }
                    throw e;
                } finally {
                    try {
                        conn.setAutoCommit(originalAutoCommit);
                    } catch (SQLException ex) {
                        LOGGER.error("Failed to restore autocommit state", ex);
                    }
                    metrics.decrementActiveTransactions();
                }
            }
        });
    }

    /**
     * Returns the active queued task count.
     */
    public long getQueuedTaskCount() {
        return threadPool.getQueue().size();
    }

    /**
     * Shutdown the executor pool safely.
     */
    public void shutdown() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(config.getExecutor().getShutdownTimeoutSeconds(), TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface DatabaseTask<T> {
        T execute() throws Exception;
    }
}
