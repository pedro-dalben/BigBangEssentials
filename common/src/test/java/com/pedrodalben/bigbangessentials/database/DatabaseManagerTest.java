package com.pedrodalben.bigbangessentials.database;

import com.pedrodalben.bigbangessentials.database.config.DatabaseConfig;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfigLoader;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfigValidator;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseException;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseUnavailableException;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import com.pedrodalben.bigbangessentials.database.metrics.DatabaseMetricsSnapshot;
import com.pedrodalben.bigbangessentials.database.migration.MigrationResult;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration and unit tests for the database module.
 */
public class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private File tempConfigFile;
    private File tempDbFile;

    @BeforeEach
    public void setUp() {
        tempConfigFile = tempDir.resolve("database.json").toFile();
        tempDbFile = tempDir.resolve("test.db").toFile();
        DatabaseManager.getInstance().shutdown();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.getInstance().shutdown();
    }

    @Test
    public void testConfigLoaderCreatesDefaultFile() throws Exception {
        try (MockedStatic<ResourceUtil> mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class)) {
            mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

            assertFalse(tempConfigFile.exists());
            
            // Should create default file and load it
            DatabaseConfig config = DatabaseConfigLoader.load();
            
            assertTrue(tempConfigFile.exists());
            assertNotNull(config);
            assertEquals(DatabaseType.SQLITE, config.getType());
            assertTrue(config.isEnabled());
            assertFalse(config.isRequired());
        }
    }

    @Test
    public void testEnvVarResolution() throws Exception {
        try (MockedStatic<ResourceUtil> mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class)) {
            mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

            // Write custom JSON config with environment variable references
            // We use PATH here since it is guaranteed to exist on any Linux test environment
            String json = "{\n" +
                          "  \"enabled\": true,\n" +
                          "  \"type\": \"MYSQL\",\n" +
                          "  \"mysql\": {\n" +
                          "    \"host\": \"127.0.0.1\",\n" +
                          "    \"port\": 3306,\n" +
                          "    \"database\": \"test\",\n" +
                          "    \"username\": \"test\",\n" +
                          "    \"password\": \"${ENV:PATH}\"\n" +
                          "  }\n" +
                          "}";
            
            try (FileWriter writer = new FileWriter(tempConfigFile)) {
                writer.write(json);
            }

            DatabaseConfig config = DatabaseConfigLoader.load();
            assertNotNull(config);
            assertEquals(DatabaseType.MYSQL, config.getType());
            // Password should be resolved to the system PATH
            assertFalse(config.getMysql().getPassword().isEmpty());
            assertNotEquals("${ENV:PATH}", config.getMysql().getPassword());
            assertEquals(System.getenv("PATH"), config.getMysql().getPassword());
        }
    }

    @Test
    public void testEnvVarResolutionMissingThrows() throws Exception {
        try (MockedStatic<ResourceUtil> mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class)) {
            mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

            String json = "{\n" +
                          "  \"enabled\": true,\n" +
                          "  \"type\": \"MYSQL\",\n" +
                          "  \"mysql\": {\n" +
                          "    \"host\": \"127.0.0.1\",\n" +
                          "    \"port\": 3306,\n" +
                          "    \"database\": \"test\",\n" +
                          "    \"username\": \"test\",\n" +
                          "    \"password\": \"${ENV:NON_EXISTENT_ENV_VAR_12345}\"\n" +
                          "  }\n" +
                          "}";
            
            try (FileWriter writer = new FileWriter(tempConfigFile)) {
                writer.write(json);
            }

            assertThrows(DatabaseException.class, DatabaseConfigLoader::load);
        }
    }

    @Test
    public void testConfigValidationInvalidValues() {
        DatabaseConfig config = new DatabaseConfig();
        
        // Host empty on MySQL
        config.setType(DatabaseType.MYSQL);
        config.getMysql().setHost("");
        assertThrows(DatabaseException.class, () -> DatabaseConfigValidator.validateAndSanitize(config));
        
        // Negative connection timeout
        config.getMysql().setHost("127.0.0.1");
        config.getPool().setConnectionTimeoutMs(-1);
        assertThrows(DatabaseException.class, () -> DatabaseConfigValidator.validateAndSanitize(config));
        
        // Negative queue capacity
        config.getPool().setConnectionTimeoutMs(5000);
        config.getExecutor().setQueueCapacity(-5);
        assertThrows(DatabaseException.class, () -> DatabaseConfigValidator.validateAndSanitize(config));
    }

    @Test
    public void testSQLiteLifecycleAndOperations() throws Exception {
        try (MockedStatic<ResourceUtil> mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class)) {
            mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

            // Create customized database.json pointing to our temp SQLite database file
            String json = "{\n" +
                          "  \"enabled\": true,\n" +
                          "  \"required\": false,\n" +
                          "  \"type\": \"SQLITE\",\n" +
                          "  \"sqlite\": {\n" +
                          "    \"file\": \"" + tempDbFile.getAbsolutePath().replace("\\", "/") + "\",\n" +
                          "    \"wal\": true,\n" +
                          "    \"foreignKeys\": true,\n" +
                          "    \"busyTimeoutMs\": 5000\n" +
                          "  },\n" +
                          "  \"pool\": {\n" +
                          "    \"maximumPoolSize\": 10,\n" + // SQLite limit overrides this to 1
                          "    \"minimumIdle\": 1,\n" +
                          "    \"connectionTimeoutMs\": 5000,\n" +
                          "    \"validationTimeoutMs\": 3000,\n" +
                          "    \"idleTimeoutMs\": 60000,\n" +
                          "    \"maxLifetimeMs\": 120000,\n" +
                          "    \"keepaliveTimeMs\": 30000\n" +
                          "  },\n" +
                          "  \"executor\": {\n" +
                          "    \"threads\": 4,\n" + // SQLite limit overrides this to 1
                          "    \"queueCapacity\": 100,\n" +
                          "    \"shutdownTimeoutSeconds\": 5\n" +
                          "  },\n" +
                          "  \"migrations\": {\n" +
                          "    \"enabled\": true,\n" +
                          "    \"validateChecksums\": true,\n" +
                          "    \"failOnChecksumMismatch\": true\n" +
                          "  },\n" +
                          "  \"debug\": {\n" +
                          "    \"logQueries\": true,\n" +
                          "    \"logSlowQueries\": true,\n" +
                          "    \"slowQueryThresholdMs\": 100\n" +
                          "  }\n" +
                          "}";
            
            try (FileWriter writer = new FileWriter(tempConfigFile)) {
                writer.write(json);
            }

            DatabaseManager manager = DatabaseManager.getInstance();
            
            // Initialization
            manager.initialize();
            
            assertTrue(manager.isReady());
            assertEquals(DatabaseState.READY, manager.getState());
            assertEquals(DatabaseType.SQLITE, manager.getType());
            assertTrue(tempDbFile.exists());
            
            // Enforced SQLite limits: maxPoolSize=1, executorThreads=1
            assertEquals(1, manager.getConfig().getPool().getMaximumPoolSize());
            assertEquals(1, manager.getConfig().getExecutor().getThreads());
            
            // Idempotent check
            manager.initialize();
            assertEquals(DatabaseState.READY, manager.getState());

            DatabaseExecutor executor = manager.getExecutor();
            assertNotNull(executor);

            // Test Ping
            assertTrue(executor.ping().get(2, TimeUnit.SECONDS));

            // Test executeUpdate and queryOne
            // Insert metadata
            int rowsAffected = executor.executeUpdate("INSERT INTO bbe_metadata (meta_key, meta_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)", 
                stmt -> {
                    stmt.setString(1, "test_key");
                    stmt.setString(2, "test_value");
                }
            ).get(2, TimeUnit.SECONDS);
            assertEquals(1, rowsAffected);

            // Query metadata
            String val = executor.queryOne("SELECT meta_value FROM bbe_metadata WHERE meta_key = ?",
                stmt -> stmt.setString(1, "test_key"),
                rs -> rs.getString("meta_value")
            ).get(2, TimeUnit.SECONDS);
            assertEquals("test_value", val);

            // Test Query list
            List<String> list = executor.queryList("SELECT meta_key FROM bbe_metadata",
                null,
                rs -> rs.getString("meta_key")
            ).get(2, TimeUnit.SECONDS);
            assertEquals(1, list.size());
            assertEquals("test_key", list.get(0));

            // Test executeBatch
            List<com.pedrodalben.bigbangessentials.database.execution.StatementBinder> batchBinders = new ArrayList<>();
            batchBinders.add(stmt -> {
                stmt.setString(1, "batch_key_1");
                stmt.setString(2, "batch_val_1");
            });
            batchBinders.add(stmt -> {
                stmt.setString(1, "batch_key_2");
                stmt.setString(2, "batch_val_2");
            });
            
            int[] batchResults = executor.executeBatch(
                "INSERT INTO bbe_metadata (meta_key, meta_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                batchBinders
            ).get(2, TimeUnit.SECONDS);
            assertEquals(2, batchResults.length);
            assertEquals(1, batchResults[0]);
            assertEquals(1, batchResults[1]);

            // Test Transaction Commit
            String transactionResult = executor.transaction(conn -> {
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO bbe_metadata (meta_key, meta_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                    stmt.setString(1, "tx_commit_key");
                    stmt.setString(2, "tx_commit_val");
                    stmt.executeUpdate();
                }
                return "TxSuccess";
            }).get(2, TimeUnit.SECONDS);
            assertEquals("TxSuccess", transactionResult);
            
            // Verify transaction commit worked
            String txVal = executor.queryOne("SELECT meta_value FROM bbe_metadata WHERE meta_key = ?",
                stmt -> stmt.setString(1, "tx_commit_key"),
                rs -> rs.getString("meta_value")
            ).get(2, TimeUnit.SECONDS);
            assertEquals("tx_commit_val", txVal);

            // Test Transaction Rollback
            try {
                executor.transaction(conn -> {
                    try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO bbe_metadata (meta_key, meta_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                        stmt.setString(1, "tx_rollback_key");
                        stmt.setString(2, "tx_rollback_val");
                        stmt.executeUpdate();
                    }
                    throw new RuntimeException("Force rollback in test");
                }).get(2, TimeUnit.SECONDS);
                fail("Transaction should have failed");
            } catch (ExecutionException e) {
                // Expected rollback exception
                assertTrue(e.getCause() instanceof RuntimeException);
                assertEquals("Force rollback in test", e.getCause().getMessage());
            }

            // Verify rollback worked (no key in db)
            String rollbackVal = executor.queryOne("SELECT meta_value FROM bbe_metadata WHERE meta_key = ?",
                stmt -> stmt.setString(1, "tx_rollback_key"),
                rs -> rs.getString("meta_value")
            ).get(2, TimeUnit.SECONDS);
            assertNull(rollbackVal);

            // Test health check
            DatabaseHealth health = manager.getHealth();
            assertTrue(health.connected());
            assertEquals(2L, health.schemaVersion());
            assertEquals(DatabaseState.READY, health.state());

            // Test metrics snapshot
            DatabaseMetricsSnapshot metrics = manager.getMetricsSnapshot();
            assertTrue(metrics.executedQueries() > 0);
            assertEquals(1, metrics.failedQueries()); // Rollback transaction query counts as failed/aborted task

            // Shutdown test
            manager.shutdown();
            assertEquals(DatabaseState.STOPPED, manager.getState());
            assertFalse(manager.isReady());
            
            // Double shutdown is idempotent
            manager.shutdown();
            assertEquals(DatabaseState.STOPPED, manager.getState());
            
            // Try operations when shut down
            assertThrows(DatabaseUnavailableException.class, manager::getExecutor);
        }
    }

    @Test
    public void testDatabaseUnavailablePolicyRequiredFalse() throws Exception {
        try (MockedStatic<ResourceUtil> mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class)) {
            mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

            // Invalid SQLite file path that is directory or invalid URL
            String json = "{\n" +
                          "  \"enabled\": true,\n" +
                          "  \"required\": false,\n" +
                          "  \"type\": \"SQLITE\",\n" +
                          "  \"sqlite\": {\n" +
                          "    \"file\": \"/invalid/directory/path/here/db.db\"\n" +
                          "  }\n" +
                          "}";
            try (FileWriter writer = new FileWriter(tempConfigFile)) {
                writer.write(json);
            }

            DatabaseManager manager = DatabaseManager.getInstance();
            
            // Since required=false, initialization should log errors, mark FAILED, and return gracefully
            assertDoesNotThrow(manager::initialize);
            assertEquals(DatabaseState.FAILED, manager.getState());
            assertFalse(manager.isReady());
        }
    }

    @Test
    public void testDatabaseUnavailablePolicyRequiredTrue() throws Exception {
        try (MockedStatic<ResourceUtil> mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class)) {
            mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

            // Invalid SQLite configuration with required=true
            String json = "{\n" +
                          "  \"enabled\": true,\n" +
                          "  \"required\": true,\n" +
                          "  \"type\": \"SQLITE\",\n" +
                          "  \"sqlite\": {\n" +
                          "    \"file\": \"/invalid/directory/path/here/db.db\"\n" +
                          "  }\n" +
                          "}";
            try (FileWriter writer = new FileWriter(tempConfigFile)) {
                writer.write(json);
            }

            DatabaseManager manager = DatabaseManager.getInstance();
            
            // Since required=true, initialization must throw DatabaseException
            assertThrows(DatabaseException.class, manager::initialize);
            assertEquals(DatabaseState.FAILED, manager.getState());
        }
    }

    @Test
    public void testSlowQueryRecording() throws Exception {
        try (MockedStatic<ResourceUtil> mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class)) {
            mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

            // Set slowQueryThresholdMs to 0 so all queries are captured as slow
            String json = "{\n" +
                          "  \"enabled\": true,\n" +
                          "  \"type\": \"SQLITE\",\n" +
                          "  \"sqlite\": {\n" +
                          "    \"file\": \"" + tempDbFile.getAbsolutePath().replace("\\", "/") + "\"\n" +
                          "  },\n" +
                          "  \"debug\": {\n" +
                          "    \"logSlowQueries\": true,\n" +
                          "    \"slowQueryThresholdMs\": 0\n" +
                          "  }\n" +
                          "}";
            try (FileWriter writer = new FileWriter(tempConfigFile)) {
                writer.write(json);
            }

            DatabaseManager manager = DatabaseManager.getInstance();
            manager.initialize();
            
            // Execute any query
            manager.getExecutor().ping().get(2, TimeUnit.SECONDS);

            DatabaseMetricsSnapshot metrics = manager.getMetricsSnapshot();
            // Since threshold was 0, it should be captured as slow query
            assertTrue(metrics.slowQueries() > 0);
        }
    }

    @Test
    public void testThreadPoolRejectionPolicy() throws Exception {
        try (MockedStatic<ResourceUtil> mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class)) {
            mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

            // Configure thread pool capacity of 1, executor threads of 1
            String json = "{\n" +
                          "  \"enabled\": true,\n" +
                          "  \"type\": \"SQLITE\",\n" +
                          "  \"sqlite\": {\n" +
                          "    \"file\": \"" + tempDbFile.getAbsolutePath().replace("\\", "/") + "\"\n" +
                          "  },\n" +
                          "  \"executor\": {\n" +
                          "    \"threads\": 1,\n" +
                          "    \"queueCapacity\": 1,\n" +
                          "    \"shutdownTimeoutSeconds\": 5\n" +
                          "  }\n" +
                          "}";
            try (FileWriter writer = new FileWriter(tempConfigFile)) {
                writer.write(json);
            }

            DatabaseManager manager = DatabaseManager.getInstance();
            manager.initialize();
            DatabaseExecutor executor = manager.getExecutor();

            // Submit a sleeping query to occupy the single thread
            CompletableFuture<Void> sleepFuture = executor.transaction(conn -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });

            // Submit second query to fill the queue (capacity 1)
            CompletableFuture<Boolean> queuedFuture = executor.ping();

            // Submit third query - this should trigger the rejection policy and fail immediately
            CompletableFuture<Boolean> rejectedFuture = executor.ping();

            try {
                rejectedFuture.get(2, TimeUnit.SECONDS);
                fail("Should have been rejected");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof DatabaseException);
                assertTrue(e.getCause().getMessage().contains("Database executor queue is full"));
            }

            // Verify rejected metric increments
            assertTrue(manager.getMetricsSnapshot().rejectedTasks() > 0);

            // Clean up threads
            sleepFuture.get(2, TimeUnit.SECONDS);
            queuedFuture.get(2, TimeUnit.SECONDS);
        }
    }
}
