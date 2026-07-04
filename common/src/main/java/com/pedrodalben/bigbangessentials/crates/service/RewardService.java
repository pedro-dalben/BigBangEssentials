package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.CrateModuleContext;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.repository.RewardRollStateRepository;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
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
                    String cleaned = normalizeCommandString(command);
                    String resolved = cleaned
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

        executeWinEffects(player, reward);
    }

    private void executeWinEffects(ServerPlayer player, CrateReward reward) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        for (String effect : reward.getWinEffects()) {
            if (effect == null || effect.isBlank()) continue;
            int colonIdx = effect.indexOf(':');
            if (colonIdx <= 0) continue;
            String type = effect.substring(0, colonIdx).toUpperCase();
            String value = effect.substring(colonIdx + 1);
            if (value.isBlank()) continue;

            try {
                switch (type) {
                    case "SOUND" -> {
                        ResourceLocation soundKey = ResourceLocation.parse(value);
                        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundKey).orElse(null);
                        if (sound != null) {
                            level.playSound(null, pos, sound, SoundSource.MASTER, 1.0f, 1.0f);
                        }
                    }
                    case "FIREWORK" -> {
                        String[] parts = value.split("_", 2);
                        FireworkExplosion.Shape shape = FireworkExplosion.Shape.SMALL_BALL;
                        if (parts.length > 1) {
                            try {
                                shape = FireworkExplosion.Shape.valueOf(parts[1].toUpperCase());
                            } catch (IllegalArgumentException ignored) {}
                        }
                        int color = switch (parts[0].toUpperCase()) {
                            case "RED" -> 0xFF0000;
                            case "ORANGE" -> 0xFF8800;
                            case "YELLOW" -> 0xFFFF00;
                            case "GREEN" -> 0x00FF00;
                            case "LIME" -> 0x88FF00;
                            case "BLUE" -> 0x0000FF;
                            case "CYAN" -> 0x00FFFF;
                            case "PURPLE" -> 0x8800FF;
                            case "MAGENTA" -> 0xFF00FF;
                            case "PINK" -> 0xFF69B4;
                            case "WHITE" -> 0xFFFFFF;
                            case "BLACK" -> 0x000000;
                            default -> 0xFFFFFF;
                        };
                        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
                        var explosion = new FireworkExplosion(shape, IntList.of(color), IntList.of(), false, false);
                        rocket.set(net.minecraft.core.component.DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
                        var firework = new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, rocket);
                        level.addFreshEntity(firework);
                    }
                    case "PARTICLE" -> {
                        ResourceLocation particleKey = ResourceLocation.parse(value);
                        var particle = BuiltInRegistries.PARTICLE_TYPE.getOptional(particleKey).orElse(null);
                        if (particle instanceof SimpleParticleType simple) {
                            level.sendParticles(simple, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to execute win effect: {} for reward: {}", effect, reward.getId(), e);
            }
        }
    }

    private static String normalizeCommandString(String command) {
        if (command == null) return "";
        String trimmed = command.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
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
