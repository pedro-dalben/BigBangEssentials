package com.pedrodalben.bigbangessentials.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Optional;

public class EnchantmentUtils {

    public static Enchantment getEnchantment(MinecraftServer server, String namespace, String path) {
        ResourceLocation id = ResourceLocationHelper.create(namespace, path);
        Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
        if (enchantment == null) {
            throw new IllegalArgumentException("Enchantment not found: " + id);
        }
        return enchantment;
    }

    public static Optional<Enchantment> getEnchantmentSafely(MinecraftServer server, String namespace, String path) {
        try {
            ResourceLocation id = ResourceLocationHelper.create(namespace, path);
            return BuiltInRegistries.ENCHANTMENT.getOptional(id);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<Enchantment> getEnchantment(MinecraftServer server, ResourceLocation location) {
        try {
            return BuiltInRegistries.ENCHANTMENT.getOptional(location);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
