package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemSerializerTest {

    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @Test
    void serialize_NullStack_ReturnsEmptyJson() {
        JsonObject json = ItemSerializer.serialize(null);
        assertTrue(json.has("empty"));
        assertTrue(json.get("empty").getAsBoolean());
    }

    @Test
    void serialize_EmptyStack_ReturnsEmptyJson() {
        JsonObject json = ItemSerializer.serialize(ItemStack.EMPTY);
        assertTrue(json.has("empty"));
        assertTrue(json.get("empty").getAsBoolean());
    }

    @Test
    void deserialize_NullJson_ReturnsEmptyStack() {
        ItemStack stack = ItemSerializer.deserialize(null);
        assertTrue(stack.isEmpty());
    }

    @Test
    void deserialize_EmptyJson_ReturnsEmptyStack() {
        JsonObject json = new JsonObject();
        json.addProperty("empty", true);
        ItemStack stack = ItemSerializer.deserialize(json);
        assertTrue(stack.isEmpty());
    }

    @Test
    void deserialize_EmptyJsonNoProperty_NoCrash() {
        JsonObject json = new JsonObject();
        json.addProperty("item", "minecraft:stone");
        try {
            ItemStack stack = ItemSerializer.deserialize(json);
            assertNotNull(stack);
        } catch (Exception e) {
            fail("Deserializing stone should not throw: " + e.getMessage());
        }
    }

    @Test
    void serialize_EmptyStack_DoesNotThrow() {
        assertDoesNotThrow(() -> ItemSerializer.serialize(ItemStack.EMPTY));
    }

    @Test
    void deserialize_NullItemValue_NoCrash() {
        JsonObject json = new JsonObject();
        json.addProperty("item", "minecraft:non_existent_item_xyz");
        try {
            ItemStack stack = ItemSerializer.deserialize(json);
            assertTrue(stack.isEmpty());
        } catch (Exception e) {
            fail("Deserializing non-existent item should not throw: " + e.getMessage());
        }
    }

    @Test
    void serializeList_EmptyList() {
        JsonArray array = ItemSerializer.serializeList(java.util.List.of());
        assertTrue(array.isEmpty());
    }

    @Test
    void serializeList_WithEmptyStacks() {
        JsonArray array = ItemSerializer.serializeList(java.util.List.of(ItemStack.EMPTY, ItemStack.EMPTY));
        assertEquals(2, array.size());
        for (var element : array) {
            assertTrue(element.getAsJsonObject().get("empty").getAsBoolean());
        }
    }

    @Test
    void deserializeList_EmptyArray() {
        JsonArray array = new JsonArray();
        var result = ItemSerializer.deserializeList(array);
        assertTrue(result.isEmpty());
    }

    @Test
    void deserializeList_InvalidElements_SkipsThem() {
        JsonArray array = new JsonArray();
        array.add("not an object");
        array.add(new JsonObject());
        var result = ItemSerializer.deserializeList(array);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isEmpty());
    }

    @Test
    void serializeAndDeserialize_RoundtripEmpty() {
        JsonObject json = ItemSerializer.serialize(ItemStack.EMPTY);
        ItemStack result = ItemSerializer.deserialize(json);
        assertTrue(result.isEmpty());
    }
}
