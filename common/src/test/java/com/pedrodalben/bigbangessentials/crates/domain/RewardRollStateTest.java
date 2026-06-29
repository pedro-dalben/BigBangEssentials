package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RewardRollStateTest {

    @Test
    void constructor_SetsRewardId() {
        RewardRollState state = new RewardRollState("reward_test");
        assertEquals("reward_test", state.getRewardId());
    }

    @Test
    void constructor_InitialCountsAreZero() {
        RewardRollState state = new RewardRollState("reward_test");
        assertEquals(0, state.getGlobalCount());
        assertTrue(state.getPlayerCounts().isEmpty());
    }

    @Test
    void constructor_NullRewardId_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new RewardRollState(null));
    }

    @Test
    void incrementGlobal_IncreasesCount() {
        RewardRollState state = new RewardRollState("reward_test");
        state.incrementGlobal();
        assertEquals(1, state.getGlobalCount());
        state.incrementGlobal();
        assertEquals(2, state.getGlobalCount());
    }

    @Test
    void incrementPlayer_IncreasesPlayerCount() {
        RewardRollState state = new RewardRollState("reward_test");
        UUID playerId = UUID.randomUUID();
        state.incrementPlayer(playerId);
        assertEquals(1, state.getPlayerCount(playerId));
        state.incrementPlayer(playerId);
        assertEquals(2, state.getPlayerCount(playerId));
    }

    @Test
    void incrementPlayer_MultiplePlayers() {
        RewardRollState state = new RewardRollState("reward_test");
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        state.incrementPlayer(player1);
        state.incrementPlayer(player1);
        state.incrementPlayer(player2);
        assertEquals(2, state.getPlayerCount(player1));
        assertEquals(1, state.getPlayerCount(player2));
    }

    @Test
    void getPlayerCount_UnknownPlayer_ReturnsZero() {
        RewardRollState state = new RewardRollState("reward_test");
        assertEquals(0, state.getPlayerCount(UUID.randomUUID()));
    }

    @Test
    void isGloballyExhausted_LimitNotReached_ReturnsFalse() {
        RewardRollState state = new RewardRollState("reward_test");
        state.incrementGlobal();
        assertFalse(state.isGloballyExhausted(5));
    }

    @Test
    void isGloballyExhausted_LimitReached_ReturnsTrue() {
        RewardRollState state = new RewardRollState("reward_test");
        state.incrementGlobal();
        state.incrementGlobal();
        assertTrue(state.isGloballyExhausted(2));
    }

    @Test
    void isGloballyExhausted_ZeroLimit_ReturnsFalse() {
        RewardRollState state = new RewardRollState("reward_test");
        state.incrementGlobal();
        assertFalse(state.isGloballyExhausted(0));
    }

    @Test
    void isGloballyExhausted_NegativeLimit_ReturnsFalse() {
        RewardRollState state = new RewardRollState("reward_test");
        state.incrementGlobal();
        assertFalse(state.isGloballyExhausted(-1));
    }

    @Test
    void isPlayerExhausted_LimitNotReached_ReturnsFalse() {
        RewardRollState state = new RewardRollState("reward_test");
        UUID playerId = UUID.randomUUID();
        state.incrementPlayer(playerId);
        assertFalse(state.isPlayerExhausted(playerId, 5));
    }

    @Test
    void isPlayerExhausted_LimitReached_ReturnsTrue() {
        RewardRollState state = new RewardRollState("reward_test");
        UUID playerId = UUID.randomUUID();
        state.incrementPlayer(playerId);
        state.incrementPlayer(playerId);
        assertTrue(state.isPlayerExhausted(playerId, 2));
    }

    @Test
    void isPlayerExhausted_ZeroLimit_ReturnsFalse() {
        RewardRollState state = new RewardRollState("reward_test");
        UUID playerId = UUID.randomUUID();
        state.incrementPlayer(playerId);
        assertFalse(state.isPlayerExhausted(playerId, 0));
    }

    @Test
    void isPlayerExhausted_NegativeLimit_ReturnsFalse() {
        RewardRollState state = new RewardRollState("reward_test");
        UUID playerId = UUID.randomUUID();
        state.incrementPlayer(playerId);
        assertFalse(state.isPlayerExhausted(playerId, -1));
    }

    @Test
    void isPlayerExhausted_UnknownPlayer_ReturnsFalse() {
        RewardRollState state = new RewardRollState("reward_test");
        assertFalse(state.isPlayerExhausted(UUID.randomUUID(), 5));
    }

    @Test
    void getPlayerCounts_ReturnsCopy() {
        RewardRollState state = new RewardRollState("reward_test");
        UUID playerId = UUID.randomUUID();
        state.incrementPlayer(playerId);
        java.util.Map<UUID, Integer> counts = state.getPlayerCounts();
        assertEquals(1, counts.size());
        counts.clear();
        assertEquals(1, state.getPlayerCount(playerId));
    }

    @Test
    void toJson_Roundtrip() {
        RewardRollState original = new RewardRollState("reward_legendary");
        original.incrementGlobal();
        original.incrementGlobal();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        original.incrementPlayer(player1);
        original.incrementPlayer(player1);
        original.incrementPlayer(player2);

        JsonObject json = original.toJson();
        RewardRollState restored = RewardRollState.fromJson(json);

        assertEquals(original.getRewardId(), restored.getRewardId());
        assertEquals(original.getGlobalCount(), restored.getGlobalCount());
        assertEquals(original.getPlayerCount(player1), restored.getPlayerCount(player1));
        assertEquals(original.getPlayerCount(player2), restored.getPlayerCount(player2));
    }

    @Test
    void fromJson_EmptyPlayerCounts() {
        JsonObject json = new JsonObject();
        json.addProperty("rewardId", "reward_common");
        json.addProperty("globalCount", 0);

        RewardRollState state = RewardRollState.fromJson(json);
        assertEquals("reward_common", state.getRewardId());
        assertEquals(0, state.getGlobalCount());
        assertTrue(state.getPlayerCounts().isEmpty());
    }

    @Test
    void fromJson_WithPlayerCounts() {
        UUID playerId = UUID.randomUUID();
        JsonObject json = new JsonObject();
        json.addProperty("rewardId", "reward_rare");
        json.addProperty("globalCount", 5);
        JsonObject players = new JsonObject();
        players.addProperty(playerId.toString(), 3);
        json.add("playerCounts", players);

        RewardRollState state = RewardRollState.fromJson(json);
        assertEquals(5, state.getGlobalCount());
        assertEquals(3, state.getPlayerCount(playerId));
    }
}
