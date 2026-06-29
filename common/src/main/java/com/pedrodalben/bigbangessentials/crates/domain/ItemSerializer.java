package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
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
        json.addProperty("item", itemId.toString());
        json.addProperty("count", stack.getCount());

        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            DataComponentPatch.CODEC
                .encodeStart(JsonOps.INSTANCE, patch)
                .resultOrPartial(error -> LOGGER.error("Failed to serialize data components: {}", error))
                .ifPresent(components -> json.add("components", components));
        }

        return json;
    }

    public static ItemStack deserialize(JsonObject json) {
        if (json == null || json.has("empty")) {
            return ItemStack.EMPTY;
        }

        try {
            String itemString = json.get("item").getAsString();
            ResourceLocation itemId = ResourceLocation.parse(itemString);
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null) {
                return ItemStack.EMPTY;
            }

            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            ItemStack stack = new ItemStack(item, count);

            if (json.has("components")) {
                JsonElement componentsElement = json.get("components");
                DataComponentPatch.CODEC
                    .parse(JsonOps.INSTANCE, componentsElement)
                    .resultOrPartial(error -> LOGGER.error("Failed to deserialize data components: {}", error))
                    .ifPresent(stack::applyComponents);
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
