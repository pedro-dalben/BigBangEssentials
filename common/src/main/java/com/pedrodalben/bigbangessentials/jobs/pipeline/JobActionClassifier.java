package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionContext;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.CropHarvestValidationService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.UUID;

/**
 * Classifies raw platform events into semantic job action types.
 * Does not know about XP, money, or job configuration.
 */
public class JobActionClassifier {
    private static final JobActionClassifier INSTANCE = new JobActionClassifier();

    public static JobActionClassifier getInstance() {
        return INSTANCE;
    }

    private JobActionClassifier() {}

    public ClassifiedAction classify(RawJobEvent event) {
        if (event == null) return ClassifiedAction.noAction();

        String source = event.eventSource().toUpperCase();
        String regId = event.registryId();

        return switch (source) {
            case "BLOCK_BREAK" -> classifyBlockBreak(event, regId);
            case "BLOCK_PLACE" -> classifyPlace(event);
            case "ENTITY_DEATH" -> classifyKill(event, regId);
            case "FISHING" -> classifyFish(event, regId);
            case "CRAFTING" -> classifyCraft(event, regId);
            case "SMELTING" -> classifySmelt(event, regId);
            case "EXPLORATION_BIOME" -> exploreBiome(event, regId);
            case "EXPLORATION_CELL" -> exploreCell(event, regId);
            case "EXPLORATION_STRUCTURE" -> exploreStructure(event, regId);
            default -> ClassifiedAction.noAction();
        };
    }

    private ClassifiedAction classifyBlockBreak(RawJobEvent event, String regId) {
        // Check if it's a mature crop -> HARVEST_CROP
        if (isHarvestableCrop(event)) {
            return ClassifiedAction.of(JobActionType.HARVEST_CROP, regId, buildContext(event));
        }
        // Otherwise it's a standard block break
        return ClassifiedAction.of(JobActionType.BREAK_BLOCK, regId, buildContext(event));
    }

    private boolean isHarvestableCrop(RawJobEvent event) {
        if (event.registryId() == null || event.registryId().isEmpty()) return false;
        try {
            var loc = net.minecraft.resources.ResourceLocation.tryParse(event.registryId());
            if (loc == null) return false;
            var block = BuiltInRegistries.BLOCK.get(loc);
            if (block == null) return false;
            BlockState state = block.defaultBlockState();
            return CropHarvestValidationService.getInstance().isCrop(state);
        } catch (Throwable e) {
            return false;
        }
    }

    private ClassifiedAction classifyPlace(RawJobEvent event) {
        return ClassifiedAction.of(JobActionType.PLACE_BLOCK, event.registryId(), buildContext(event));
    }

    private ClassifiedAction classifyKill(RawJobEvent event, String regId) {
        return ClassifiedAction.of(JobActionType.KILL_ENTITY, regId, buildContext(event));
    }

    private ClassifiedAction classifyFish(RawJobEvent event, String regId) {
        return ClassifiedAction.of(JobActionType.FISH, regId, buildContext(event));
    }

    private ClassifiedAction classifyCraft(RawJobEvent event, String regId) {
        return ClassifiedAction.of(JobActionType.CRAFT_ITEM, regId, buildContext(event));
    }

    private ClassifiedAction classifySmelt(RawJobEvent event, String regId) {
        return ClassifiedAction.of(JobActionType.SMELT_ITEM, regId, buildContext(event));
    }

    private ClassifiedAction exploreBiome(RawJobEvent event, String regId) {
        JobActionContext ctx = JobActionContext.builder()
                .dimension(event.dimension())
                .position(event.position())
                .biome(regId)
                .firstDiscovery(true)
                .eventSource(event.eventSource())
                .build();
        return ClassifiedAction.of(JobActionType.EXPLORE, regId, ctx);
    }

    private ClassifiedAction exploreCell(RawJobEvent event, String regId) {
        JobActionContext ctx = JobActionContext.builder()
                .dimension(event.dimension())
                .position(event.position())
                .firstDiscovery(true)
                .eventSource(event.eventSource())
                .build();
        return ClassifiedAction.of(JobActionType.EXPLORE, regId, ctx);
    }

    private ClassifiedAction exploreStructure(RawJobEvent event, String regId) {
        JobActionContext ctx = JobActionContext.builder()
                .dimension(event.dimension())
                .position(event.position())
                .structure(regId)
                .firstDiscovery(true)
                .eventSource(event.eventSource())
                .build();
        return ClassifiedAction.of(JobActionType.EXPLORE, regId, ctx);
    }

    private JobActionContext buildContext(RawJobEvent event) {
        return JobActionContext.builder()
                .dimension(event.dimension())
                .position(event.position())
                .blockId(event.registryId())
                .eventSource(event.eventSource())
                .build();
    }

    public record ClassifiedAction(JobActionType actionType, String targetId, JobActionContext context, boolean isRecompensable) {
        public static ClassifiedAction of(JobActionType type, String target, JobActionContext ctx) {
            return new ClassifiedAction(type, target != null ? target : "", ctx != null ? ctx : JobActionContext.empty(), type != null);
        }

        public static ClassifiedAction noAction() {
            return new ClassifiedAction(null, "", JobActionContext.empty(), false);
        }

        public JobAction toJobAction(UUID playerId) {
            if (!isRecompensable || actionType == null) {
                throw new IllegalStateException("Cannot create JobAction from non-recompensable classification");
            }
            return JobAction.create(playerId, actionType, "CLASSIFIER", targetId, context);
        }
    }
}
