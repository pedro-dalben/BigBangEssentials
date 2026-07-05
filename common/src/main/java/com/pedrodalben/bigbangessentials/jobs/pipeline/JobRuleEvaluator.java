package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;

/**
 * Evaluates whether a job definition defines a reward rule for the given action.
 * Supports exact target matches, aliases, and tag patterns without altering legacy configuration formats.
 */
public class JobRuleEvaluator {
    private static final JobRuleEvaluator INSTANCE = new JobRuleEvaluator();

    public static JobRuleEvaluator getInstance() {
        return INSTANCE;
    }

    private JobRuleEvaluator() {}

    public Optional<EvaluatedRule> evaluate(JobDefinition jobDef, JobAction action) {
        if (jobDef == null || action == null || jobDef.actions == null) {
            return Optional.empty();
        }

        String targetId = action.targetId();

        // 1. Check exact match across all config aliases for this action type
        for (String configKey : action.type().getConfigKeys()) {
            ActionReward reward = jobDef.getReward(configKey, targetId);
            if (reward != null) {
                return Optional.of(new EvaluatedRule(reward, configKey, targetId));
            }
        }

        // 2. Check tag patterns across all config aliases
        for (String configKey : action.type().getConfigKeys()) {
            Map<String, ActionReward> map = jobDef.actions.get(configKey.toUpperCase().replace('_', '-'));
            if (map != null) {
                for (Map.Entry<String, ActionReward> entry : map.entrySet()) {
                    String pattern = entry.getKey();
                    if (pattern.startsWith("#")) {
                        if (matchesTag(pattern, action)) {
                            return Optional.of(new EvaluatedRule(entry.getValue(), configKey, pattern));
                        }
                    }
                }
            }
        }

        // 3. Check wildcard across all config aliases
        for (String configKey : action.type().getConfigKeys()) {
            ActionReward reward = jobDef.getWildcardReward(configKey);
            if (reward != null) {
                return Optional.of(new EvaluatedRule(reward, configKey, "*"));
            }
        }

        return Optional.empty();
    }

    private boolean matchesTag(String pattern, JobAction action) {
        // First check explicit context tags
        if (action.context().getTags() != null && !action.context().getTags().isEmpty()) {
            String cleanPattern = pattern.substring(1);
            for (String tag : action.context().getTags()) {
                if (tag.equalsIgnoreCase(cleanPattern) || tag.equalsIgnoreCase(pattern) ||
                    tag.replace("minecraft:", "").equalsIgnoreCase(cleanPattern.replace("minecraft:", ""))) {
                    return true;
                }
            }
        }

        // Fallback: Check Minecraft BuiltInRegistries for block/item/entity tags
        try {
            ResourceLocation targetLoc = ResourceLocation.tryParse(action.targetId());
            if (targetLoc == null) return false;

            JobActionType type = action.type();
            if (type == JobActionType.BREAK_BLOCK || type == JobActionType.HARVEST_CROP ||
                type == JobActionType.PLACE_BLOCK) {
                Block block = BuiltInRegistries.BLOCK.get(targetLoc);
                if (block != null) {
                    BlockState state = block.defaultBlockState();
                    return JobsManager.blockMatches(state, pattern);
                }
            } else if (type == JobActionType.KILL_ENTITY) {
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(targetLoc);
                if (entityType != null) {
                    return JobsManager.entityMatches(entityType, pattern);
                }
            } else if (type == JobActionType.FISH ||
                       type == JobActionType.CRAFT_ITEM ||
                       type == JobActionType.SMELT_ITEM) {
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(targetLoc);
                if (item != null) {
                    ItemStack stack = item.getDefaultInstance();
                    return JobsManager.itemMatches(stack, pattern);
                }
            }
        } catch (Throwable ignored) {
            // Registry lookup might fail in mock tests or unsupported types
        }

        return false;
    }

    public record EvaluatedRule(ActionReward reward, String matchedActionKey, String matchedTargetKey) {}
}
