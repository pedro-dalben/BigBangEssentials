package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.util.ResourceLocationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemSerializer.class);

    private ItemSerializer() {}

    public static JsonObject serialize(ItemStack stack) {
        JsonObject json = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            json.addProperty("empty", true);
            return json;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        json.addProperty("item", itemId != null ? itemId.toString() : "minecraft:air");
        json.addProperty("count", stack.getCount());

        if (stack.hasTag()) {
            json.addProperty("nbt", stack.getTag().toString());
        }

        return json;
    }

    public static ItemStack deserialize(JsonObject json) {
        if (json == null || json.has("empty")) {
            return ItemStack.EMPTY;
        }

        try {
            String itemString = json.get("item").getAsString();
            ResourceLocation itemId = ResourceLocationHelper.parse(itemString);
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null) {
                return ItemStack.EMPTY;
            }

            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            ItemStack stack = new ItemStack(item, count);

            if (json.has("nbt")) {
                try {
                    CompoundTag tag = TagParser.parseTag(json.get("nbt").getAsString());
                    stack.setTag(tag);
                } catch (Exception e) {
                    LOGGER.error("Failed to deserialize NBT: {}", e.getMessage());
                }
            }

            return stack;
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize ItemStack from JSON: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    public static JsonArray serializeList(java.util.List<ItemStack> stacks) {
        JsonArray array = new JsonArray();
        for (ItemStack stack : stacks) {
            array.add(serialize(stack));
        }
        return array;
    }

    public static java.util.List<ItemStack> deserializeList(JsonArray array) {
        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                result.add(deserialize(element.getAsJsonObject()));
            }
        }
        return result;
    }
}
