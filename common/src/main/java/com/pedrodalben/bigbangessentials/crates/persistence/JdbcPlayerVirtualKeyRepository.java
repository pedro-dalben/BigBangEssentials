package com.pedrodalben.bigbangessentials.crates.persistence;

import com.pedrodalben.bigbangessentials.crates.domain.PlayerVirtualKeyBalance;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerVirtualKeyRepository;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseUnavailableException;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcPlayerVirtualKeyRepository extends JdbcRepository implements PlayerVirtualKeyRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPlayerVirtualKeyRepository.class);

    private static final String TABLE = "crate_player_keys";
    private static final String SELECT_BY_PLAYER_AND_KEY = "SELECT * FROM " + TABLE + " WHERE player_uuid = ? AND key_id = ?";
    private static final String SELECT_BY_PLAYER = "SELECT * FROM " + TABLE + " WHERE player_uuid = ?";
    private static final String SELECT_BY_KEY = "SELECT * FROM " + TABLE + " WHERE key_id = ?";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE;
    private static final String INSERT = "INSERT INTO " + TABLE + " (player_uuid, key_id, amount, updated_at) VALUES (?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE " + TABLE + " SET amount = ?, updated_at = ? WHERE player_uuid = ? AND key_id = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE player_uuid = ? AND key_id = ?";
    private static final String DELETE_BY_PLAYER = "DELETE FROM " + TABLE + " WHERE player_uuid = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private boolean tableCreated = false;

    private final RowMapper<PlayerVirtualKeyBalance> MAPPER = (rs) -> {
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        String keyId = rs.getString("key_id");
        int amount = rs.getInt("amount");
        return new PlayerVirtualKeyBalance(playerId, keyId, amount);
    };

    public JdbcPlayerVirtualKeyRepository() {
        ensureTable();
    }

    private synchronized void ensureTable() {
        if (tableCreated) return;
        try {
            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "key_id VARCHAR(64) NOT NULL, " +
                "amount INT NOT NULL DEFAULT 0, " +
                "updated_at BIGINT NOT NULL, " +
                "PRIMARY KEY (player_uuid, key_id)" +
                ")", null).join();
            tableCreated = true;
            LOGGER.debug("Ensured table {} exists", TABLE);
        } catch (DatabaseUnavailableException e) {
            LOGGER.warn("Database not ready yet, table {} deferred until first use", TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create table {}: {}", TABLE, e.getMessage(), e);
        }
    }

    @Override
    public Optional<PlayerVirtualKeyBalance> findByPlayerAndKey(UUID playerId, String keyId) {
        try {
            return getDatabase().querySingle(SELECT_BY_PLAYER_AND_KEY,
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, keyId);
                },
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find player key: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<PlayerVirtualKeyBalance> findByPlayer(UUID playerId) {
        try {
            return getDatabase().queryList(SELECT_BY_PLAYER,
                stmt -> stmt.setString(1, playerId.toString()),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find keys by player: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<PlayerVirtualKeyBalance> findByKey(String keyId) {
        try {
            return getDatabase().queryList(SELECT_BY_KEY,
                stmt -> stmt.setString(1, keyId),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find keys by key ID: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<PlayerVirtualKeyBalance> findAll() {
        try {
            return getDatabase().queryList(SELECT_ALL, null, MAPPER).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find all player keys: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public PlayerVirtualKeyBalance save(PlayerVirtualKeyBalance balance) {
        try {
            int updated = getDatabase().executeUpdate(UPDATE,
                stmt -> {
                    stmt.setInt(1, balance.getAmount());
                    stmt.setLong(2, System.currentTimeMillis());
                    stmt.setString(3, balance.getPlayerId().toString());
                    stmt.setString(4, balance.getKeyId());
                }
            ).join();
            if (updated == 0) {
                getDatabase().executeUpdate(INSERT,
                    stmt -> {
                        stmt.setString(1, balance.getPlayerId().toString());
                        stmt.setString(2, balance.getKeyId());
                        stmt.setInt(3, balance.getAmount());
                        stmt.setLong(4, System.currentTimeMillis());
                    }
                ).join();
            }
            return balance;
        } catch (Exception e) {
            LOGGER.error("Failed to save player key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save player key", e);
        }
    }

    @Override
    public void delete(PlayerVirtualKeyBalance balance) {
        try {
            getDatabase().executeUpdate(DELETE,
                stmt -> {
                    stmt.setString(1, balance.getPlayerId().toString());
                    stmt.setString(2, balance.getKeyId());
                }
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete player key: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteByPlayer(UUID playerId) {
        try {
            getDatabase().executeUpdate(DELETE_BY_PLAYER,
                stmt -> stmt.setString(1, playerId.toString())
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete keys by player: {}", e.getMessage(), e);
        }
    }

    @Override
    public long count() {
        try {
            return getDatabase().querySingle(COUNT, null, (rs) -> rs.getLong(1)).join().orElse(0L);
        } catch (Exception e) {
            LOGGER.error("Failed to count player keys: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public boolean decrementBalance(UUID playerId, String keyId, int amount) {
        try {
            String sql = "UPDATE " + TABLE + " SET amount = amount - ?, updated_at = ? " +
                         "WHERE player_uuid = ? AND key_id = ? AND amount >= ?";
            int updated = getDatabase().executeUpdate(sql,
                stmt -> {
                    stmt.setInt(1, amount);
                    stmt.setLong(2, System.currentTimeMillis());
                    stmt.setString(3, playerId.toString());
                    stmt.setString(4, keyId);
                    stmt.setInt(5, amount);
                }
            ).join();
            return updated > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to atomically decrement key balance: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int incrementBalance(UUID playerId, String keyId, int amount) {
        try {
            String sql;
            if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
                sql = "INSERT INTO " + TABLE + " (player_uuid, key_id, amount, updated_at) VALUES (?, ?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount), updated_at = VALUES(updated_at)";
            } else {
                sql = "INSERT INTO " + TABLE + " (player_uuid, key_id, amount, updated_at) VALUES (?, ?, ?, ?) " +
                      "ON CONFLICT(player_uuid, key_id) DO UPDATE SET amount = amount + ?, updated_at = ?";
            }
            getDatabase().executeUpdate(sql,
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, keyId);
                    stmt.setInt(3, amount);
                    stmt.setLong(4, System.currentTimeMillis());
                    if (DatabaseManager.getInstance().getType() != DatabaseType.MYSQL) {
                        stmt.setInt(5, amount);
                        stmt.setLong(6, System.currentTimeMillis());
                    }
                }
            ).join();
            return findByPlayerAndKey(playerId, keyId)
                .map(PlayerVirtualKeyBalance::getAmount)
                .orElse(0);
        } catch (Exception e) {
            LOGGER.error("Failed to atomically increment key balance: {}", e.getMessage(), e);
            return -1;
        }
    }
}
