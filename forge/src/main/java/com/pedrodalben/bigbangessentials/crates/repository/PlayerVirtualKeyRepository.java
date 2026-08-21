package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.PlayerVirtualKeyBalance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerVirtualKeyRepository {
    Optional<PlayerVirtualKeyBalance> findByPlayerAndKey(UUID playerId, String keyId);
    List<PlayerVirtualKeyBalance> findByPlayer(UUID playerId);
    List<PlayerVirtualKeyBalance> findByKey(String keyId);
    List<PlayerVirtualKeyBalance> findAll();
    PlayerVirtualKeyBalance save(PlayerVirtualKeyBalance balance);
    void delete(PlayerVirtualKeyBalance balance);
    void deleteByPlayer(UUID playerId);
    long count();

    /**
     * Atomically decrements a key balance if it's greater than or equal to the required amount.
     * 
     * @return true if the balance was successfully decremented, false otherwise.
     */
    boolean decrementBalance(UUID playerId, String keyId, int amount);

    /**
     * Atomically increments a key balance.
     * 
     * @return the new balance after increment.
     */
    int incrementBalance(UUID playerId, String keyId, int amount);
}

