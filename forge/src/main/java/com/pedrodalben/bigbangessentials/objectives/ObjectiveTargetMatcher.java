package com.pedrodalben.bigbangessentials.objectives;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Generic registry/tag matcher for objective targets.
 * Extracted from Jobs matching logic so Jobs, RankUp, and future systems behave identically.
 */
public final class ObjectiveTargetMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectiveTargetMatcher.class);

    private ObjectiveTargetMatcher() {}

    public static boolean matchesBlock(BlockState state, String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        if (pattern.startsWith("#")) {
            ResourceLocation tagLoc = parse(pattern.substring(1));
            if (tagLoc == null) return false;
            TagKey<net.minecraft.world.level.block.Block> tagKey = TagKey.create(Registries.BLOCK, tagLoc);
            return state.is(tagKey);
        }
        ResourceLocation loc = parse(pattern);
        if (loc == null) return false;
        ResourceLocation blockLoc = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return blockLoc.equals(loc) || blockLoc.getPath().equals(loc.getPath());
    }

    public static boolean matchesEntity(EntityType<?> type, String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        if (pattern.startsWith("#")) {
            ResourceLocation tagLoc = parse(pattern.substring(1));
            if (tagLoc == null) return false;
            TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, tagLoc);
            return type.is(tagKey);
        }
        ResourceLocation loc = parse(pattern);
        if (loc == null) return false;
        ResourceLocation entityLoc = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return entityLoc.equals(loc) || entityLoc.getPath().equals(loc.getPath());
    }

    public static boolean matchesItem(ItemStack stack, String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        if (pattern.startsWith("#")) {
            ResourceLocation tagLoc = parse(pattern.substring(1));
            if (tagLoc == null) return false;
            TagKey<net.minecraft.world.item.Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
            return stack.is(tagKey);
        }
        ResourceLocation loc = parse(pattern);
        if (loc == null) return false;
        ResourceLocation itemLoc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemLoc.equals(loc) || itemLoc.getPath().equals(loc.getPath());
    }

    public static boolean matchesBiome(Holder<Biome> biomeHolder, String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        if (pattern.startsWith("#")) {
            ResourceLocation tagLoc = parse(pattern.substring(1));
            if (tagLoc == null) return false;
            TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, tagLoc);
            return biomeHolder.is(tagKey);
        }
        ResourceLocation loc = parse(pattern);
        if (loc == null) return false;
        return biomeHolder.is(ResourceKey.create(Registries.BIOME, loc));
    }

    public static boolean matchesAdvancement(String advancementId, String pattern) {
        if (advancementId == null || pattern == null) return false;
        return advancementId.equalsIgnoreCase(pattern);
    }

    @Nullable
    public static ResourceLocation parse(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            LOGGER.debug("Invalid registry ID format '{}'", id);
        }
        return loc;
    }

    public static boolean isValidTagSyntax(String pattern) {
        if (pattern == null || !pattern.startsWith("#")) return false;
        return parse(pattern.substring(1)) != null;
    }
}
