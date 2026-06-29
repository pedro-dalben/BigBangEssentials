package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CrateRewardTest {

    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @Test
    void constructor_SetsFields() {
        CrateReward reward = new CrateReward("reward_diamond", "crate_vip", "Diamond", RewardType.ITEM, "legendary");
        assertEquals("reward_diamond", reward.getId());
        assertEquals("crate_vip", reward.getCrateId());
        assertEquals("Diamond", reward.getName());
        assertEquals(RewardType.ITEM, reward.getType());
        assertEquals("legendary", reward.getRarityId());
    }

    @Test
    void constructor_NullId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateReward(null, "crate_1", "Test", RewardType.ITEM, "common"));
    }

    @Test
    void constructor_BlankId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateReward(" ", "crate_1", "Test", RewardType.ITEM, "common"));
    }

    @Test
    void constructor_InvalidIdChars_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateReward("has space", "crate_1", "Test", RewardType.ITEM, "common"));
        assertThrows(IllegalArgumentException.class,
            () -> new CrateReward("special!", "crate_1", "Test", RewardType.ITEM, "common"));
    }

    @Test
    void constructor_NormalizesId() {
        CrateReward reward = new CrateReward("MY_REWARD-1", "crate_1", "My Reward", RewardType.COMMAND, "rare");
        assertEquals("my_reward-1", reward.getId());
    }

    @Test
    void constructor_UsesIdAsNameWhenNameNull() {
        CrateReward reward = new CrateReward("auto_reward", "crate_1", null, RewardType.ITEM, "common");
        assertEquals("auto_reward", reward.getName());
    }

    @Test
    void constructor_DefaultsTypeToItemWhenNull() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", null, "common");
        assertEquals(RewardType.ITEM, reward.getType());
    }

    @Test
    void constructor_ValidatesRarityId() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, null));
        assertThrows(IllegalArgumentException.class,
            () -> new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, ""));
    }

    @Test
    void constructor_SetsDefaultValues() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.COMMAND, "common");
        assertEquals(1.0, reward.getWeight(), 0.001);
        assertTrue(reward.getItems().isEmpty());
        assertTrue(reward.getCommands().isEmpty());
        assertEquals("", reward.getRequiredPermission());
        assertTrue(reward.getBlockingPermissions().isEmpty());
        assertEquals(-1, reward.getGlobalLimit());
        assertEquals(-1, reward.getPlayerLimit());
        assertFalse(reward.isBroadcast());
        assertEquals("", reward.getBroadcastMessage());
        assertEquals("", reward.getPlayerMessage());
        assertTrue(reward.isActive());
        assertTrue(reward.isVisibleInPreview());
        assertFalse(reward.isMilestoneOnly());
        assertEquals(0, reward.getDisplayOrder());
        assertTrue(reward.getLore().isEmpty());
    }

    @Test
    void setWeight_ClampsToZero() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setWeight(-5.0);
        assertEquals(0.0, reward.getWeight(), 0.001);
    }

    @Test
    void setRarityId_Validates() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setRarityId("EPIC");
        assertEquals("epic", reward.getRarityId());
        assertThrows(IllegalArgumentException.class, () -> reward.setRarityId(null));
    }

    @Test
    void isEligible_ActiveInactive() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        assertTrue(reward.isEligible(Set.of(), Map.of(), Map.of()));

        reward.setActive(false);
        assertFalse(reward.isEligible(Set.of(), Map.of(), Map.of()));
    }

    @Test
    void isEligible_RequiredPermission_Missing() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setRequiredPermission("vip.required");
        assertFalse(reward.isEligible(Set.of("other.perm"), Map.of(), Map.of()));
    }

    @Test
    void isEligible_RequiredPermission_HasPermission() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setRequiredPermission("vip.required");
        assertTrue(reward.isEligible(Set.of("vip.required"), Map.of(), Map.of()));
    }

    @Test
    void isEligible_BlockingPermission_Blocks() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setBlockingPermissions(List.of("block.this"));
        assertFalse(reward.isEligible(Set.of("block.this"), Map.of(), Map.of()));
    }

    @Test
    void isEligible_BlockingPermission_NoBlockWhenNoPerm() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setBlockingPermissions(List.of("block.this"));
        assertTrue(reward.isEligible(Set.of("other.perm"), Map.of(), Map.of()));
    }

    @Test
    void isEligible_GlobalLimit_NotExhausted() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setGlobalLimit(5);
        assertTrue(reward.isEligible(Set.of(), Map.of(), Map.of("r1", 3)));
    }

    @Test
    void isEligible_GlobalLimit_Exhausted() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setGlobalLimit(5);
        assertFalse(reward.isEligible(Set.of(), Map.of(), Map.of("r1", 5)));
    }

    @Test
    void isEligible_GlobalLimit_OverExhausted() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setGlobalLimit(5);
        assertFalse(reward.isEligible(Set.of(), Map.of(), Map.of("r1", 10)));
    }

    @Test
    void isEligible_GlobalLimit_UnlimitedWhenMinusOne() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setGlobalLimit(-1);
        assertTrue(reward.isEligible(Set.of(), Map.of(), Map.of("r1", 999)));
    }

    @Test
    void isEligible_PlayerLimit_NotExhausted() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setPlayerLimit(3);
        assertTrue(reward.isEligible(Set.of(), Map.of("r1", 2), Map.of()));
    }

    @Test
    void isEligible_PlayerLimit_Exhausted() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setPlayerLimit(3);
        assertFalse(reward.isEligible(Set.of(), Map.of("r1", 3), Map.of()));
    }

    @Test
    void isEligible_PlayerLimit_UnlimitedWhenMinusOne() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setPlayerLimit(-1);
        assertTrue(reward.isEligible(Set.of(), Map.of("r1", 999), Map.of()));
    }

    @Test
    void isEligible_Combined_AllConditionsMet() {
        CrateReward reward = new CrateReward("r1", "crate_1", "VIP Reward", RewardType.ITEM, "legendary");
        reward.setRequiredPermission("vip");
        reward.setBlockingPermissions(List.of("blacklisted"));
        reward.setGlobalLimit(100);
        reward.setPlayerLimit(5);

        assertTrue(reward.isEligible(
            Set.of("vip"),
            Map.of("r1", 3),
            Map.of("r1", 50)
        ));
    }

    @Test
    void isEligible_Combined_OneConditionFails() {
        CrateReward reward = new CrateReward("r1", "crate_1", "VIP Reward", RewardType.ITEM, "legendary");
        reward.setRequiredPermission("vip");
        reward.setBlockingPermissions(List.of("blacklisted"));
        reward.setGlobalLimit(100);
        reward.setPlayerLimit(5);

        assertFalse(reward.isEligible(
            Set.of("vip", "blacklisted"),
            Map.of("r1", 3),
            Map.of("r1", 50)
        ));
    }

    @Test
    void toJson_Roundtrip() throws Exception {
        CrateReward original = new CrateReward("reward_sword", "crate_vip", "Legendary Sword", RewardType.ITEM, "legendary");
        original.setWeight(2.5);
        original.setRequiredPermission("vip.reward");
        original.setBlockingPermissions(List.of("noob"));
        original.setGlobalLimit(50);
        original.setPlayerLimit(3);
        original.setBroadcast(true);
        original.setBroadcastMessage("Player got a legendary sword!");
        original.setPlayerMessage("You got a legendary sword!");
        original.setActive(true);
        original.setVisibleInPreview(true);
        original.setMilestoneOnly(false);
        original.setDisplayOrder(1);
        original.setLore(List.of("A powerful sword"));

        JsonObject json = original.toJson();
        CrateReward restored = CrateReward.fromJson(json);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getCrateId(), restored.getCrateId());
        assertEquals(original.getType(), restored.getType());
        assertEquals(original.getRarityId(), restored.getRarityId());
        assertEquals(original.getWeight(), restored.getWeight(), 0.001);
        assertEquals(original.getRequiredPermission(), restored.getRequiredPermission());
        assertEquals(original.getBlockingPermissions(), restored.getBlockingPermissions());
        assertEquals(original.getGlobalLimit(), restored.getGlobalLimit());
        assertEquals(original.getPlayerLimit(), restored.getPlayerLimit());
        assertEquals(original.isBroadcast(), restored.isBroadcast());
        assertEquals(original.getBroadcastMessage(), restored.getBroadcastMessage());
        assertEquals(original.getPlayerMessage(), restored.getPlayerMessage());
        assertEquals(original.isActive(), restored.isActive());
        assertEquals(original.isVisibleInPreview(), restored.isVisibleInPreview());
        assertEquals(original.isMilestoneOnly(), restored.isMilestoneOnly());
        assertEquals(original.getDisplayOrder(), restored.getDisplayOrder());
        assertEquals(original.getLore(), restored.getLore());
    }

    @Test
    void fromJson_MinimalData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "reward_auto");
        json.addProperty("crateId", "crate_test");
        json.addProperty("rarityId", "uncommon");

        CrateReward reward = CrateReward.fromJson(json);
        assertEquals("reward_auto", reward.getId());
        assertEquals("reward_auto", reward.getName());
        assertEquals("crate_test", reward.getCrateId());
        assertEquals(RewardType.ITEM, reward.getType());
        assertEquals("uncommon", reward.getRarityId());
        assertEquals(1.0, reward.getWeight(), 0.001);
    }

    @Test
    void getItems_ReturnsCopy() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        List<net.minecraft.world.item.ItemStack> items = reward.getItems();
        items.add(net.minecraft.world.item.ItemStack.EMPTY);
        assertTrue(reward.getItems().isEmpty());
    }

    @Test
    void getCommands_ReturnsCopy() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.COMMAND, "common");
        reward.setCommands(List.of("give {player} diamond"));
        List<String> commands = reward.getCommands();
        commands.add("extra");
        assertEquals(1, reward.getCommands().size());
    }

    @Test
    void setRequiredPermission_WorkaroundForBug() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setRequiredPermission("test.perm");
        assertEquals("test.perm", reward.getRequiredPermission());
    }

    @Test
    void getBlockingPermissions_ReturnsCopy() {
        CrateReward reward = new CrateReward("r1", "crate_1", "Test", RewardType.ITEM, "common");
        reward.setBlockingPermissions(List.of("perm1"));
        List<String> perms = reward.getBlockingPermissions();
        perms.add("perm2");
        assertEquals(1, reward.getBlockingPermissions().size());
    }
}
