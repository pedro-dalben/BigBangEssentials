package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.RewardRollState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RewardRollStateRepository {
    Optional<RewardRollState> findByRewardId(String rewardId);
    List<RewardRollState> findAll();
    RewardRollState save(RewardRollState state);
    void delete(RewardRollState state);
    void deleteByRewardId(String rewardId);
    long count();

    int incrementGlobalCount(String rewardId);
    int incrementPlayerCount(String rewardId, UUID playerId);
    int getPlayerCount(String rewardId, UUID playerId);
}
