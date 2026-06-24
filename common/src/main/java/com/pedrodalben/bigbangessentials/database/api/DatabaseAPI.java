package com.pedrodalben.bigbangessentials.database.api;

import com.pedrodalben.bigbangessentials.database.DatabaseHealth;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseState;
import com.pedrodalben.bigbangessentials.database.DatabaseType;

import java.util.concurrent.CompletableFuture;

/**
 * Public API for the Database infrastructure module.
 */
public final class DatabaseAPI {

    private DatabaseAPI() {
        // Prevent instantiation
    }

    /**
     * Checks if the database is initialized and ready for operations.
     */
    public static boolean isAvailable() {
        return DatabaseManager.getInstance().isReady();
    }

    /**
     * Gets the current state of the database manager.
     */
    public static DatabaseState getState() {
        return DatabaseManager.getInstance().getState();
    }

    /**
     * Gets the active database type (SQLite/MySQL).
     */
    public static DatabaseType getType() {
        return DatabaseManager.getInstance().getType();
    }

    /**
     * Performs an asynchronous health check of the database connection.
     * Guaranteed not to block the main server thread.
     */
    public static CompletableFuture<DatabaseHealth> healthCheck() {
        DatabaseManager manager = DatabaseManager.getInstance();
        CompletableFuture<DatabaseHealth> future = new CompletableFuture<>();
        
        if (!manager.isReady()) {
            future.complete(manager.getHealth());
            return future;
        }

        // Run health check logic asynchronously using the executor to avoid blocking the caller's thread
        manager.getExecutor().ping().whenComplete((success, throwable) -> {
            future.complete(manager.getHealth());
        });

        return future;
    }
}
