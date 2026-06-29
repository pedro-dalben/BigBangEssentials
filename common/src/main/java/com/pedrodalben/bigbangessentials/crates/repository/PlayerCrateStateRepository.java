package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.PlayerCrateState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerCrateStateRepository {
    Optional<PlayerCrateState> findByPlayerAndCrate(UUID playerId, String crateId);
    List<PlayerCrateState> findByPlayer(UUID playerId);
    List<PlayerCrateState> findByCrate(String crateId);
    List<PlayerCrateState> findAll();
    PlayerCrateState save(PlayerCrateState state);
    void delete(PlayerCrateState state);
    void deleteByPlayer(UUID playerId);
    long count();

    void startCooldown(UUID playerId, String crateId, long cooldownUntil);
    PlayerCrateState recordOpening(UUID playerId, String crateId);
    void clearCooldown(UUID playerId, String crateId);
}
