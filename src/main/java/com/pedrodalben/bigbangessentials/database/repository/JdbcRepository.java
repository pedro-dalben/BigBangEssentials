package com.pedrodalben.bigbangessentials.database.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;

/**
 * Base class for JDBC repository implementations.
 *
 * <p>Future domain repositories (e.g. Economy, Homes, Warps) should inherit from this class
 * and interact with the database using the provided {@link DatabaseExecutor}.</p>
 *
 * <p>Example future architecture:</p>
 * <pre>
 * public interface HomeStorage {
 *     Map&lt;String, TeleportLocation&gt; loadHomes(UUID playerId);
 *     void saveHome(UUID playerId, String name, TeleportLocation location);
 *     boolean deleteHome(UUID playerId, String name);
 * }
 *
 * public class JdbcHomeStorage extends JdbcRepository implements HomeStorage {
 *     public JdbcHomeStorage() {
 *         super();
 *     }
 *     // Implement loadHomes, saveHome, deleteHome using database.queryList/executeUpdate
 * }
 * </pre>
 */
public abstract class JdbcRepository {
    protected final DatabaseExecutor database;

    protected JdbcRepository() {
        this.database = DatabaseManager.getInstance().getExecutor();
    }

    protected JdbcRepository(DatabaseExecutor database) {
        this.database = database;
    }
}
