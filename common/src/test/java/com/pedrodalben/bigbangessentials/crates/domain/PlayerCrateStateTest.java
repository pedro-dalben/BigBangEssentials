package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCrateStateTest {

    @Test
    void constructor_SetsFields() {
        UUID playerId = UUID.randomUUID();
        PlayerCrateState state = new PlayerCrateState(playerId, "crate_vip");
        assertEquals(playerId, state.getPlayerId());
        assertEquals("crate_vip", state.getCrateId());
    }

    @Test
    void constructor_InitialStateIsZero() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertEquals(0, state.getCooldownUntil());
        assertEquals(0, state.getTotalOpened());
        assertEquals(0, state.getMilestoneProgress());
        assertNull(state.getLatestOpenedAt());
    }

    @Test
    void constructor_NullPlayerId_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> new PlayerCrateState(null, "crate_test"));
    }

    @Test
    void constructor_NullCrateId_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> new PlayerCrateState(UUID.randomUUID(), null));
    }

    @Test
    void isOnCooldown_NoCooldown_ReturnsFalse() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertFalse(state.isOnCooldown());
    }

    @Test
    void isOnCooldown_ActiveCooldown_ReturnsTrue() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.startCooldown(100000);
        assertTrue(state.isOnCooldown());
    }

    @Test
    void isOnCooldown_ExpiredCooldown_ReturnsFalse() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.setCooldownUntil(System.currentTimeMillis() - 1);
        assertFalse(state.isOnCooldown());
    }

    @Test
    void getRemainingCooldownMillis_NoCooldown_ReturnsZero() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertEquals(0, state.getRemainingCooldownMillis());
    }

    @Test
    void getRemainingCooldownMillis_Expired_ReturnsZero() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.setCooldownUntil(System.currentTimeMillis() - 5000);
        assertEquals(0, state.getRemainingCooldownMillis());
    }

    @Test
    void getRemainingCooldownMillis_Active_ReturnsPositive() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.startCooldown(60000);
        assertTrue(state.getRemainingCooldownMillis() > 0);
        assertTrue(state.getRemainingCooldownMillis() <= 60000);
    }

    @Test
    void startCooldown_SetsCooldownUntil() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        long before = System.currentTimeMillis();
        state.startCooldown(5000);
        long after = System.currentTimeMillis();
        assertTrue(state.getCooldownUntil() >= before + 5000);
        assertTrue(state.getCooldownUntil() <= after + 5000);
    }

    @Test
    void clearCooldown_ResetsToZero() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.startCooldown(5000);
        assertTrue(state.getCooldownUntil() > 0);
        state.clearCooldown();
        assertEquals(0, state.getCooldownUntil());
    }

    @Test
    void recordOpening_IncrementsTotalOpened() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertEquals(0, state.getTotalOpened());
        state.recordOpening();
        assertEquals(1, state.getTotalOpened());
        state.recordOpening();
        assertEquals(2, state.getTotalOpened());
    }

    @Test
    void recordOpening_SetsLatestOpenedAt() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertNull(state.getLatestOpenedAt());
        state.recordOpening();
        assertNotNull(state.getLatestOpenedAt());
    }

    @Test
    void recordOpening_UpdatesMilestoneProgress() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.recordOpening();
        assertEquals(1, state.getMilestoneProgress());
        state.recordOpening();
        assertEquals(2, state.getMilestoneProgress());
    }

    @Test
    void setMilestoneProgress_ClampsToZero() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.setMilestoneProgress(-1);
        assertEquals(0, state.getMilestoneProgress());
    }

    @Test
    void setMilestoneProgress_PositiveValue() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.setMilestoneProgress(15);
        assertEquals(15, state.getMilestoneProgress());
    }

    @Test
    void getOpeningsUntilNextMilestone_NullMilestone_ReturnsMinusOne() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertEquals(-1, state.getOpeningsUntilNextMilestone(null));
    }

    @Test
    void getOpeningsUntilNextMilestone_InactiveMilestone_ReturnsMinusOne() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        milestone.setActive(false);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertEquals(-1, state.getOpeningsUntilNextMilestone(milestone));
    }

    @Test
    void getOpeningsUntilNextMilestone_SomeProgress() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.recordOpening();
        state.recordOpening();
        state.recordOpening();
        assertEquals(7, state.getOpeningsUntilNextMilestone(milestone));
    }

    @Test
    void getOpeningsUntilNextMilestone_AlreadyReached_ReturnsZero() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 3);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.recordOpening();
        state.recordOpening();
        state.recordOpening();
        assertEquals(0, state.getOpeningsUntilNextMilestone(milestone));
    }

    @Test
    void isMilestoneReached_NullMilestone_ReturnsFalse() {
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertFalse(state.isMilestoneReached(null));
    }

    @Test
    void isMilestoneReached_InactiveMilestone_ReturnsFalse() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 5);
        milestone.setActive(false);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertFalse(state.isMilestoneReached(milestone));
    }

    @Test
    void isMilestoneReached_NotYetReached_ReturnsFalse() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        assertEquals(0, milestone.getProgressPercent(state.getTotalOpened()), 0.001);
        assertFalse(state.isMilestoneReached(milestone));
    }

    @Test
    void isMilestoneReached_ExactlyReached_ReturnsTrue() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 5);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        for (int i = 0; i < 5; i++) state.recordOpening();
        assertTrue(state.isMilestoneReached(milestone));
    }

    @Test
    void isMilestoneReached_Repeatable_ExactMultiple_ReturnsTrue() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 5);
        milestone.setRepeatable(true);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        for (int i = 0; i < 10; i++) state.recordOpening();
        assertTrue(state.isMilestoneReached(milestone));
    }

    @Test
    void isMilestoneReached_Repeatable_NotExactMultiple_ReturnsFalse() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 5);
        milestone.setRepeatable(true);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        for (int i = 0; i < 7; i++) state.recordOpening();
        assertFalse(state.isMilestoneReached(milestone));
    }

    @Test
    void isMilestoneReached_Repeatable_NotReachedMinimum_ReturnsFalse() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 5);
        milestone.setRepeatable(true);
        PlayerCrateState state = new PlayerCrateState(UUID.randomUUID(), "crate_test");
        state.recordOpening();
        state.recordOpening();
        assertFalse(state.isMilestoneReached(milestone));
    }

    @Test
    void toJson_Roundtrip() {
        UUID playerId = UUID.randomUUID();
        PlayerCrateState original = new PlayerCrateState(playerId, "crate_daily");
        original.startCooldown(30000);
        original.recordOpening();
        original.recordOpening();
        original.recordOpening();

        JsonObject json = original.toJson();
        PlayerCrateState restored = PlayerCrateState.fromJson(json);

        assertEquals(original.getPlayerId(), restored.getPlayerId());
        assertEquals(original.getCrateId(), restored.getCrateId());
        assertEquals(original.getCooldownUntil(), restored.getCooldownUntil());
        assertEquals(original.getTotalOpened(), restored.getTotalOpened());
        assertEquals(original.getMilestoneProgress(), restored.getMilestoneProgress());
    }

    @Test
    void fromJson_FullRestore() {
        UUID playerId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();

        JsonObject json = new JsonObject();
        json.addProperty("playerId", playerId.toString());
        json.addProperty("crateId", "crate_rare");
        json.addProperty("cooldownUntil", 123456789L);
        json.addProperty("totalOpened", 25);
        json.addProperty("milestoneProgress", 25);
        json.addProperty("latestOpenedAt", now.toString());

        PlayerCrateState state = PlayerCrateState.fromJson(json);
        assertEquals(playerId, state.getPlayerId());
        assertEquals("crate_rare", state.getCrateId());
        assertEquals(123456789L, state.getCooldownUntil());
        assertEquals(25, state.getTotalOpened());
        assertEquals(25, state.getMilestoneProgress());
        assertEquals(now, state.getLatestOpenedAt());
    }
}
