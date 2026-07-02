package com.pedrodalben.bigbangessentials.crates.persistence;

import com.pedrodalben.bigbangessentials.crates.repository.CrateMetricsRepository;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class JdbcCrateMetricsRepository extends JdbcRepository implements CrateMetricsRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcCrateMetricsRepository.class);

    private static final String TABLE = "crate_metrics";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE;
    private static final String SELECT_BY_KEY = "SELECT metric_value FROM " + TABLE + " WHERE metric_key = ?";
    private static final String DELETE_ALL = "DELETE FROM " + TABLE;

    private boolean tableCreated = false;

    public JdbcCrateMetricsRepository() {
        ensureTable();
    }

    private synchronized void ensureTable() {
        if (tableCreated) return;
        try {
            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "metric_key VARCHAR(128) NOT NULL, " +
                "metric_value BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (metric_key)" +
                ")", null).join();
            tableCreated = true;
            LOGGER.debug("Ensured table {} exists", TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create table {}: {}", TABLE, e.getMessage(), e);
        }
    }

    @Override
    public long incrementCounter(String metricKey) {
        return addCounter(metricKey, 1);
    }

    @Override
    public long addCounter(String metricKey, long amount) {
        if (amount <= 0) return getCounter(metricKey);
        try {
            getDatabase().executeUpdate(upsertAmountSql(), stmt -> {
                stmt.setString(1, metricKey);
                stmt.setLong(2, amount);
                if (DatabaseManager.getInstance().getType() != DatabaseType.MYSQL) {
                    stmt.setLong(3, amount);
                }
            }).join();
            return getDatabase().querySingle(SELECT_BY_KEY,
                stmt -> stmt.setString(1, metricKey),
                (rs) -> rs.getLong("metric_value")
            ).join().orElse(0L);
        } catch (Exception e) {
            LOGGER.error("Failed to add metric '{}': {}", metricKey, e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public long getCounter(String metricKey) {
        try {
            return getDatabase().querySingle(SELECT_BY_KEY,
                stmt -> stmt.setString(1, metricKey),
                (rs) -> rs.getLong("metric_value")
            ).join().orElse(0L);
        } catch (Exception e) {
            LOGGER.error("Failed to get metric '{}': {}", metricKey, e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public Map<String, Long> getAllCounters() {
        try {
            Map<String, Long> result = new LinkedHashMap<>();
            getDatabase().queryList(SELECT_ALL, null,
                (RowMapper<Void>) (rs) -> {
                    result.put(rs.getString("metric_key"), rs.getLong("metric_value"));
                    return null;
                }
            ).join();
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to get all metrics: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    @Override
    public void resetAll() {
        try {
            getDatabase().executeUpdate(DELETE_ALL, null).join();
            LOGGER.info("All metrics have been reset");
        } catch (Exception e) {
            LOGGER.error("Failed to reset metrics: {}", e.getMessage(), e);
        }
    }

    private String upsertAmountSql() {
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            return "INSERT INTO " + TABLE + " (metric_key, metric_value) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE metric_value = metric_value + VALUES(metric_value)";
        }
        return "INSERT INTO " + TABLE + " (metric_key, metric_value) VALUES (?, ?) "
            + "ON CONFLICT(metric_key) DO UPDATE SET metric_value = metric_value + ?";
    }
}
