package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerVirtualKeyBalanceTest {

    @Test
    void constructor_SetsFields() {
        UUID playerId = UUID.randomUUID();
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(playerId, "vip_key", 10);
        assertEquals(playerId, balance.getPlayerId());
        assertEquals("vip_key", balance.getKeyId());
        assertEquals(10, balance.getAmount());
    }

    @Test
    void constructor_ClampsNegativeAmountToZero() {
        UUID playerId = UUID.randomUUID();
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(playerId, "test_key", -5);
        assertEquals(0, balance.getAmount());
    }

    @Test
    void constructor_NullPlayerId_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> new PlayerVirtualKeyBalance(null, "test_key", 5));
    }

    @Test
    void constructor_NullKeyId_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> new PlayerVirtualKeyBalance(UUID.randomUUID(), null, 5));
    }

    @Test
    void setAmount_ClampsToZero() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 10);
        balance.setAmount(-1);
        assertEquals(0, balance.getAmount());
    }

    @Test
    void setAmount_PositiveValue() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 0);
        balance.setAmount(25);
        assertEquals(25, balance.getAmount());
    }

    @Test
    void hasAtLeast_SufficientBalance() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 10);
        assertTrue(balance.hasAtLeast(5));
        assertTrue(balance.hasAtLeast(10));
    }

    @Test
    void hasAtLeast_InsufficientBalance() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 5);
        assertFalse(balance.hasAtLeast(10));
    }

    @Test
    void hasAtLeast_ZeroBalance() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 0);
        assertFalse(balance.hasAtLeast(1));
    }

    @Test
    void add_PositiveDelta_IncreasesAmount() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 5);
        assertTrue(balance.add(3));
        assertEquals(8, balance.getAmount());
    }

    @Test
    void add_ZeroDelta_ReturnsFalse() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 5);
        assertFalse(balance.add(0));
        assertEquals(5, balance.getAmount());
    }

    @Test
    void add_NegativeDelta_ReturnsFalse() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 5);
        assertFalse(balance.add(-2));
        assertEquals(5, balance.getAmount());
    }

    @Test
    void remove_PositiveDelta_DecreasesAmount() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 10);
        assertTrue(balance.remove(3));
        assertEquals(7, balance.getAmount());
    }

    @Test
    void remove_ExactAmount_ReturnsTrue() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 5);
        assertTrue(balance.remove(5));
        assertEquals(0, balance.getAmount());
    }

    @Test
    void remove_ExcessiveDelta_ReturnsFalse() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 5);
        assertFalse(balance.remove(10));
        assertEquals(5, balance.getAmount());
    }

    @Test
    void remove_ZeroDelta_ReturnsFalse() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 5);
        assertFalse(balance.remove(0));
        assertEquals(5, balance.getAmount());
    }

    @Test
    void remove_NegativeDelta_ReturnsFalse() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 5);
        assertFalse(balance.remove(-1));
        assertEquals(5, balance.getAmount());
    }

    @Test
    void remove_FromEmptyBalance_ReturnsFalse() {
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(UUID.randomUUID(), "test_key", 0);
        assertFalse(balance.remove(1));
    }

    @Test
    void toJson_Roundtrip() {
        UUID playerId = UUID.randomUUID();
        PlayerVirtualKeyBalance original = new PlayerVirtualKeyBalance(playerId, "diamond_key", 42);
        original.setAmount(42);

        JsonObject json = original.toJson();
        PlayerVirtualKeyBalance restored = PlayerVirtualKeyBalance.fromJson(json);

        assertEquals(original.getPlayerId(), restored.getPlayerId());
        assertEquals(original.getKeyId(), restored.getKeyId());
        assertEquals(original.getAmount(), restored.getAmount());
    }

    @Test
    void fromJson_RestoresUpdatedAt() {
        JsonObject json = new JsonObject();
        json.addProperty("playerId", UUID.randomUUID().toString());
        json.addProperty("keyId", "test_key");
        json.addProperty("amount", 15);
        json.addProperty("updatedAt", java.time.Instant.now().toString());

        PlayerVirtualKeyBalance restored = PlayerVirtualKeyBalance.fromJson(json);
        assertEquals(15, restored.getAmount());
        assertNotNull(restored.getUpdatedAt());
    }
}
