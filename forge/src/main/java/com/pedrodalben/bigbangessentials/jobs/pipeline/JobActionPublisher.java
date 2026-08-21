package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Singleton gateway decoupling event listeners and external triggers from the job processing pipeline.
 * Converts legacy trigger calls to normalized JobActions and routes them through JobActionProcessor.
 */
public class JobActionPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobActionPublisher.class);
    private static final JobActionPublisher INSTANCE = new JobActionPublisher();

    private final AtomicLong publishedCount = new AtomicLong(0);

    public static JobActionPublisher getInstance() {
        return INSTANCE;
    }

    private JobActionPublisher() {}

    /**
     * Publishes a structured JobAction to the execution pipeline.
     */
    public void publish(ServerPlayer player, JobAction action) {
        if (player == null || action == null) return;
        publishedCount.incrementAndGet();
        try {
            JobActionProcessor.getInstance().process(player, action);
        } catch (Throwable t) {
            LOGGER.error("Error processing job action {} for player {}", action.actionId(), player.getUUID(), t);
        }
    }

    /**
     * Legacy bridge method converting raw event parameters into a normalized JobAction.
     */
    public void publish(ServerPlayer player, String actionTypeString, Object target, String registryId) {
        if (player == null || actionTypeString == null) return;

        JobActionType type = JobActionType.fromString(actionTypeString);
        if (type == null) {
            if (JobsManager.isGlobalDebugMode()) {
                LOGGER.warn("Unknown job action type string '{}' published for player {}", actionTypeString, player.getName().getString());
            }
            return;
        }

        String targetId = resolveTargetId(target, registryId);
        if (targetId == null || targetId.isEmpty()) {
            return;
        }

        JobActionContext context = JobActionContext.builder()
                .dimension(player.level() != null && player.level().dimension() != null ? player.level().dimension().location().toString() : "")
                .position(player.blockPosition() != null ? player.blockPosition().toShortString() : "")
                .eventSource("LEGACY_BRIDGE")
                .build();

        JobAction action = JobAction.create(player.getUUID(), type, "LEGACY_BRIDGE", targetId, context);
        publish(player, action);
    }

    private String resolveTargetId(Object target, String registryId) {
        if (registryId != null && !registryId.trim().isEmpty()) {
            return registryId.trim();
        }
        if (target instanceof BlockState bs) {
            return BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
        }
        if (target instanceof EntityType<?> et) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(et).toString();
        }
        if (target instanceof ItemStack is && !is.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(is.getItem()).toString();
        }
        if (target != null) {
            return String.valueOf(target);
        }
        return "";
    }

    public long getPublishedCount() {
        return publishedCount.get();
    }
}
