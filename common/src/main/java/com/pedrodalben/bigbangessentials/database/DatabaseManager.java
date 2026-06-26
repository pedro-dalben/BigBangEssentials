package com.pedrodalben.bigbangessentials.database;

import com.zaxxer.hikari.HikariDataSource;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfig;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfigLoader;
import com.pedrodalben.bigbangessentials.database.datasource.MySqlDataSourceFactory;
import com.pedrodalben.bigbangessentials.database.datasource.SqliteDataSourceFactory;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.dialect.MySqlDialect;
import com.pedrodalben.bigbangessentials.database.dialect.SqliteDialect;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseException;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseUnavailableException;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import com.pedrodalben.bigbangessentials.database.metrics.DatabaseMetrics;
import com.pedrodalben.bigbangessentials.database.metrics.DatabaseMetricsSnapshot;
import com.pedrodalben.bigbangessentials.database.migration.LegacyJsonPlayerPreferencesImporter;
import com.pedrodalben.bigbangessentials.database.migration.MigrationManager;
import com.pedrodalben.bigbangessentials.database.migration.MigrationResult;
import com.pedrodalben.bigbangessentials.database.repository.JdbcPlayerPreferencesStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Main Database Manager. Handles the persistent connection pools, migrations, 
 * performance metrics, and also read-only database discovery for the web dashboard.
 */
public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);
    private static final DatabaseManager INSTANCE = new DatabaseManager();

    // Infrastructure state
    private volatile DatabaseState state = DatabaseState.NEW;
    private DatabaseConfig config;
    private DatabaseType type;
    private DatabaseDialect dialect;
    private HikariDataSource dataSource;
    private DatabaseExecutor executor;
    
    private final DatabaseMetrics metrics = new DatabaseMetrics();
    private final MigrationManager migrationManager = new MigrationManager();

    // Discovery state for Dashboard visualizer
    private final Map<String, DatabaseInfo> discoveredDatabases = new HashMap<>();
    private final Path configDirectory;
    private volatile boolean discovered = false;

    // Inert constructor
    private DatabaseManager() {
        this.configDirectory = Paths.get("config");
        // Completely inert, no I/O, no connection, no threads
    }

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the database subsystem.
     * This method is thread-safe and idempotent.
     *
     * @throws DatabaseException if initialization fails and config.required is true
     */
    public synchronized void initialize() throws DatabaseException {
        if (state != DatabaseState.NEW && state != DatabaseState.FAILED && state != DatabaseState.STOPPED) {
            LOGGER.info("DatabaseManager is already initialized or initializing. Current state: {}", state);
            return;
        }

        state = DatabaseState.STARTING;
        LOGGER.info("Starting DatabaseManager initialization...");

        try {
            // Load and resolve configuration
            config = DatabaseConfigLoader.load();
            
            if (!config.isEnabled()) {
                LOGGER.info("Database module is disabled in configuration.");
                state = DatabaseState.STOPPED;
                return;
            }

            type = config.getType();

            // Set dialect and create pool
            if (type == DatabaseType.SQLITE) {
                dialect = new SqliteDialect();
                dataSource = new SqliteDataSourceFactory().create(config);
            } else if (type == DatabaseType.MYSQL) {
                dialect = new MySqlDialect();
                dataSource = new MySqlDataSourceFactory().create(config);
            } else {
                throw new DatabaseException("Unsupported database type: " + type);
            }

            // Create executor
            executor = new DatabaseExecutor(dataSource, config, type, metrics);

            // Execute migrations
            state = DatabaseState.MIGRATING;
            try (Connection conn = dataSource.getConnection()) {
                migrationManager.runMigrations(conn, dialect, config);
            }

            // Perform initial health check
            DatabaseHealth health = getHealth();
            if (!health.connected()) {
                throw new DatabaseException("Initial health check failed: " + health.message());
            }

            state = DatabaseState.READY;
            runLegacyImport();
            LOGGER.info("DatabaseManager initialized successfully. Type: {}, State: {}", type, state);

        } catch (Throwable e) {
            state = DatabaseState.FAILED;
            LOGGER.error("CRITICAL: Failed to initialize DatabaseManager: {}", e.getMessage(), e);
            
            // Close resource partly initialized
            closeResourcesSafely();

            if (config != null && config.isRequired()) {
                throw new DatabaseException("Database is marked as REQUIRED and failed to initialize: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Shuts down the database subsystem, closing pools and executors.
     * Idempotent and thread-safe.
     */
    public synchronized void shutdown() {
        if (state == DatabaseState.STOPPED || state == DatabaseState.NEW) {
            return;
        }

        LOGGER.info("Shutting down DatabaseManager...");
        state = DatabaseState.STOPPING;

        closeResourcesSafely();

        state = DatabaseState.STOPPED;
        LOGGER.info("DatabaseManager shutdown complete.");
    }

    private void runLegacyImport() {
        try {
            var storage = new JdbcPlayerPreferencesStorage();
            var importer = new LegacyJsonPlayerPreferencesImporter(storage);
            var summary = importer.importAll().get(30, java.util.concurrent.TimeUnit.SECONDS);
            if (summary.total() > 0) {
                LOGGER.info("Legacy JSON import completed: {}", summary);
            }
        } catch (Exception e) {
            LOGGER.warn("Legacy JSON import skipped or failed: {}", e.getMessage());
        }
    }

    private void closeResourcesSafely() {
        if (executor != null) {
            try {
                executor.shutdown();
            } catch (Exception e) {
                LOGGER.error("Error shutting down database executor", e);
            }
            executor = null;
        }

        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (Exception e) {
                LOGGER.error("Error closing Hikari connection pool", e);
            }
            dataSource = null;
        }
    }

    /**
     * Executes pending migrations manually with concurrency protection.
     */
    public synchronized List<MigrationResult> runPendingMigrations() {
        if (!isReady()) {
            throw new DatabaseUnavailableException("Database is not ready to run migrations. Current state: " + state);
        }
        
        try (Connection conn = dataSource.getConnection()) {
            return migrationManager.runMigrations(conn, dialect, config);
        } catch (Exception e) {
            throw new DatabaseException("Failed to execute pending migrations", e);
        }
    }

    public boolean isReady() {
        return state == DatabaseState.READY;
    }

    public DatabaseState getState() {
        return state;
    }

    public DatabaseType getType() {
        return type;
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public DatabaseDialect getDialect() {
        return dialect;
    }

    public DatabaseMetrics getMetrics() {
        return metrics;
    }

    /**
     * Returns a snapshot of performance metrics.
     */
    public DatabaseMetricsSnapshot getMetricsSnapshot() {
        long queued = executor != null ? executor.getQueuedTaskCount() : 0;
        return metrics.getSnapshot(queued);
    }

    /**
     * Returns the connection pool status metrics to avoid exposing HikariDataSource.
     */
    public boolean isPoolActive() {
        return dataSource != null && !dataSource.isClosed();
    }

    public int getPoolActiveConnections() {
        return isPoolActive() ? dataSource.getHikariPoolMXBean().getActiveConnections() : 0;
    }

    public int getPoolIdleConnections() {
        return isPoolActive() ? dataSource.getHikariPoolMXBean().getIdleConnections() : 0;
    }

    public int getPoolTotalConnections() {
        return isPoolActive() ? dataSource.getHikariPoolMXBean().getTotalConnections() : 0;
    }

    /**
     * Get the active database connection pool. Expose package-private/protected internally.
     */
    protected HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Returns the asynchronous query executor.
     *
     * @return The database query executor
     * @throws DatabaseUnavailableException if the database is not in READY state
     */
    public DatabaseExecutor getExecutor() {
        if (!isReady()) {
            throw new DatabaseUnavailableException("Database execution is unavailable. State: " + state);
        }
        return executor;
    }

    /**
     * Synchronously checks database health and measures latency.
     */
    public DatabaseHealth getHealth() {
        if (dataSource == null || (state != DatabaseState.READY && state != DatabaseState.DEGRADED && state != DatabaseState.MIGRATING && state != DatabaseState.STARTING)) {
            return new DatabaseHealth(state, type, false, -1, 0, "Database is not connected or initialized", Instant.now());
        }

        long startTime = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(3); // 3 seconds timeout
            long latency = System.currentTimeMillis() - startTime;
            long schemaVersion = migrationManager.getCurrentVersion(conn);
            
            DatabaseState currentHealthState = state;
            if (state == DatabaseState.READY && !valid) {
                currentHealthState = DatabaseState.DEGRADED;
            }

            return new DatabaseHealth(
                currentHealthState,
                type,
                valid,
                latency,
                schemaVersion,
                valid ? "Database is healthy" : "Database connection ping failed",
                Instant.now()
            );
        } catch (Exception e) {
            return new DatabaseHealth(
                DatabaseState.DEGRADED,
                type,
                false,
                -1,
                0,
                "Failed to get connection: " + e.getMessage(),
                Instant.now()
            );
        }
    }

    /**
     * Generates a human-readable summary of database health.
     */
    public String getHealthSummary() {
        DatabaseHealth health = getHealth();
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("State: %s\n", health.state()));
        summary.append(String.format("Type: %s\n", health.type() != null ? health.type() : "UNKNOWN"));
        summary.append(String.format("Connected: %b\n", health.connected()));
        summary.append(String.format("Latency: %s\n", health.latencyMs() >= 0 ? health.latencyMs() + "ms" : "N/A"));
        summary.append(String.format("Schema Version: %d\n", health.schemaVersion()));
        summary.append(String.format("Status Message: %s\n", health.message()));
        
        if (isPoolActive()) {
            summary.append(String.format("Active Connections: %d\n", getPoolActiveConnections()));
            summary.append(String.format("Idle Connections: %d\n", getPoolIdleConnections()));
            summary.append(String.format("Total Connections: %d\n", getPoolTotalConnections()));
        }
        
        return summary.toString();
    }

    // =========================================================================
    // DISCOVERY & QUERYING FOR WEB DASHBOARD VISUALIZER (Retained API)
    // =========================================================================

    public static class DatabaseInfo {
        private final String id;
        private final String name;
        private final Path path;
        private final long size;
        private final Instant modified;
        
        public DatabaseInfo(String id, String name, Path path, long size, Instant modified) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.size = size;
            this.modified = modified;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public Path getPath() { return path; }
        public long getSize() { return size; }
        public Instant getModified() { return modified; }
    }
    
    public static class TableInfo {
        private final String name;
        private final String type;
        private final long rowCount;
        
        public TableInfo(String name, String type, long rowCount) {
            this.name = name;
            this.type = type;
            this.rowCount = rowCount;
        }
        
        public String getName() { return name; }
        public String getType() { return type; }
        public long getRowCount() { return rowCount; }
    }
    
    public static class ColumnInfo {
        private final int index;
        private final String name;
        private final String type;
        private final boolean notNull;
        private final String defaultValue;
        private final boolean primaryKey;
        
        public ColumnInfo(int index, String name, String type, boolean notNull, 
                          String defaultValue, boolean primaryKey) {
            this.index = index;
            this.name = name;
            this.type = type;
            this.notNull = notNull;
            this.defaultValue = defaultValue;
            this.primaryKey = primaryKey;
        }
        
        public int getIndex() { return index; }
        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isNotNull() { return notNull; }
        public String getDefaultValue() { return defaultValue; }
        public boolean isPrimaryKey() { return primaryKey; }
    }
    
    public static class QueryResult {
        private final List<String> columns;
        private final List<Map<String, Object>> rows;
        private final int totalRows;
        private final int page;
        private final int pageSize;
        private final long executionTime;
        
        public QueryResult(List<String> columns, List<Map<String, Object>> rows, 
                          int totalRows, int page, int pageSize, long executionTime) {
            this.columns = columns;
            this.rows = rows;
            this.totalRows = totalRows;
            this.page = page;
            this.pageSize = pageSize;
            this.executionTime = executionTime;
        }
        
        public List<String> getColumns() { return columns; }
        public List<Map<String, Object>> getRows() { return rows; }
        public int getTotalRows() { return totalRows; }
        public int getPage() { return page; }
        public int getPageSize() { return pageSize; }
        public long getExecutionTime() { return executionTime; }
    }
    
    public synchronized void discoverDatabases() {
        discoveredDatabases.clear();
        
        if (!Files.exists(configDirectory)) {
            LOGGER.warn("Config directory does not exist: {}", configDirectory);
            discovered = true;
            return;
        }
        
        try (Stream<Path> paths = Files.walk(configDirectory, 3)) {
            paths.filter(path -> path.toString().endsWith(".db") || 
                                 path.toString().endsWith(".sqlite") ||
                                 path.toString().endsWith(".sqlite3"))
                 .forEach(this::registerDatabase);
        } catch (IOException e) {
            LOGGER.error("Failed to discover databases", e);
        }
        
        discovered = true;
        LOGGER.info("Discovered {} database(s)", discoveredDatabases.size());
    }
    
    private void registerDatabase(Path dbPath) {
        try {
            if (!Files.exists(dbPath) || !Files.isRegularFile(dbPath)) {
                return;
            }
            
            String fileName = dbPath.getFileName().toString();
            String id = generateDatabaseId(dbPath);
            long size = Files.size(dbPath);
            Instant modified = Files.getLastModifiedTime(dbPath).toInstant();
            
            DatabaseInfo info = new DatabaseInfo(id, fileName, dbPath, size, modified);
            discoveredDatabases.put(id, info);
            
            LOGGER.debug("Registered database: {} at {}", fileName, dbPath);
            
        } catch (IOException e) {
            LOGGER.warn("Failed to register database: {}", dbPath, e);
        }
    }
    
    private String generateDatabaseId(Path path) {
        String relativePath = configDirectory.relativize(path).toString();
        return relativePath.replace("\\", "/").replace(".db", "")
                          .replace(".sqlite", "").replace(".sqlite3", "");
    }
    
    private void ensureDiscovered() {
        if (!discovered) {
            discoverDatabases();
        }
    }
    
    public synchronized List<DatabaseInfo> getDatabases() {
        ensureDiscovered();
        return new ArrayList<>(discoveredDatabases.values());
    }
    
    public synchronized DatabaseInfo getDatabase(String databaseId) {
        ensureDiscovered();
        return discoveredDatabases.get(databaseId);
    }
    
    private Connection getConnection(String databaseId) throws SQLException {
        DatabaseInfo db = getDatabase(databaseId);
        if (db == null) {
            throw new SQLException("Database not found: " + databaseId);
        }
        
        String url = "jdbc:sqlite:" + db.getPath().toString();
        Connection conn = DriverManager.getConnection(url);
        conn.setReadOnly(true); // Read-only for safety
        return conn;
    }
    
    public List<TableInfo> getTables(String databaseId) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        
        try (Connection conn = getConnection(databaseId)) {
            DatabaseMetaData meta = conn.getMetaData();
            
            try (ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableType = rs.getString("TABLE_TYPE");
                    
                    long rowCount = 0;
                    try (Statement stmt = conn.createStatement();
                         ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM \"" + tableName + "\"")) {
                        if (countRs.next()) {
                            rowCount = countRs.getLong(1);
                        }
                    } catch (SQLException e) {
                        LOGGER.warn("Failed to get row count for table: {}", tableName);
                    }
                    
                    tables.add(new TableInfo(tableName, tableType, rowCount));
                }
            }
        }
        
        return tables;
    }
    
    public List<ColumnInfo> getTableSchema(String databaseId, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        
        try (Connection conn = getConnection(databaseId);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(\"" + tableName + "\")")) {
            
            while (rs.next()) {
                int cid = rs.getInt("cid");
                String name = rs.getString("name");
                String type = rs.getString("type");
                boolean notNull = rs.getInt("notnull") == 1;
                String defaultValue = rs.getString("dflt_value");
                boolean pk = rs.getInt("pk") > 0;
                
                columns.add(new ColumnInfo(cid, name, type, notNull, defaultValue, pk));
            }
        }
        
        return columns;
    }
    
    public QueryResult executeQuery(String databaseId, String query, int page, int pageSize) 
            throws SQLException {
        
        String trimmedQuery = query.trim().toUpperCase();
        if (!trimmedQuery.startsWith("SELECT")) {
            throw new SQLException("Only SELECT queries are allowed");
        }
        
        if (trimmedQuery.contains("ATTACH") || trimmedQuery.contains("PRAGMA")) {
            throw new SQLException("Query contains forbidden operations");
        }
        
        long startTime = System.currentTimeMillis();
        
        try (Connection conn = getConnection(databaseId)) {
            int totalRows = 0;
            String countQuery = "SELECT COUNT(*) FROM (" + query + ")";
            try (Statement countStmt = conn.createStatement();
                 ResultSet countRs = countStmt.executeQuery(countQuery)) {
                if (countRs.next()) {
                    totalRows = countRs.getInt(1);
                }
            }
            
            int offset = (page - 1) * pageSize;
            String paginatedQuery = query + " LIMIT " + pageSize + " OFFSET " + offset;
            
            List<String> columns = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(paginatedQuery)) {
                
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(meta.getColumnName(i));
                }
                
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = meta.getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    rows.add(row);
                }
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return new QueryResult(columns, rows, totalRows, page, pageSize, executionTime);
        }
    }
    
    public String exportTableAsCSV(String databaseId, String tableName) throws SQLException {
        StringBuilder csv = new StringBuilder();
        
        try (Connection conn = getConnection(databaseId);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + tableName + "\"")) {
            
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) csv.append(",");
                csv.append(escapeCSV(meta.getColumnName(i)));
            }
            csv.append("\n");
            
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) csv.append(",");
                    Object value = rs.getObject(i);
                    csv.append(escapeCSV(value != null ? value.toString() : ""));
                }
                csv.append("\n");
            }
        }
        
        return csv.toString();
    }
    
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    public Map<String, Object> getDatabaseStats(String databaseId) throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        
        DatabaseInfo db = getDatabase(databaseId);
        if (db == null) {
            throw new SQLException("Database not found: " + databaseId);
        }
        
        stats.put("name", db.getName());
        stats.put("size", db.getSize());
        stats.put("sizeFormatted", formatFileSize(db.getSize()));
        stats.put("modified", db.getModified().toString());
        
        try (Connection conn = getConnection(databaseId)) {
            List<TableInfo> tables = getTables(databaseId);
            stats.put("tableCount", tables.size());
            
            long totalRows = tables.stream().mapToLong(TableInfo::getRowCount).sum();
            stats.put("totalRows", totalRows);
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT sqlite_version()")) {
                if (rs.next()) {
                    stats.put("sqliteVersion", rs.getString(1));
                }
            }
        }
        
        return stats;
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), pre);
    }
}
