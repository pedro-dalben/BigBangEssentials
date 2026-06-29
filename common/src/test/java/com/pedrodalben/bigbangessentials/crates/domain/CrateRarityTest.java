package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrateRarityTest {

    @Test
    void constructor_ValidatesId() {
        CrateRarity rarity = new CrateRarity("legendary", "Legendary", "#FFD700", 5.0);
        assertEquals("legendary", rarity.getId());
        assertEquals("Legendary", rarity.getName());
        assertEquals("#FFD700", rarity.getColor());
        assertEquals(5.0, rarity.getWeight());
    }

    @Test
    void constructor_NullId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateRarity(null, "Test", "#FFFFFF", 1.0));
    }

    @Test
    void constructor_BlankId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateRarity(" ", "Test", "#FFFFFF", 1.0));
    }

    @Test
    void constructor_InvalidIdChars_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CrateRarity("special chars", "Test", "#FFFFFF", 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new CrateRarity("special!", "Test", "#FFFFFF", 1.0));
    }

    @Test
    void constructor_NormalizesId() {
        CrateRarity rarity = new CrateRarity("MY_Rarity-1", "My Rarity", "#FF0000", 1.0);
        assertEquals("my_rarity-1", rarity.getId());
    }

    @Test
    void constructor_UsesIdAsNameWhenNameNull() {
        CrateRarity rarity = new CrateRarity("uncommon", null, "#00FF00", 2.0);
        assertEquals("uncommon", rarity.getName());
    }

    @Test
    void constructor_ClampsWeightToZero() {
        CrateRarity rarity = new CrateRarity("negative", "Negative", "#FFFFFF", -5.0);
        assertEquals(0.0, rarity.getWeight(), 0.001);
    }

    @Test
    void constructor_DefaultColorWhenNull() {
        CrateRarity rarity = new CrateRarity("test", "Test", null, 1.0);
        assertEquals("#FFFFFF", rarity.getColor());
    }

    @Test
    void constructor_DefaultColorWhenBlank() {
        CrateRarity rarity = new CrateRarity("test", "Test", "  ", 1.0);
        assertEquals("#FFFFFF", rarity.getColor());
    }

    @Test
    void constructor_NormalizesColorWithHash() {
        CrateRarity rarity = new CrateRarity("test", "Test", "FFA500", 1.0);
        assertEquals("#FFA500", rarity.getColor());
    }

    @Test
    void constructor_UppercasesColor() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#ff69b4", 1.0);
        assertEquals("#FF69B4", rarity.getColor());
    }

    @Test
    void constructor_InvalidHexColorDefaultsToWhite() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#ZZZZZZ", 1.0);
        assertEquals("#FFFFFF", rarity.getColor());
    }

    @Test
    void constructor_WrongLengthColorDefaultsToWhite() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#FFF", 1.0);
        assertEquals("#FFFFFF", rarity.getColor());
    }

    @Test
    void constructor_SetsDefaultValues() {
        CrateRarity rarity = new CrateRarity("common", "Common", "#AAAAAA", 10.0);
        assertEquals(0, rarity.getPriority());
        assertEquals("minecraft:paper", rarity.getIcon());
        assertTrue(rarity.getLore().isEmpty());
        assertTrue(rarity.isActive());
        assertEquals(0, rarity.getDisplayOrder());
    }

    @Test
    void setColor_ValidatesAndNormalizes() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#FFFFFF", 1.0);
        rarity.setColor("00FF00");
        assertEquals("#00FF00", rarity.getColor());
        rarity.setColor(null);
        assertEquals("#FFFFFF", rarity.getColor());
    }

    @Test
    void setWeight_ClampsToZero() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#FFFFFF", 1.0);
        rarity.setWeight(-10.0);
        assertEquals(0.0, rarity.getWeight(), 0.001);
    }

    @Test
    void setActive_TogglesState() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#FFFFFF", 1.0);
        assertTrue(rarity.isActive());
        rarity.setActive(false);
        assertFalse(rarity.isActive());
    }

    @Test
    void setLore_ReturnsCopy() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#FFFFFF", 1.0);
        rarity.setLore(java.util.List.of("line1", "line2"));
        assertEquals(2, rarity.getLore().size());
        assertTrue(rarity.getLore().contains("line1"));
    }

    @Test
    void equals_SameId_AreEqual() {
        CrateRarity r1 = new CrateRarity("legendary", "Legendary", "#FFD700", 5.0);
        CrateRarity r2 = new CrateRarity("legendary", "Legendary Different", "#FF0000", 1.0);
        assertEquals(r1, r2);
    }

    @Test
    void equals_DifferentId_NotEqual() {
        CrateRarity r1 = new CrateRarity("legendary", "Legendary", "#FFD700", 5.0);
        CrateRarity r2 = new CrateRarity("epic", "Epic", "#AA00FF", 4.0);
        assertNotEquals(r1, r2);
    }

    @Test
    void equals_SameInstance_IsEqual() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#FFFFFF", 1.0);
        assertEquals(rarity, rarity);
    }

    @Test
    void equals_DifferentType_NotEqual() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#FFFFFF", 1.0);
        assertNotEquals(rarity, "test");
    }

    @Test
    void equals_Null_NotEqual() {
        CrateRarity rarity = new CrateRarity("test", "Test", "#FFFFFF", 1.0);
        assertNotEquals(rarity, null);
    }

    @Test
    void hashCode_ConsistentWithEquals() {
        CrateRarity r1 = new CrateRarity("legendary", "Legendary", "#FFD700", 5.0);
        CrateRarity r2 = new CrateRarity("legendary", "Legendary", "#FFD700", 10.0);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void toJson_Roundtrip() {
        CrateRarity original = new CrateRarity("rare", "Rare", "#0000FF", 3.0);
        original.setPriority(2);
        original.setIcon("minecraft:diamond");
        original.setDisplayOrder(1);
        original.setLore(java.util.List.of("A rare item"));

        JsonObject json = original.toJson();
        CrateRarity restored = CrateRarity.fromJson(json);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getColor(), restored.getColor());
        assertEquals(original.getWeight(), restored.getWeight(), 0.001);
        assertEquals(original.getPriority(), restored.getPriority());
        assertEquals(original.getIcon(), restored.getIcon());
        assertEquals(original.isActive(), restored.isActive());
        assertEquals(original.getDisplayOrder(), restored.getDisplayOrder());
        assertEquals(original.getLore(), restored.getLore());
    }

    @Test
    void fromJson_MinimalData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "common");
        CrateRarity rarity = CrateRarity.fromJson(json);
        assertEquals("common", rarity.getId());
        assertEquals("common", rarity.getName());
        assertEquals("#FFFFFF", rarity.getColor());
        assertEquals(1.0, rarity.getWeight(), 0.001);
        assertTrue(rarity.isActive());
    }

    @Test
    void fromJson_FullData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "epic");
        json.addProperty("name", "Epic");
        json.addProperty("color", "#AA00FF");
        json.addProperty("weight", 4.0);
        json.addProperty("priority", 3);
        json.addProperty("icon", "minecraft:emerald");
        json.addProperty("active", false);
        json.addProperty("displayOrder", 2);

        CrateRarity rarity = CrateRarity.fromJson(json);
        assertEquals("epic", rarity.getId());
        assertEquals("Epic", rarity.getName());
        assertEquals("#AA00FF", rarity.getColor());
        assertEquals(4.0, rarity.getWeight(), 0.001);
        assertEquals(3, rarity.getPriority());
        assertEquals("minecraft:emerald", rarity.getIcon());
        assertFalse(rarity.isActive());
        assertEquals(2, rarity.getDisplayOrder());
    }
}
