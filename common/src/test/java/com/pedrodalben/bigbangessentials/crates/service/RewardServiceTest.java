package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.*;
import com.pedrodalben.bigbangessentials.crates.repository.RewardRollStateRepository;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RewardServiceTest {

    private static RewardService rewardService;

    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
        rewardService = new RewardService(null, null);
    }

    @Test
    void selectRarityByWeight_EmptyRarities_ReturnsNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("common", "Common", "#FFF", 10.0);
        crate.setRarities(List.of(rarity));
        assertNull(rewardService.selectRarityByWeight(crate));
    }

    @Test
    void selectRarityByWeight_NoActiveRarities_ReturnsNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("common", "Common", "#FFF", 10.0);
        rarity.setActive(false);
        crate.setRarities(List.of(rarity));
        assertNull(rewardService.selectRarityByWeight(crate));
    }

    @Test
    void selectRarityByWeight_NoRewardsForRarity_ReturnsNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("common", "Common", "#FFF", 10.0);
        rarity.setActive(true);
        crate.setRarities(List.of(rarity));
        assertNull(rewardService.selectRarityByWeight(crate));
    }

    @Test
    void selectRarityByWeight_TotalWeightZero_ReturnsNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("common", "Common", "#FFF", 0.0);
        rarity.setActive(true);
        crate.setRarities(List.of(rarity));
        assertNull(rewardService.selectRarityByWeight(crate));
    }

    @Test
    void selectRarityByWeight_SingleRarity_ReturnsIt() {
        CrateDefinition crate = crateWithReward("common", 10.0, "r1");
        CrateRarity result = rewardService.selectRarityByWeight(crate);
        assertNotNull(result);
        assertEquals("common", result.getId());
    }

    @Test
    void selectRarityByWeight_MultipleRarities_ReturnsNonNull() {
        CrateDefinition crate = crateWithReward("common", 10.0, "r1");
        CrateRarity rare = new CrateRarity("rare", "Rare", "#00F", 30.0);
        rare.setActive(true);
        crate.setRarities(List.of(crate.getRarities().get(0), rare));
        CrateReward r2 = new CrateReward("r2", "test", "R2", RewardType.ITEM, "rare");
        crate.setRewards(List.of(crate.getRewards().get(0), r2));

        CrateRarity result = rewardService.selectRarityByWeight(crate);
        assertNotNull(result);
        assertTrue(result.getId().equals("common") || result.getId().equals("rare"));
    }

    @Test
    void selectRarityByWeight_InactiveRaritiesExcluded() {
        CrateDefinition crate = crateWithReward("common", 10.0, "r1");
        CrateRarity rare = new CrateRarity("rare", "Rare", "#00F", 30.0);
        rare.setActive(false);
        crate.setRarities(List.of(crate.getRarities().get(0), rare));

        CrateRarity result = rewardService.selectRarityByWeight(crate);
        assertNotNull(result);
        assertEquals("common", result.getId());
    }

    @Test
    void selectRewardByWeight_EmptyRewards_ReturnsNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("common", "Common", "#FFF", 10.0);
        rarity.setActive(true);
        crate.setRarities(List.of(rarity));

        assertNull(rewardService.selectRewardByWeight(crate, "common"));
    }

    @Test
    void selectRewardByWeight_NoActiveRewards_ReturnsNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("common", "Common", "#FFF", 10.0);
        rarity.setActive(true);
        crate.setRarities(List.of(rarity));

        CrateReward reward = new CrateReward("r1", "test", "R1", RewardType.ITEM, "common");
        reward.setActive(false);
        crate.setRewards(List.of(reward));

        assertNull(rewardService.selectRewardByWeight(crate, "common"));
    }

    @Test
    void selectRewardByWeight_OnlyMilestoneRewards_ReturnsNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("common", "Common", "#FFF", 10.0);
        rarity.setActive(true);
        crate.setRarities(List.of(rarity));

        CrateReward reward = new CrateReward("r1", "test", "R1", RewardType.ITEM, "common");
        reward.setMilestoneOnly(true);
        crate.setRewards(List.of(reward));

        assertNull(rewardService.selectRewardByWeight(crate, "common"));
    }

    @Test
    void selectRewardByWeight_TotalWeightZero_ReturnsNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("common", "Common", "#FFF", 10.0);
        rarity.setActive(true);
        crate.setRarities(List.of(rarity));

        CrateReward reward = new CrateReward("r1", "test", "R1", RewardType.ITEM, "common");
        reward.setWeight(0.0);
        crate.setRewards(List.of(reward));

        assertNull(rewardService.selectRewardByWeight(crate, "common"));
    }

    @Test
    void selectRewardByWeight_SingleReward_ReturnsIt() {
        CrateDefinition crate = crateWithReward("common", 10.0, "r1");
        CrateReward result = rewardService.selectRewardByWeight(crate, "common");
        assertNotNull(result);
        assertEquals("r1", result.getId());
    }

    @Test
    void selectRewardByWeight_MultipleRewards_ReturnsNonNull() {
        CrateDefinition crate = crateWithReward("common", 10.0, "r1");
        CrateReward r2 = new CrateReward("r2", "test", "R2", RewardType.ITEM, "common");
        r2.setWeight(5.0);
        crate.setRewards(List.of(crate.getRewards().get(0), r2));

        CrateReward result = rewardService.selectRewardByWeight(crate, "common");
        assertNotNull(result);
        assertTrue(result.getId().equals("r1") || result.getId().equals("r2"));
    }

    @Test
    void selectRewardByWeight_OnlyActiveRewardsConsidered() {
        CrateDefinition crate = crateWithReward("common", 10.0, "r1");
        CrateReward r2 = new CrateReward("r2", "test", "R2", RewardType.ITEM, "common");
        r2.setActive(false);
        crate.setRewards(List.of(crate.getRewards().get(0), r2));

        CrateReward result = rewardService.selectRewardByWeight(crate, "common");
        assertNotNull(result);
        assertEquals("r1", result.getId());
    }

    @Test
    void selectRewardByWeight_MilestoneOnlyRewardsExcluded() {
        CrateDefinition crate = crateWithReward("common", 10.0, "r1");
        CrateReward r2 = new CrateReward("r2", "test", "R2", RewardType.ITEM, "common");
        r2.setMilestoneOnly(true);
        crate.setRewards(List.of(crate.getRewards().get(0), r2));

        CrateReward result = rewardService.selectRewardByWeight(crate, "common");
        assertNotNull(result);
        assertEquals("r1", result.getId());
    }

    private CrateDefinition crateWithReward(String rarityId, double rarityWeight, String rewardId) {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity(rarityId, rarityId, "#FFF", rarityWeight);
        rarity.setActive(true);
        crate.setRarities(List.of(rarity));

        CrateReward reward = new CrateReward(rewardId, "test", "Reward", RewardType.ITEM, rarityId);
        crate.setRewards(List.of(reward));
        return crate;
    }
}
