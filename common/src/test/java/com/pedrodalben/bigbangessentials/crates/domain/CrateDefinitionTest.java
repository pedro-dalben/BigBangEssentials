package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CrateDefinitionTest {

    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @Test
    void constructor_SetsFields() {
        UUID id = UUID.randomUUID();
        CrateDefinition crate = new CrateDefinition(id, "vip_crate", "VIP Crate");
        assertEquals(id, crate.getId());
        assertEquals("vip_crate", crate.getKey());
        assertEquals("VIP Crate", crate.getDisplayName());
    }

    @Test
    void constructor_NullKey_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateDefinition(UUID.randomUUID(), null, "Test"));
    }

    @Test
    void constructor_BlankKey_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateDefinition(UUID.randomUUID(), "  ", "Test"));
    }

    @Test
    void constructor_InvalidKeyChars_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateDefinition(UUID.randomUUID(), "has space", "Test"));
        assertThrows(IllegalArgumentException.class,
            () -> new CrateDefinition(UUID.randomUUID(), "special!", "Test"));
    }

    @Test
    void constructor_NormalizesKey() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "MY_Crate-1", "Test");
        assertEquals("my_crate-1", crate.getKey());
    }

    @Test
    void constructor_UsesKeyAsDisplayNameWhenNull() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "auto_name", null);
        assertEquals("auto_name", crate.getDisplayName());
    }

    @Test
    void constructor_SetsDefaults() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "default_crate", "Default");
        assertEquals("", crate.getDescription());
        assertTrue(crate.isEnabled());
        assertEquals(CrateOpeningType.VIRTUAL, crate.getOpeningType());
        assertEquals(0, crate.getCooldownMillis());
        assertEquals(0.0, crate.getCost(), 0.001);
        assertTrue(crate.getRarities().isEmpty());
        assertTrue(crate.getRewards().isEmpty());
        assertTrue(crate.getMilestones().isEmpty());
        assertTrue(crate.getLore().isEmpty());
        assertNotNull(crate.getPreviewConfig());
        assertNotNull(crate.getAnimationConfig());
        assertNotNull(crate.getRequirements());
        assertNotNull(crate.getVisualConfig());
        assertNotNull(crate.getCreatedAt());
        assertNotNull(crate.getUpdatedAt());
    }

    @Test
    void setCooldownMillis_ClampsToZero() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setCooldownMillis(-100);
        assertEquals(0, crate.getCooldownMillis());
    }

    @Test
    void setCost_ClampsToZero() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setCost(-50.0);
        assertEquals(0.0, crate.getCost(), 0.001);
    }

    @Test
    void getRarities_ReturnsCopy() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setRarities(new ArrayList<>(List.of(new CrateRarity("common", "Common", "#FFF", 10.0))));
        List<CrateRarity> rarities = crate.getRarities();
        rarities.clear();
        assertEquals(1, crate.getRarities().size());
    }

    @Test
    void getRewards_ReturnsCopy() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setRewards(new ArrayList<>(List.of(new CrateReward("r1", "test", "R1", RewardType.ITEM, "common"))));
        List<CrateReward> rewards = crate.getRewards();
        rewards.clear();
        assertEquals(1, crate.getRewards().size());
    }

    @Test
    void setRarities_NullClears() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setRarities(List.of(new CrateRarity("common", "Common", "#FFF", 10.0)));
        assertFalse(crate.getRarities().isEmpty());
        crate.setRarities(null);
        assertTrue(crate.getRarities().isEmpty());
    }

    @Test
    void setRewards_NullClears() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setRewards(List.of(new CrateReward("r1", "test", "R1", RewardType.ITEM, "common")));
        assertFalse(crate.getRewards().isEmpty());
        crate.setRewards(null);
        assertTrue(crate.getRewards().isEmpty());
    }

    @Test
    void getRarity_ById_ReturnsCorrect() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity legendary = new CrateRarity("legendary", "Legendary", "#FFD700", 5.0);
        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        crate.setRarities(List.of(legendary, common));

        assertSame(legendary, crate.getRarity("legendary"));
        assertSame(common, crate.getRarity("common"));
        assertNull(crate.getRarity("nonexistent"));
    }

    @Test
    void getRarity_CacheWorks() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity rarity = new CrateRarity("rare", "Rare", "#00F", 3.0);
        crate.setRarities(List.of(rarity));

        assertNotNull(crate.getRarity("rare"));

        crate.setRarities(List.of());
        assertNull(crate.getRarity("rare"));
    }

    @Test
    void getReward_ById_ReturnsCorrect() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateReward r1 = new CrateReward("r1", "test", "R1", RewardType.ITEM, "common");
        CrateReward r2 = new CrateReward("r2", "test", "R2", RewardType.COMMAND, "legendary");
        crate.setRewards(List.of(r1, r2));

        assertSame(r1, crate.getReward("r1"));
        assertSame(r2, crate.getReward("r2"));
        assertNull(crate.getReward("r3"));
    }

    @Test
    void getReward_CacheInvalidates() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateReward reward = new CrateReward("r1", "test", "R1", RewardType.ITEM, "common");
        crate.setRewards(List.of(reward));

        assertNotNull(crate.getReward("r1"));

        crate.setRewards(List.of());
        assertNull(crate.getReward("r1"));
    }

    @Test
    void getRewardsByRarity_FiltersCorrectly() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateReward r1 = new CrateReward("r1", "test", "R1", RewardType.ITEM, "common");
        CrateReward r2 = new CrateReward("r2", "test", "R2", RewardType.ITEM, "legendary");
        CrateReward r3 = new CrateReward("r3", "test", "R3", RewardType.COMMAND, "common");
        crate.setRewards(List.of(r1, r2, r3));

        List<CrateReward> commonRewards = crate.getRewardsByRarity("common");
        assertEquals(2, commonRewards.size());
        assertTrue(commonRewards.contains(r1));
        assertTrue(commonRewards.contains(r3));
    }

    @Test
    void getEligibleRewards_FiltersActiveAndEligible() {
        CrateDefinition crate = createCrateWithRewards("common", "common");

        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        common.setActive(true);
        crate.setRarities(List.of(common));

        List<CrateReward> eligible = crate.getEligibleRewards(Set.of(), Map.of(), Map.of());
        assertEquals(2, eligible.size());
    }

    @Test
    void getEligibleRewards_ExcludesInactiveRewards() {
        CrateDefinition crate = createCrateWithRewards("common", "common");

        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        common.setActive(true);
        crate.setRarities(List.of(common));

        crate.getRewards().get(0).setActive(false);

        List<CrateReward> eligible = crate.getEligibleRewards(Set.of(), Map.of(), Map.of());
        assertEquals(1, eligible.size());
        assertEquals("r2", eligible.get(0).getId());
    }

    @Test
    void getEligibleRewards_ExcludesWhenRarityInactive() {
        CrateDefinition crate = createCrateWithRewards("common", "common");

        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        common.setActive(false);
        crate.setRarities(List.of(common));

        List<CrateReward> eligible = crate.getEligibleRewards(Set.of(), Map.of(), Map.of());
        assertTrue(eligible.isEmpty());
    }

    @Test
    void getEligibleRewards_ExcludesByPermission() {
        CrateDefinition crate = createCrateWithRewards("common", "common");

        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        common.setActive(true);
        crate.setRarities(List.of(common));

        crate.getRewards().get(0).setRequiredPermission("vip.only");

        List<CrateReward> eligible = crate.getEligibleRewards(Set.of("vip.only"), Map.of(), Map.of());
        assertEquals(2, eligible.size());

        eligible = crate.getEligibleRewards(Set.of(), Map.of(), Map.of());
        assertEquals(1, eligible.size());
        assertEquals("r2", eligible.get(0).getId());
    }

    @Test
    void getEligibleRewards_ExcludesByLimit() {
        CrateDefinition crate = createCrateWithRewards("common", "common");

        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        common.setActive(true);
        crate.setRarities(List.of(common));

        crate.getRewards().get(0).setPlayerLimit(1);

        List<CrateReward> eligible = crate.getEligibleRewards(Set.of(), Map.of(crate.getRewards().get(0).getId(), 1), Map.of());
        assertEquals(1, eligible.size());
        assertEquals("r2", eligible.get(0).getId());
    }

    @Test
    void hasValidRewards_ActiveRewards_ReturnsTrue() {
        CrateDefinition crate = createCrateWithRewards("common", "common");
        assertTrue(crate.hasValidRewards());
    }

    @Test
    void hasValidRewards_NoRewards_ReturnsFalse() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        assertFalse(crate.hasValidRewards());
    }

    @Test
    void hasValidRewards_AllInactive_ReturnsFalse() {
        CrateDefinition crate = createCrateWithRewards("common", "common");
        crate.getRewards().forEach(r -> r.setActive(false));
        assertFalse(crate.hasValidRewards());
    }

    @Test
    void hasRewardsForRarity_ActiveRewards_ReturnsTrue() {
        CrateDefinition crate = createCrateWithRewards("common", "common");
        assertTrue(crate.hasRewardsForRarity("common"));
        assertFalse(crate.hasRewardsForRarity("legendary"));
    }

    @Test
    void calculateRarityChance_NoActiveRarities_ReturnsZero() {
        CrateDefinition crate = createCrateWithRewards("common", "common");
        assertEquals(0.0, crate.calculateRarityChance("nonexistent"), 0.001);
    }

    @Test
    void calculateRarityChance_ActiveRarity_ReturnsPercentage() {
        CrateDefinition crate = crateWithRaritiesAndRewards();

        double commonChance = crate.calculateRarityChance("common");
        double rareChance = crate.calculateRarityChance("rare");

        assertEquals(25.0, commonChance, 0.001);
        assertEquals(75.0, rareChance, 0.001);
    }

    @Test
    void calculateRarityChance_SingleRarity_Returns100() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        CrateRarity only = new CrateRarity("only", "Only", "#FFF", 5.0);
        only.setActive(true);
        crate.setRarities(List.of(only));
        CrateReward reward = new CrateReward("r1", "test", "R1", RewardType.ITEM, "only");
        crate.setRewards(List.of(reward));

        assertEquals(100.0, crate.calculateRarityChance("only"), 0.001);
    }

    @Test
    void calculateRarityChance_ExcludesRarityWithoutRewards() {
        CrateDefinition crate = createCrateWithRewards("common", "common");

        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        common.setActive(true);
        CrateRarity emptyRarity = new CrateRarity("empty", "Empty", "#000", 20.0);
        emptyRarity.setActive(true);
        crate.setRarities(List.of(common, emptyRarity));

        double commonChance = crate.calculateRarityChance("common");
        assertEquals(100.0, commonChance, 0.001);
    }

    @Test
    void calculateRewardChance_NonexistentReward_ReturnsZero() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        assertEquals(0.0, crate.calculateRewardChance("nonexistent"), 0.001);
    }

    @Test
    void calculateRewardChance_ReturnsWeightedPercentage() {
        CrateDefinition crate = createCrateWithRewards("common", "common");

        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        common.setActive(true);
        crate.setRarities(List.of(common));

        double rewardChance = crate.calculateRewardChance(crate.getRewards().get(0).getId());
        assertTrue(rewardChance > 0);
    }

    @Test
    void toJson_Roundtrip() {
        UUID id = UUID.randomUUID();
        CrateDefinition original = new CrateDefinition(id, "test_crate", "Test Crate");
        original.setDescription("A test crate");
        original.setEnabled(true);
        original.setOpeningType(CrateOpeningType.PHYSICAL);
        original.setCooldownMillis(5000);
        original.setCost(100.0);
        original.setLore(List.of("Line 1", "Line 2"));

        CrateRarity rarity = new CrateRarity("common", "Common", "#AAAAAA", 10.0);
        original.setRarities(List.of(rarity));

        CrateReward reward = new CrateReward("r1", "test_crate", "Test Reward", RewardType.COMMAND, "common");
        reward.setWeight(1.0);
        original.setRewards(List.of(reward));

        CrateMilestone milestone = new CrateMilestone("m1", "Milestone", "r1", 10);
        original.setMilestones(List.of(milestone));

        JsonObject json = original.toJson();
        CrateDefinition restored = CrateDefinition.fromJson(json);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getKey(), restored.getKey());
        assertEquals(original.getDisplayName(), restored.getDisplayName());
        assertEquals(original.getDescription(), restored.getDescription());
        assertEquals(original.isEnabled(), restored.isEnabled());
        assertEquals(original.getOpeningType(), restored.getOpeningType());
        assertEquals(original.getCooldownMillis(), restored.getCooldownMillis());
        assertEquals(original.getCost(), restored.getCost(), 0.001);
        assertEquals(original.getLore(), restored.getLore());
        assertEquals(original.getRarities().size(), restored.getRarities().size());
        assertEquals(original.getRewards().size(), restored.getRewards().size());
        assertEquals(original.getMilestones().size(), restored.getMilestones().size());
    }

    @Test
    void fromJson_MinimalData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", UUID.randomUUID().toString());
        json.addProperty("key", "minimal_crate");
        json.addProperty("displayName", "Minimal");

        CrateDefinition crate = CrateDefinition.fromJson(json);
        assertEquals("minimal_crate", crate.getKey());
        assertEquals("Minimal", crate.getDisplayName());
        assertTrue(crate.isEnabled());
        assertTrue(crate.getRarities().isEmpty());
        assertTrue(crate.getRewards().isEmpty());
    }

    private CrateDefinition createCrateWithRewards(String rarityId1, String rarityId2) {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test_crate", "Test Crate");
        CrateReward r1 = new CrateReward("r1", "test_crate", "Reward 1", RewardType.ITEM, rarityId1);
        CrateReward r2 = new CrateReward("r2", "test_crate", "Reward 2", RewardType.COMMAND, rarityId2);
        crate.setRewards(List.of(r1, r2));
        return crate;
    }

    private CrateDefinition crateWithRaritiesAndRewards() {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");

        CrateRarity common = new CrateRarity("common", "Common", "#AAA", 10.0);
        common.setActive(true);
        CrateRarity rare = new CrateRarity("rare", "Rare", "#00F", 30.0);
        rare.setActive(true);
        crate.setRarities(List.of(common, rare));

        CrateReward r1 = new CrateReward("r1", "test", "R1", RewardType.ITEM, "common");
        CrateReward r2 = new CrateReward("r2", "test", "R2", RewardType.COMMAND, "common");
        CrateReward r3 = new CrateReward("r3", "test", "R3", RewardType.ITEM, "rare");
        crate.setRewards(List.of(r1, r2, r3));
        return crate;
    }
}
