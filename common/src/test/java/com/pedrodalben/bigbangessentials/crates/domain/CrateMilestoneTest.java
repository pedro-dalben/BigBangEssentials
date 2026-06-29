package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrateMilestoneTest {

    @Test
    void constructor_SetsFields() {
        CrateMilestone milestone = new CrateMilestone("milestone_10", "10 Openings", "reward_diamond", 10);
        assertEquals("milestone_10", milestone.getId());
        assertEquals("10 Openings", milestone.getName());
        assertEquals("reward_diamond", milestone.getRewardId());
        assertEquals(10, milestone.getRequiredOpenings());
    }

    @Test
    void constructor_NullId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateMilestone(null, "Test", "reward_1", 5));
    }

    @Test
    void constructor_BlankId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateMilestone("", "Test", "reward_1", 5));
    }

    @Test
    void constructor_InvalidIdChars_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateMilestone("INVALID!", "Test", "reward_1", 5));
    }

    @Test
    void constructor_ValidatesRewardId() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateMilestone("m1", "Test", null, 5));
    }

    @Test
    void constructor_ClampsRequiredOpeningsToMinimumOne() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 0);
        assertEquals(1, milestone.getRequiredOpenings());

        milestone = new CrateMilestone("m2", "Test", "reward_1", -5);
        assertEquals(1, milestone.getRequiredOpenings());
    }

    @Test
    void constructor_UsesIdAsNameWhenNameNull() {
        CrateMilestone milestone = new CrateMilestone("auto_name", null, "reward_1", 5);
        assertEquals("auto_name", milestone.getName());
    }

    @Test
    void constructor_SetsDefaults() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 5);
        assertEquals("", milestone.getDescription());
        assertFalse(milestone.isRepeatable());
        assertTrue(milestone.isActive());
        assertEquals(0, milestone.getDisplayOrder());
    }

    @Test
    void getProgressPercent_ZeroOpenings() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertEquals(0.0, milestone.getProgressPercent(0), 0.001);
    }

    @Test
    void getProgressPercent_Partial() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertEquals(50.0, milestone.getProgressPercent(5), 0.001);
    }

    @Test
    void getProgressPercent_Exact() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertEquals(100.0, milestone.getProgressPercent(10), 0.001);
    }

    @Test
    void getProgressPercent_Overflow() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertEquals(100.0, milestone.getProgressPercent(20), 0.001);
    }

    @Test
    void getProgressPercent_OneRequiredOpening() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 1);
        assertEquals(100.0, milestone.getProgressPercent(1), 0.001);
        assertEquals(0.0, milestone.getProgressPercent(0), 0.001);
    }

    @Test
    void getOpeningsRemaining_NotReached() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertEquals(7, milestone.getOpeningsRemaining(3));
    }

    @Test
    void getOpeningsRemaining_ExactReached() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertEquals(0, milestone.getOpeningsRemaining(10));
    }

    @Test
    void getOpeningsRemaining_Overflow() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertEquals(0, milestone.getOpeningsRemaining(15));
    }

    @Test
    void getOpeningsRemaining_ZeroOpenings() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertEquals(10, milestone.getOpeningsRemaining(0));
    }

    @Test
    void isReached_NotReached_ReturnsFalse() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertFalse(milestone.isReached(5));
    }

    @Test
    void isReached_Exact_ReturnsTrue() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertTrue(milestone.isReached(10));
    }

    @Test
    void isReached_Overflow_ReturnsTrue() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertTrue(milestone.isReached(15));
    }

    @Test
    void isReached_ZeroOpenings() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertFalse(milestone.isReached(0));
    }

    @Test
    void setRepeatable_Toggles() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertFalse(milestone.isRepeatable());
        milestone.setRepeatable(true);
        assertTrue(milestone.isRepeatable());
    }

    @Test
    void setActive_Toggles() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        assertTrue(milestone.isActive());
        milestone.setActive(false);
        assertFalse(milestone.isActive());
    }

    @Test
    void setRequiredOpenings_ClampsToMinimumOne() {
        CrateMilestone milestone = new CrateMilestone("m1", "Test", "reward_1", 10);
        milestone.setRequiredOpenings(-1);
        assertEquals(1, milestone.getRequiredOpenings());
    }

    @Test
    void toJson_Roundtrip() {
        CrateMilestone original = new CrateMilestone("milestone_50", "50 Openings", "reward_legendary", 50);
        original.setDescription("Reach 50 openings");
        original.setRepeatable(true);
        original.setActive(false);
        original.setDisplayOrder(3);

        JsonObject json = original.toJson();
        CrateMilestone restored = CrateMilestone.fromJson(json);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getDescription(), restored.getDescription());
        assertEquals(original.getRewardId(), restored.getRewardId());
        assertEquals(original.getRequiredOpenings(), restored.getRequiredOpenings());
        assertEquals(original.isRepeatable(), restored.isRepeatable());
        assertEquals(original.isActive(), restored.isActive());
        assertEquals(original.getDisplayOrder(), restored.getDisplayOrder());
    }

    @Test
    void fromJson_MinimalData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "m1");
        json.addProperty("rewardId", "reward_test");
        CrateMilestone milestone = CrateMilestone.fromJson(json);
        assertEquals("m1", milestone.getId());
        assertEquals("m1", milestone.getName());
        assertEquals("reward_test", milestone.getRewardId());
        assertEquals(1, milestone.getRequiredOpenings());
        assertFalse(milestone.isRepeatable());
        assertTrue(milestone.isActive());
    }

    @Test
    void fromJson_FullData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "m2");
        json.addProperty("name", "Milestone 2");
        json.addProperty("description", "Second milestone");
        json.addProperty("rewardId", "reward_epic");
        json.addProperty("requiredOpenings", 25);
        json.addProperty("repeatable", true);
        json.addProperty("active", false);
        json.addProperty("displayOrder", 2);

        CrateMilestone milestone = CrateMilestone.fromJson(json);
        assertEquals("m2", milestone.getId());
        assertEquals("Milestone 2", milestone.getName());
        assertEquals("Second milestone", milestone.getDescription());
        assertEquals("reward_epic", milestone.getRewardId());
        assertEquals(25, milestone.getRequiredOpenings());
        assertTrue(milestone.isRepeatable());
        assertFalse(milestone.isActive());
        assertEquals(2, milestone.getDisplayOrder());
    }
}
