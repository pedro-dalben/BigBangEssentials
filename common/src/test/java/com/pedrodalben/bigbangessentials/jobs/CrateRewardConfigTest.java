package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.rewards.CrateRewardDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.UnlockRequirements;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrateRewardConfigTest {

    @Test
    void parseValidCrateReward() {
        CrateRewardDefinition reward = new CrateRewardDefinition(
                List.of("BREAK-BLOCK", "COBBLEMON-CAPTURE"),
                "craft_key", 0.02, 2, 10, "iniciante", 5, 3600L
        );
        assertEquals("craft_key", reward.keyId());
        assertEquals(0.02, reward.chance(), 0.001);
        assertEquals(2, reward.amount());
        assertEquals(10, reward.minimumJobLevel());
        assertEquals("iniciante", reward.requiredRankId());
        assertEquals(5, reward.dailyLimit());
        assertEquals(3600L, reward.cooldownSeconds());
    }

    @Test
    void matchesAction() {
        CrateRewardDefinition reward = new CrateRewardDefinition(
                List.of("BREAK-BLOCK", "KILL-ENTITY"), "craft_key", 0.01, 1, 1, null, 3, 1800L
        );
        assertTrue(reward.matchesAction("BREAK-BLOCK"));
        assertTrue(reward.matchesAction("KILL-ENTITY"));
        assertFalse(reward.matchesAction("FISH"));
        assertTrue(reward.matchesAction("break-block")); // case insensitive
    }

    @Test
    void emptyActionsMatchAll() {
        CrateRewardDefinition reward = new CrateRewardDefinition(
                List.of(), "craft_key", 0.01, 1, 1, null, 3, 1800L
        );
        assertTrue(reward.matchesAction("ANYTHING"));
        assertTrue(reward.matchesAction("BREAK-BLOCK"));
    }

    @Test
    void invalidChanceThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new CrateRewardDefinition(List.of(), "k", -0.1, 1, 1, null, 3, 1800L)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new CrateRewardDefinition(List.of(), "k", 1.5, 1, 1, null, 3, 1800L)
        );
    }

    @Test
    void invalidAmountThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new CrateRewardDefinition(List.of(), "k", 0.1, 0, 1, null, 3, 1800L)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new CrateRewardDefinition(List.of(), "k", 0.1, -1, 1, null, 3, 1800L)
        );
    }

    @Test
    void blankKeyIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new CrateRewardDefinition(List.of(), "", 0.1, 1, 1, null, 3, 1800L)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new CrateRewardDefinition(List.of(), "  ", 0.1, 1, 1, null, 3, 1800L)
        );
    }

    @Test
    void unlockRequirementsDefaults() {
        UnlockRequirements req = UnlockRequirements.DEFAULT;
        assertTrue(req.unlockedByDefault());
        assertNull(req.requiredRankId());
        assertEquals(0, req.requiredRankOrder());
        assertNull(req.permission());
        assertFalse(req.hasRankRequirement());
        assertFalse(req.hasPermissionRequirement());
    }

    @Test
    void unlockRequirementsWithRank() {
        UnlockRequirements req = new UnlockRequirements(false, "veteran", 2, null);
        assertFalse(req.unlockedByDefault());
        assertEquals("veteran", req.requiredRankId());
        assertEquals(2, req.requiredRankOrder());
        assertTrue(req.hasRankRequirement());
        assertFalse(req.hasPermissionRequirement());
    }

    @Test
    void unlockRequirementsWithPermission() {
        UnlockRequirements req = new UnlockRequirements(false, null, 0, "bigbangessentials.jobs.profession.researcher");
        assertFalse(req.unlockedByDefault());
        assertFalse(req.hasRankRequirement());
        assertTrue(req.hasPermissionRequirement());
        assertEquals("bigbangessentials.jobs.profession.researcher", req.permission());
    }
}
