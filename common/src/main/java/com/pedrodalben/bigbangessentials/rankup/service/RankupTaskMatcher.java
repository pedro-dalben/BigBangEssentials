package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext;
import com.pedrodalben.bigbangessentials.objectives.ObjectiveTargetMatcher;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTask;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTaskFilter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class RankupTaskMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupTaskMatcher.class);

    private RankupTaskMatcher() {}

    public static boolean matches(RankupTask task, ObjectiveEventContext ctx) {
        if (task == null || ctx == null) return false;
        if (!task.enabled()) return false;
        if (task.type() != ctx.getActionType()) return false;

        RankupTaskFilter f = task.filters();
        return switch (task.type()) {
            case BREAK_BLOCK -> matchesBlock(ctx.getTarget(), f);
            case PLACE_BLOCK -> matchesBlock(ctx.getTarget(), f);
            case KILL_ENTITY -> matchesEntity(ctx.getTarget(), f);
            case FISH -> matchesItem(ctx.getTarget(), f) && passesFishOnly(ctx.getTarget(), f);
            case CRAFT_ITEM, SMELT_ITEM -> matchesItem(ctx.getTarget(), f);
            case VISIT_BIOME -> matchesBiome(ctx.getTarget(), f);
            case ADVANCEMENT -> matchesAdvancement(ctx.getRegistryId(), f);
            case PLAYTIME_MINUTES -> true;
            case COBBLEMON_CAPTURE, COBBLEMON_BATTLE_WIN, COBBLEMON_DEFEAT, COBBLEMON_HATCH_EGG ->
                    matchesCobblemon(ctx, f);
            default -> false;
        };
    }

    private static boolean matchesBlock(@Nullable Object target, RankupTaskFilter f) {
        if (!(target instanceof BlockState state)) return false;
        if (f.blocks().isEmpty()) return true;
        for (String pattern : f.blocks()) {
            if (ObjectiveTargetMatcher.matchesBlock(state, pattern)) return true;
        }
        return false;
    }

    private static boolean matchesEntity(@Nullable Object target, RankupTaskFilter f) {
        if (!(target instanceof EntityType<?> type)) return false;
        if (f.entities().isEmpty()) return true;
        for (String pattern : f.entities()) {
            if (ObjectiveTargetMatcher.matchesEntity(type, pattern)) return true;
        }
        return false;
    }

    private static boolean matchesItem(@Nullable Object target, RankupTaskFilter f) {
        if (!(target instanceof ItemStack stack)) return false;
        if (f.items().isEmpty()) return true;
        for (String pattern : f.items()) {
            if (ObjectiveTargetMatcher.matchesItem(stack, pattern)) return true;
        }
        return false;
    }

    private static boolean passesFishOnly(@Nullable Object target, RankupTaskFilter f) {
        if (f.fishOnly() == null || !f.fishOnly()) return true;
        if (!(target instanceof ItemStack stack)) return false;
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return loc.getPath().contains("fish") || loc.getPath().contains("bucket");
    }

    private static boolean matchesBiome(@Nullable Object target, RankupTaskFilter f) {
        if (!(target instanceof Holder<?> holder) || !(holder.value() instanceof Biome)) return false;
        @SuppressWarnings("unchecked")
        Holder<Biome> biomeHolder = (Holder<Biome>) holder;
        if (f.biomes().isEmpty()) return true;
        for (String pattern : f.biomes()) {
            if (ObjectiveTargetMatcher.matchesBiome(biomeHolder, pattern)) return true;
        }
        return false;
    }

    private static boolean matchesAdvancement(@Nullable String advancementId, RankupTaskFilter f) {
        if (advancementId == null) return false;
        if (f.advancements().isEmpty()) return true;
        for (String pattern : f.advancements()) {
            if (ObjectiveTargetMatcher.matchesAdvancement(advancementId, pattern)) return true;
        }
        return false;
    }

    private static boolean matchesCobblemon(ObjectiveEventContext ctx, RankupTaskFilter f) {
        Object target = ctx.getTarget();
        if (target instanceof CobblemonCaptureEventData data) {
            if (f.species() != null && !f.species().isEmpty() && !f.species().contains(data.species())) return false;
            if (f.types() != null && !f.types().isEmpty() && data.types().stream().noneMatch(t -> f.types().contains(t))) return false;
            if (f.legendary() != null && f.legendary() != data.legendary()) return false;
            if (f.shiny() != null && f.shiny() != data.shiny()) return false;
            return true;
        }
        return true; // If no detailed data, accept and let bridge validate
    }

    public record CobblemonCaptureEventData(String species, java.util.List<String> types, boolean legendary, boolean shiny) {}
}
