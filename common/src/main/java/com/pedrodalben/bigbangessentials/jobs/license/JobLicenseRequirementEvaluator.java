package com.pedrodalben.bigbangessentials.jobs.license;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Evaluates whether a valid JobAction matches the requirements of a Job License objective.
 */
public class JobLicenseRequirementEvaluator {
    private static final JobLicenseRequirementEvaluator INSTANCE = new JobLicenseRequirementEvaluator();

    public static JobLicenseRequirementEvaluator getInstance() {
        return INSTANCE;
    }

    private JobLicenseRequirementEvaluator() {}

    public boolean evaluate(JobAction action, JobLicenseObjective objective) {
        if (action == null || objective == null) return false;

        // 1. Check action type match
        boolean typeMatches = action.type().name().equalsIgnoreCase(objective.actionType());
        if (!typeMatches) {
            for (String alias : action.type().getConfigKeys()) {
                if (alias.equalsIgnoreCase(objective.actionType()) || alias.replace('-', '_').equalsIgnoreCase(objective.actionType())) {
                    typeMatches = true;
                    break;
                }
            }
        }
        if (!typeMatches) return false;

        // 2. Check non-player placed requirement
        if (objective.requireNonPlayerPlaced() && action.context() != null && action.context().isPlayerPlacedBlock()) {
            return false;
        }

        // 3. Check mature crop requirement
        if (objective.requireMature() && action.context() != null && !action.context().isCropMature()) {
            return false;
        }

        // 4. If no target restrictions specified, any target of this action type matches
        if (objective.matchTargetIds().isEmpty() && objective.matchTags().isEmpty()) {
            return true;
        }

        String targetId = action.targetId();

        // Check explicit target ID list
        for (String tid : objective.matchTargetIds()) {
            if (tid.equalsIgnoreCase(targetId)) {
                return true;
            }
        }

        // Check tag list
        for (String tag : objective.matchTags()) {
            String pattern = tag.startsWith("#") ? tag : "#" + tag;
            if (matchesTag(pattern, action)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesTag(String pattern, JobAction action) {
        if (action.context() != null && action.context().getTags() != null) {
            String cleanPattern = pattern.startsWith("#") ? pattern.substring(1) : pattern;
            for (String ctxTag : action.context().getTags()) {
                if (ctxTag.equalsIgnoreCase(cleanPattern) || ctxTag.equalsIgnoreCase(pattern) ||
                    ctxTag.replace("minecraft:", "").equalsIgnoreCase(cleanPattern.replace("minecraft:", ""))) {
                    return true;
                }
            }
        }

        try {
            ResourceLocation targetLoc = ResourceLocation.tryParse(action.targetId());
            if (targetLoc == null) return false;

            JobActionType type = action.type();
            if (type == JobActionType.BREAK_BLOCK || type == JobActionType.HARVEST_CROP || type == JobActionType.PLACE_BLOCK) {
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
            } else if (type == JobActionType.FISH || type == JobActionType.CRAFT_ITEM || type == JobActionType.SMELT_ITEM) {
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(targetLoc);
                if (item != null) {
                    ItemStack stack = item.getDefaultInstance();
                    return JobsManager.itemMatches(stack, pattern);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
