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
}
