package com.pedrodalben.bigbangessentials.database.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.database.DatabaseHealth;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseState;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.api.DatabaseAPI;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfig;
import com.pedrodalben.bigbangessentials.database.metrics.DatabaseMetricsSnapshot;
import com.pedrodalben.bigbangessentials.database.migration.MigrationResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Implements Brigadier command nodes for database management.
 */
public class DatabaseCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseCommands.class);

    /**
     * Registers the /bigbangessentials database subcommand.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("database")
            .requires(DatabaseCommands::hasAdminPermission)
            .then(Commands.literal("status")
                .executes(DatabaseCommands::executeStatus)
            )
            .then(Commands.literal("test")
                .executes(DatabaseCommands::executeTest)
            )
            .then(Commands.literal("info")
                .executes(DatabaseCommands::executeInfo)
            )
            .then(Commands.literal("migrate")
                .executes(DatabaseCommands::executeMigrate)
            );
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        if (source.getEntity() == null) {
            return true; // Server console
        }
        // Consistent with other admin commands in BigBangEssentials
        return com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
            source.getEntity().getUUID(), "bigbangessentials.admin.database") ||
            com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                source.getEntity().getUUID(), "bigbangessentials.reload");
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        DatabaseManager manager = DatabaseManager.getInstance();

        source.sendSuccess(() -> Component.literal("§6§l═════ Database Subsystem Status ═════"), false);
        source.sendSuccess(() -> Component.literal("§eState: §f" + manager.getState()), false);
        source.sendSuccess(() -> Component.literal("§eType: §f" + (manager.getType() != null ? manager.getType() : "NONE")), false);
        source.sendSuccess(() -> Component.literal("§eEnabled: §f" + (manager.getConfig() != null ? manager.getConfig().isEnabled() : "false")), false);

        DatabaseHealth health = manager.getHealth();
        source.sendSuccess(() -> Component.literal("§eConnected: §f" + health.connected()), false);
        source.sendSuccess(() -> Component.literal("§ePing Latency: §f" + (health.latencyMs() >= 0 ? health.latencyMs() + "ms" : "N/A")), false);
        source.sendSuccess(() -> Component.literal("§eSchema Version: §f" + health.schemaVersion()), false);

        DatabaseMetricsSnapshot metrics = manager.getMetricsSnapshot();
        source.sendSuccess(() -> Component.literal("§eQueued Tasks: §f" + metrics.queuedTasks()), false);
        source.sendSuccess(() -> Component.literal("§eActive Transactions: §f" + metrics.activeTransactions()), false);
        source.sendSuccess(() -> Component.literal("§eTotal Queries: §f" + metrics.executedQueries()), false);
        source.sendSuccess(() -> Component.literal("§eFailed Queries: §f" + metrics.failedQueries()), false);
        source.sendSuccess(() -> Component.literal("§eSlow Queries: §f" + metrics.slowQueries()), false);
        source.sendSuccess(() -> Component.literal("§eAvg Execution Time: §f" + metrics.averageExecutionTimeMs() + "ms"), false);
        source.sendSuccess(() -> Component.literal("§eMax Execution Time: §f" + metrics.maximumExecutionTimeMs() + "ms"), false);
        source.sendSuccess(() -> Component.literal("§eAvg Queue/Connection/SQL/Commit: §f" + metrics.averageQueueWaitTimeMs() + "/" + metrics.averageConnectionWaitTimeMs() + "/" + metrics.averageSqlTimeMs() + "/" + metrics.averageCommitTimeMs() + "ms"), false);
        source.sendSuccess(() -> Component.literal("§ePeak Queue / Tx Retries: §f" + metrics.peakQueuedTasks() + " / " + metrics.transactionRetries()), false);

        if (manager.isPoolActive()) {
            source.sendSuccess(() -> Component.literal("§ePool Active Connections: §f" + manager.getPoolActiveConnections()), false);
            source.sendSuccess(() -> Component.literal("§ePool Idle Connections: §f" + manager.getPoolIdleConnections()), false);
            source.sendSuccess(() -> Component.literal("§ePool Total Connections: §f" + manager.getPoolTotalConnections()), false);
        }
        source.sendSuccess(() -> Component.literal("§6§l══════════════════════════════════"), false);

        return 1;
    }

    private static int executeTest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§eRunning database connection test (asynchronous)..."), false);

        DatabaseAPI.healthCheck().whenComplete((health, ex) -> {
            if (ex != null) {
                source.sendFailure(Component.literal("§c❌ Database connection test failed: " + ex.getMessage()));
                return;
            }
            if (health.connected()) {
                source.sendSuccess(() -> Component.literal("§a✔ Database connection is healthy!"), true);
                source.sendSuccess(() -> Component.literal(String.format("§aLatency: %dms | Schema Version: %d", 
                    health.latencyMs(), health.schemaVersion())), true);
            } else {
                source.sendFailure(Component.literal("§c❌ Database connection is unhealthy: " + health.message()));
            }
        });

        return 1;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        DatabaseManager manager = DatabaseManager.getInstance();
        DatabaseConfig config = manager.getConfig();

        if (config == null) {
            source.sendFailure(Component.literal("§c❌ Configuration is not loaded."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6§l═════ Database Configuration ═════"), false);
        source.sendSuccess(() -> Component.literal("§eType: §f" + config.getType()), false);
        source.sendSuccess(() -> Component.literal("§eRequired: §f" + config.isRequired()), false);

        if (config.getType() == DatabaseType.SQLITE) {
            DatabaseConfig.SqliteConfig sqlite = config.getSqlite();
            source.sendSuccess(() -> Component.literal("§eSQLite File: §f" + sqlite.getFile()), false);
            source.sendSuccess(() -> Component.literal("§eSQLite WAL Mode: §f" + sqlite.isWal()), false);
            source.sendSuccess(() -> Component.literal("§eSQLite Foreign Keys: §f" + sqlite.isForeignKeys()), false);
        } else if (config.getType() == DatabaseType.MYSQL) {
            DatabaseConfig.MySqlConfig mysql = config.getMysql();
            source.sendSuccess(() -> Component.literal("§eMySQL Host: §f" + mysql.getHost()), false);
            source.sendSuccess(() -> Component.literal("§eMySQL Port: §f" + mysql.getPort()), false);
            source.sendSuccess(() -> Component.literal("§eMySQL Database: §f" + mysql.getDatabase()), false);
            source.sendSuccess(() -> Component.literal("§eMySQL Username: §f" + mysql.getUsername()), false);
            source.sendSuccess(() -> Component.literal("§eMySQL Password: §f********"), false);
            source.sendSuccess(() -> Component.literal("§eMySQL SSL Mode: §f" + mysql.getSslMode()), false);
        }

        DatabaseConfig.PoolConfig pool = config.getPool();
        source.sendSuccess(() -> Component.literal("§ePool Maximum Size: §f" + pool.getMaximumPoolSize()), false);
        source.sendSuccess(() -> Component.literal("§ePool Minimum Idle: §f" + pool.getMinimumIdle()), false);

        DatabaseConfig.ExecutorConfig executor = config.getExecutor();
        source.sendSuccess(() -> Component.literal("§eExecutor Threads: §f" + executor.getThreads()), false);
        source.sendSuccess(() -> Component.literal("§eExecutor Queue Capacity: §f" + executor.getQueueCapacity()), false);
        source.sendSuccess(() -> Component.literal("§6§l══════════════════════════════════"), false);

        return 1;
    }

    private static int executeMigrate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§eChecking database migrations..."), false);

        DatabaseManager manager = DatabaseManager.getInstance();
        if (!manager.isReady()) {
            source.sendFailure(Component.literal("§c❌ Database is not ready. State: " + manager.getState()));
            return 0;
        }

        // Run migrations on a worker thread asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                List<MigrationResult> results = manager.runPendingMigrations();
                if (results.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("§a✔ No pending migrations. Database schema is up-to-date."), true);
                } else {
                    source.sendSuccess(() -> Component.literal(String.format("§a✔ Executed %d migration(s):", results.size())), true);
                    for (MigrationResult result : results) {
                        source.sendSuccess(() -> Component.literal(String.format("  §a- Version %d (%s) [%dms]", 
                            result.version(), result.description(), result.executionMs())), true);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Manual database migration failed", e);
                source.sendFailure(Component.literal("§c❌ Migration failed: " + e.getMessage()));
            }
        });

        return 1;
    }
}
