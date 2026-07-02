package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.CrateModuleContext;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.repository.RewardRollStateRepository;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RewardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardService.class);
    private static RewardService instance;
    private static final Random RANDOM = new Random();

    private final RewardRollStateRepository rollStateRepo;
    private final RewardEligibilityService eligibilityService;

    public RewardService(RewardRollStateRepository rollStateRepo, RewardEligibilityService eligibilityService) {
        this.rollStateRepo = rollStateRepo;
        this.eligibilityService = eligibilityService;
    }

    public static RewardService getInstance() {
        if (instance == null) {
            RewardService ctx = CrateModuleContext.getInstance().getRewardService();
            if (ctx != null) {
                instance = ctx;
            } else {
                var rollRepo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcRewardRollStateRepository();
                instance = new RewardService(rollRepo, new RewardEligibilityService(rollRepo));
            }
        }
        return instance;
    }

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
                        .replace("{player}", player.getGameProfile().getName())
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

    private void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        if (!inventory.add(stack)) {
            player.drop(stack, false);
        }
    }

    public List<CrateReward> getEligibleRewardsForPlayer(CrateDefinition crate, ServerPlayer player) {
        Map<String, Integer> globalCounts = eligibilityService.getGlobalCounts(
            crate.getRewards().toArray(new CrateReward[0]));
        Map<String, Integer> playerCounts = eligibilityService.getPlayerCounts(
            crate.getRewards().toArray(new CrateReward[0]), player.getUUID());

        return crate.getRewards().stream()
            .filter(CrateReward::isActive)
            .filter(r -> {
                CrateRarity rarity = crate.getRarity(r.getRarityId());
                return rarity != null && rarity.isActive();
            })
            .filter(r -> eligibilityService.isEligible(r, player, playerCounts, globalCounts))
            .toList();
    }

    private void recordRewardRoll(CrateReward reward, UUID playerId) {
        rollStateRepo.incrementGlobalCount(reward.getId());
        rollStateRepo.incrementPlayerCount(reward.getId(), playerId);
    }

    public CrateReward rollEligibleReward(CrateDefinition crate, ServerPlayer player) {
        CrateReward[] allRewards = crate.getRewards().toArray(new CrateReward[0]);
        Map<String, Integer> globalCounts = eligibilityService.getGlobalCounts(allRewards);
        Map<String, Integer> playerCounts = eligibilityService.getPlayerCounts(allRewards, player.getUUID());

        CrateRarity selectedRarity = selectRarityByWeight(crate);
        if (selectedRarity == null) return null;

        List<CrateReward> eligible = crate.getRewardsByRarity(selectedRarity.getId()).stream()
            .filter(CrateReward::isActive)
            .filter(r -> !r.isMilestoneOnly())
            .filter(r -> eligibilityService.isEligible(r, player, playerCounts, globalCounts))
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
