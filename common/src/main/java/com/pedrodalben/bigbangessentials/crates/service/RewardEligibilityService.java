package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.repository.RewardRollStateRepository;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central service for checking reward eligibility.
 * Validates permissions, global/player limits, and atomic reservation.
 */
public class RewardEligibilityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardEligibilityService.class);

    private final RewardRollStateRepository rollStateRepo;

    public RewardEligibilityService(RewardRollStateRepository rollStateRepo) {
        this.rollStateRepo = rollStateRepo;
    }

    public boolean isEligible(CrateReward reward, ServerPlayer player,
                              Map<String, Integer> playerRewardCounts,
                              Map<String, Integer> globalRewardCounts) {
        Set<String> permissions = getPlayerPermissions(player);
        return reward.isEligible(permissions, playerRewardCounts, globalRewardCounts);
    }

    private Set<String> getPlayerPermissions(ServerPlayer player) {
        Set<String> permissions = new HashSet<>();
        UUID playerId = player.getUUID();

        String[] nodesToCheck = {
            "bigbangessentials.crates.reward.*",
            "bigbangessentials.crates.reward." + player.getName().getString()
        };

        for (String node : nodesToCheck) {
            if (PermissionAPI.hasPermission(playerId, node)) {
                permissions.add(node);
            }
        }

        return permissions;
    }

    public Map<String, Integer> getGlobalCounts(CrateReward[] rewards) {
        Map<String, Integer> globalCounts = new HashMap<>();
        for (CrateReward reward : rewards) {
            rollStateRepo.findByRewardId(reward.getId()).ifPresent(rs ->
                globalCounts.put(reward.getId(), rs.getGlobalCount()));
        }
        return globalCounts;
    }

    public Map<String, Integer> getPlayerCounts(CrateReward[] rewards, UUID playerId) {
        Map<String, Integer> playerCounts = new HashMap<>();
        for (CrateReward reward : rewards) {
            int pc = rollStateRepo.getPlayerCount(reward.getId(), playerId);
            if (pc > 0) {
                playerCounts.put(reward.getId(), pc);
            }
        }
        return playerCounts;
    }
}
