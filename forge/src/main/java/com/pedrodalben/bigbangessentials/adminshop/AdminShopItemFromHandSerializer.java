package com.pedrodalben.bigbangessentials.adminshop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.crates.domain.ItemSerializer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public final class AdminShopItemFromHandSerializer {
    private static final Gson GSON = new Gson();

    private AdminShopItemFromHandSerializer() {}

    public static JsonObject serializeItem(ItemStack stack) {
        return ItemSerializer.serialize(stack);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> serializeAsMap(ItemStack stack) {
        JsonObject json = ItemSerializer.serialize(stack);
        return GSON.fromJson(json.toString(), Map.class);
    }

    public static String effectiveItemId(ItemStack stack) {
        if (stack.isEmpty()) return "minecraft:stone";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String effectiveDisplayName(ItemStack stack) {
        return stack.getHoverName().getString();
    }
}
