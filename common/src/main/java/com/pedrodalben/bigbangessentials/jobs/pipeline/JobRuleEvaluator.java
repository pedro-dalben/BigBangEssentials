package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

/**
 * Evaluates whether a job definition defines a reward rule for the given action.
 * Strict allowlist: exact match -> tag match -> typed default-reward.
 * Wildcard "*" is REJECTED for economic actions (BREAK_BLOCK, HARVEST_CROP, KILL_ENTITY, etc.)
 * Only BLOCKED_BY_ENVIRONMENT, DEGRADED, DISABLED states don't use wildcards for rewards.
 */
public class JobRuleEvaluator {
    private static final JobRuleEvaluator INSTANCE = new JobRuleEvaluator();

    public static JobRuleEvaluator getInstance() {
        return INSTANCE;
    }

    private JobRuleEvaluator() {}

    public MatchResult evaluate(JobDefinition jobDef, JobAction action) {
        if (jobDef == null || action == null || jobDef.actions == null) {
            return MatchResult.NO_MATCH;
        }

        String targetId = action.targetId();

        // 1. Check exact registry ID match
        Optional<EvaluatedRule> exactMatch = findExactMatch(jobDef, action, targetId);
        if (exactMatch.isPresent()) {
            return MatchResult.matched(exactMatch.get());
        }

        // 2. Check tag patterns (#namespace:path) with deterministic priority
        Optional<EvaluatedRule> tagMatch = findTagMatch(jobDef, action);
        if (tagMatch.isPresent()) {
            return MatchResult.matched(tagMatch.get());
        }

        // 3. Check typed default-reward (only for EXPLORE actions, never wildcard)
        Optional<EvaluatedRule> defaultReward = findDefaultReward(jobDef, action);
        if (defaultReward.isPresent()) {
            return MatchResult.matched(defaultReward.get());
        }

        // 4. Backward-compat: HARVEST_CROP falls back to BREAK_BLOCK entries (legacy configs)
        if (action.type() == com.pedrodalben.bigbangessentials.jobs.JobActionType.HARVEST_CROP) {
            Optional<EvaluatedRule> breakBlockFallback = findBreakBlockFallback(jobDef, targetId);
            if (breakBlockFallback.isPresent()) {
                return MatchResult.matched(breakBlockFallback.get());
            }
        }

        return MatchResult.NO_MATCH;
    }

    private Optional<EvaluatedRule> findExactMatch(JobDefinition jobDef, JobAction action, String targetId) {
        for (String configKey : action.type().getConfigKeys()) {
            ActionReward reward = jobDef.getReward(configKey, targetId);
            if (reward != null) {
                return Optional.of(new EvaluatedRule(reward, configKey, targetId));
            }
        }
        return Optional.empty();
    }

    private Optional<EvaluatedRule> findTagMatch(JobDefinition jobDef, JobAction action) {
        // Collect all tag matches first, then pick the best one
        Map<String, EvaluatedRule> matches = new LinkedHashMap<>();

        for (String configKey : action.type().getConfigKeys()) {
            Map<String, ActionReward> map = jobDef.actions.get(configKey);
            if (map != null) {
                for (Map.Entry<String, ActionReward> entry : map.entrySet()) {
                    String pattern = entry.getKey();
                    if (pattern.startsWith("#")) {
                        if (matchesTag(pattern, action)) {
                            matches.put(pattern, new EvaluatedRule(entry.getValue(), configKey, pattern));
                        }
                    }
                }
            }
        }

        if (matches.isEmpty()) return Optional.empty();

        // Deterministic: first match by insertion order (LinkedHashMap)
        return Optional.of(matches.values().iterator().next());
    }

    private Optional<EvaluatedRule> findDefaultReward(JobDefinition jobDef, JobAction action) {
        for (String configKey : action.type().getConfigKeys()) {
            ActionReward reward = jobDef.getDefaultReward(configKey);
            if (reward != null) {
                return Optional.of(new EvaluatedRule(reward, configKey, "default-reward"));
            }
        }
        return Optional.empty();
    }

    private Optional<EvaluatedRule> findBreakBlockFallback(JobDefinition jobDef, String targetId) {
        String breakBlockKey = com.pedrodalben.bigbangessentials.jobs.JobActionType.BREAK_BLOCK.getConfigKeys().get(0);
        ActionReward reward = jobDef.getReward(breakBlockKey, targetId);
        if (reward != null) {
            return Optional.of(new EvaluatedRule(reward, breakBlockKey, targetId));
        }
        return Optional.empty();
    }

    private boolean matchesTag(String pattern, JobAction action) {
        if (action.context().getTags() != null && !action.context().getTags().isEmpty()) {
            String cleanPattern = pattern.substring(1);
            for (String tag : action.context().getTags()) {
                if (tag.equalsIgnoreCase(cleanPattern) || tag.equalsIgnoreCase(pattern)
                        || tag.replace("minecraft:", "").equalsIgnoreCase(cleanPattern.replace("minecraft:", ""))) {
                    return true;
                }
            }
        }

        try {
            ResourceLocation targetLoc = ResourceLocation.tryParse(action.targetId());
            if (targetLoc == null) return false;

            return switch (action.type()) {
                case BREAK_BLOCK, HARVEST_CROP, PLACE_BLOCK -> {
                    Block block = BuiltInRegistries.BLOCK.get(targetLoc);
                    if (block != null) {
                        BlockState state = block.defaultBlockState();
                        yield JobsManager.blockMatches(state, pattern);
                    }
                    yield false;
                }
                case KILL_ENTITY -> {
                    EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.get(targetLoc);
                    if (et != null) {
                        yield JobsManager.entityMatches(et, pattern);
                    }
                    yield false;
                }
                case FISH, CRAFT_ITEM, SMELT_ITEM -> {
                    Item item = BuiltInRegistries.ITEM.get(targetLoc);
                    if (item != null) {
                        yield JobsManager.itemMatches(item.getDefaultInstance(), pattern);
                    }
                    yield false;
                }
                case USE_MAGIC -> {
                    Block block = BuiltInRegistries.BLOCK.get(targetLoc);
                    if (block != null) {
                        BlockState state = block.defaultBlockState();
                        yield JobsManager.blockMatches(state, pattern);
                    }
                    yield false;
                }
                default -> false;
            };
        } catch (Throwable ignored) {
            return false;
        }
    }

    public record EvaluatedRule(ActionReward reward, String matchedActionKey, String matchedTargetKey) {}

    public record MatchResult(EvaluatedRule rule, boolean isMatch, String reason) {
        public static final MatchResult NO_MATCH = new MatchResult(null, false, "NO_MATCHING_REWARD_RULE");

        public static MatchResult matched(EvaluatedRule rule) {
            return new MatchResult(rule, true, "MATCHED");
        }

        public static MatchResult blocked(String reason) {
            return new MatchResult(null, false, reason);
        }
    }
}
