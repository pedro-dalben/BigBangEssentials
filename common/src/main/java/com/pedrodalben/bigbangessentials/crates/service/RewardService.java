package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.RewardRollState;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcRewardRollStateRepository;
import com.pedrodalben.bigbangessentials.crates.repository.RewardRollStateRepository;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class RewardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardService.class);
    private static final RewardService INSTANCE = new RewardService();
    private static final Random RANDOM = new Random();

    private final RewardRollStateRepository rollStateRepo;

    private RewardService() {
        this.rollStateRepo = new JdbcRewardRollStateRepository();
    }

    public static RewardService getInstance() {
        return INSTANCE;
    }

    /**
     * Stage 1: Select a rarity by weighted random.
     */
    public CrateRarity selectRarityByWeight(CrateDefinition crate) {
        List<CrateRarity> activeRarities = crate.getRarities().stream()
            .filter(CrateRarity::isActive)
            .filter(r -> crate.hasRewardsForRarity(r.getId()))
            .toList();

        if (activeRarities.isEmpty()) return null;

        double totalWeight = activeRarities.stream()
            .mapToDouble(CrateRarity::getWeight)
            .sum();

        if (totalWeight <= 0) return null;

        double roll = RANDOM.nextDouble() * totalWeight;
        double cumulative = 0;

        for (CrateRarity rarity : activeRarities) {
            cumulative += rarity.getWeight();
            if (roll <= cumulative) {
                return rarity;
            }
        }

        return activeRarities.get(activeRarities.size() - 1);
    }

    /**
     * Stage 2: Select a reward within rarity by weight.
     */
    public CrateReward selectRewardByWeight(CrateDefinition crate, String rarityId) {
        List<CrateReward> eligibleRewards = crate.getRewardsByRarity(rarityId).stream()
            .filter(CrateReward::isActive)
            .filter(r -> !r.isMilestoneOnly())
            .toList();

        if (eligibleRewards.isEmpty()) return null;

        double totalWeight = eligibleRewards.stream()
            .mapToDouble(CrateReward::getWeight)
            .sum();

        if (totalWeight <= 0) return null;

        double roll = RANDOM.nextDouble() * totalWeight;
        double cumulative = 0;

        for (CrateReward reward : eligibleRewards) {
            cumulative += reward.getWeight();
            if (roll <= cumulative) {
                return reward;
            }
        }

        return eligibleRewards.get(eligibleRewards.size() - 1);
    }

    /**
     * Checks if a reward is eligible for the given player based on limits and permissions.
     */
    public boolean isEligible(CrateReward reward, ServerPlayer player,
                              Map<String, Integer> playerRewardCounts,
                              Map<String, Integer> globalRewardCounts) {
        Set<String> permissions = getPlayerPermissions(player);
        return reward.isEligible(permissions, playerRewardCounts, globalRewardCounts);
    }

    /**
     * Gets player permissions relevant for crate eligibility checks.
     */
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

    /**
     * Deliver a reward to the player.
     */
    public void deliverReward(ServerPlayer player, CrateReward reward) {
        if (reward.getType().name().equals("ITEM")) {
            for (ItemStack item : reward.getItems()) {
                if (!item.isEmpty()) {
                    giveItemToPlayer(player, item.copy());
                }
            }
        } else if (reward.getType().name().equals("COMMAND")) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                CommandSourceStack source = server.createCommandSourceStack();
                for (String command : reward.getCommands()) {
                    String resolved = command
                        .replace("{player}", player.getName().getString())
                        .replace("{uuid}", player.getUUID().toString());
                    try {
                        server.getCommands().performPrefixedCommand(source, resolved);
                    } catch (Exception e) {
                        LOGGER.error("Failed to execute reward command: {}", resolved, e);
                    }
                }
            }
        }

        recordRewardRoll(reward, player.getUUID());
    }

    /**
     * Give item to player with overflow protection (drops on ground if inventory full).
     */
    private void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        if (!inventory.add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Get eligible rewards for a player considering all limits.
     */
    public List<CrateReward> getEligibleRewardsForPlayer(CrateDefinition crate, ServerPlayer player) {
        Map<String, Integer> globalCounts = new HashMap<>();
        Map<String, Integer> playerCounts = new HashMap<>();

        for (CrateReward reward : crate.getRewards()) {
            RewardRollState rollState = rollStateRepo.findByRewardId(reward.getId()).orElse(null);
            if (rollState != null) {
                globalCounts.put(reward.getId(), rollState.getGlobalCount());
                playerCounts.put(reward.getId(), rollState.getPlayerCount(player.getUUID()));
            }
        }

        Set<String> permissions = getPlayerPermissions(player);

        return crate.getRewards().stream()
            .filter(CrateReward::isActive)
            .filter(r -> {
                CrateRarity rarity = crate.getRarity(r.getRarityId());
                return rarity != null && rarity.isActive();
            })
            .filter(r -> r.isEligible(permissions, playerCounts, globalCounts))
            .toList();
    }

    /**
     * Record a reward roll for limit tracking (thread-safe).
     */
    private synchronized void recordRewardRoll(CrateReward reward, UUID playerId) {
        RewardRollState rollState = rollStateRepo.findByRewardId(reward.getId())
            .orElse(new RewardRollState(reward.getId()));

        rollState.incrementGlobal();
        rollState.incrementPlayer(playerId);
        rollStateRepo.save(rollState);
    }

    /**
     * Roll a reward for the player, considering eligibility limits (permissions, global/player limits).
     * Returns null if no eligible reward is available.
     */
    public CrateReward rollEligibleReward(CrateDefinition crate, ServerPlayer player) {
        Map<String, Integer> globalCounts = new HashMap<>();
        Map<String, Integer> playerCounts = new HashMap<>();

        for (CrateReward reward : crate.getRewards()) {
            RewardRollState rollState = rollStateRepo.findByRewardId(reward.getId()).orElse(null);
            if (rollState != null) {
                globalCounts.put(reward.getId(), rollState.getGlobalCount());
                playerCounts.put(reward.getId(), rollState.getPlayerCount(player.getUUID()));
            }
        }

        CrateRarity selectedRarity = selectRarityByWeight(crate);
        if (selectedRarity == null) return null;

        List<CrateReward> eligible = crate.getRewardsByRarity(selectedRarity.getId()).stream()
            .filter(CrateReward::isActive)
            .filter(r -> !r.isMilestoneOnly())
            .filter(r -> isEligible(r, player, playerCounts, globalCounts))
            .toList();

        if (eligible.isEmpty()) return null;

        double totalWeight = eligible.stream()
            .mapToDouble(CrateReward::getWeight)
            .sum();

        if (totalWeight <= 0) return null;

        double roll = RANDOM.nextDouble() * totalWeight;
        double cumulative = 0;

        for (CrateReward reward : eligible) {
            cumulative += reward.getWeight();
            if (roll <= cumulative) {
                return reward;
            }
        }

        return eligible.get(eligible.size() - 1);
    }

    public void reload() {
    }
}
