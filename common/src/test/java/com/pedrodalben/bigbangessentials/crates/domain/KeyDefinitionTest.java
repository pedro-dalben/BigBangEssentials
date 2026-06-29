package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyDefinitionTest {

    @Test
    void constructor_SetsFields() {
        KeyDefinition key = new KeyDefinition("vip_key", "VIP Key");
        assertEquals("vip_key", key.getId());
        assertEquals("VIP Key", key.getName());
    }

    @Test
    void constructor_NullId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new KeyDefinition(null, "Test"));
    }

    @Test
    void constructor_BlankId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new KeyDefinition("", "Test"));
        assertThrows(IllegalArgumentException.class, () -> new KeyDefinition("   ", "Test"));
    }

    @Test
    void constructor_InvalidIdChars_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new KeyDefinition("spaces are bad", "Test"));
        assertThrows(IllegalArgumentException.class, () -> new KeyDefinition("special!", "Test"));
    }

    @Test
    void constructor_NormalizesId() {
        KeyDefinition key = new KeyDefinition("MY_KEY-1", "My Key");
        assertEquals("my_key-1", key.getId());
    }

    @Test
    void constructor_UsesIdAsNameWhenNameNull() {
        KeyDefinition key = new KeyDefinition("auto_key", null);
        assertEquals("auto_key", key.getName());
    }

    @Test
    void constructor_SetsDefaults() {
        KeyDefinition key = new KeyDefinition("test_key", "Test Key");
        assertTrue(key.isActive());
        assertFalse(key.isVirtual());
        assertTrue(key.getCompatibleCrateIds().isEmpty());
        assertTrue(key.getLore().isEmpty());
        assertEquals("", key.getRequiredPermission());
        assertEquals("", key.getGiveSound());
        assertEquals("", key.getTakeSound());
        assertTrue(key.getGiveCommands().isEmpty());
        assertNull(key.getPhysicalItem());
        assertNotNull(key.getCreatedAt());
        assertNotNull(key.getUpdatedAt());
    }

    @Test
    void setVirtual_Toggles() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        assertFalse(key.isVirtual());
        key.setVirtual(true);
        assertTrue(key.isVirtual());
        key.setVirtual(false);
        assertFalse(key.isVirtual());
    }

    @Test
    void setActive_Toggles() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        assertTrue(key.isActive());
        key.setActive(false);
        assertFalse(key.isActive());
    }

    @Test
    void compatibleCrateIds_AddAndRemove() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        assertTrue(key.getCompatibleCrateIds().isEmpty());

        key.addCompatibleCrateId("crate_vip");
        key.addCompatibleCrateId("crate_daily");
        assertEquals(2, key.getCompatibleCrateIds().size());
        assertTrue(key.getCompatibleCrateIds().contains("crate_vip"));
    }

    @Test
    void compatibleCrateIds_NoDuplicates() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        key.addCompatibleCrateId("crate_vip");
        key.addCompatibleCrateId("crate_vip");
        assertEquals(1, key.getCompatibleCrateIds().size());
    }

    @Test
    void compatibleCrateIds_Remove() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        key.addCompatibleCrateId("crate_vip");
        key.addCompatibleCrateId("crate_daily");
        key.removeCompatibleCrateId("crate_vip");
        assertEquals(1, key.getCompatibleCrateIds().size());
        assertFalse(key.getCompatibleCrateIds().contains("crate_vip"));
    }

    @Test
    void compatibleCrateIds_RemoveNonExistent() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        key.addCompatibleCrateId("crate_vip");
        key.removeCompatibleCrateId("nonexistent");
        assertEquals(1, key.getCompatibleCrateIds().size());
    }

    @Test
    void setCompatibleCrateIds_ReplacesList() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        key.addCompatibleCrateId("old_crate");
        key.setCompatibleCrateIds(java.util.List.of("crate1", "crate2"));
        assertEquals(2, key.getCompatibleCrateIds().size());
        assertTrue(key.getCompatibleCrateIds().contains("crate1"));
        assertFalse(key.getCompatibleCrateIds().contains("old_crate"));
    }

    @Test
    void setCompatibleCrateIds_NullClears() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        key.addCompatibleCrateId("crate_vip");
        key.setCompatibleCrateIds(null);
        assertTrue(key.getCompatibleCrateIds().isEmpty());
    }

    @Test
    void getCompatibleCrateIds_ReturnsCopy() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        key.addCompatibleCrateId("crate_vip");
        var ids = key.getCompatibleCrateIds();
        ids.add("injected");
        assertEquals(1, key.getCompatibleCrateIds().size());
    }

    @Test
    void setRequiredPermission_Updates() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        assertEquals("", key.getRequiredPermission());
        key.setRequiredPermission("bigbangessentials.key.test_key");
        assertEquals("bigbangessentials.key.test_key", key.getRequiredPermission());
    }

    @Test
    void toJson_Roundtrip() {
        KeyDefinition original = new KeyDefinition("event_key", "Event Key");
        original.setActive(true);
        original.setVirtual(true);
        original.setRequiredPermission("event.permission");
        original.setGiveSound("minecraft:entity.experience_orb.pickup");
        original.setTakeSound("minecraft:entity.item.pickup");
        original.setLore(java.util.List.of("An event key"));
        original.addCompatibleCrateId("crate_event");
        original.addCompatibleCrateId("crate_special");
        original.setGiveCommands(java.util.List.of("say {player} got a key!"));

        JsonObject json = original.toJson();
        KeyDefinition restored = KeyDefinition.fromJson(json);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.isActive(), restored.isActive());
        assertEquals(original.isVirtual(), restored.isVirtual());
        assertEquals(original.getRequiredPermission(), restored.getRequiredPermission());
        assertEquals(original.getGiveSound(), restored.getGiveSound());
        assertEquals(original.getTakeSound(), restored.getTakeSound());
        assertEquals(original.getLore(), restored.getLore());
        assertEquals(original.getCompatibleCrateIds(), restored.getCompatibleCrateIds());
        assertEquals(original.getGiveCommands(), restored.getGiveCommands());
    }

    @Test
    void fromJson_MinimalData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "simple_key");
        json.addProperty("name", "Simple Key");

        KeyDefinition key = KeyDefinition.fromJson(json);
        assertEquals("simple_key", key.getId());
        assertEquals("Simple Key", key.getName());
        assertTrue(key.isActive());
        assertFalse(key.isVirtual());
        assertTrue(key.getCompatibleCrateIds().isEmpty());
    }

    @Test
    void fromJson_MissingName_UsesId() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "key_only");

        KeyDefinition key = KeyDefinition.fromJson(json);
        assertEquals("key_only", key.getId());
        assertEquals("key_only", key.getName());
    }

    @Test
    void getLore_ReturnsCopy() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        key.setLore(java.util.List.of("line1"));
        var lore = key.getLore();
        lore.add("injected");
        assertEquals(1, key.getLore().size());
    }

    @Test
    void getGiveCommands_ReturnsCopy() {
        KeyDefinition key = new KeyDefinition("test_key", "Test");
        key.setGiveCommands(java.util.List.of("cmd1"));
        var cmds = key.getGiveCommands();
        cmds.add("injected");
        assertEquals(1, key.getGiveCommands().size());
    }
}
