package com.pedrodalben.bigbangessentials.jobs.pasture;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionContext;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class PastureCollectionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PastureCollectionService.class);
    private static final PastureCollectionService INSTANCE = new PastureCollectionService();

    public static PastureCollectionService getInstance() {
        return INSTANCE;
    }

    private PastureCollectionService() {}

    public void processManualCollection(ServerPlayer player, String pasturePos, String itemId, int amount, String eventSource) {
        if (player == null || itemId == null || amount <= 0) return;

        // Strict check: only manual or contract_delivery allowed
        if (!"manual".equalsIgnoreCase(eventSource) && !"contract_delivery".equalsIgnoreCase(eventSource)) {
            LOGGER.debug("Blocked non-manual pasture collection from source {}", eventSource);
            return;
        }

        UUID playerId = player.getUUID();
        int diversity = PastureDiversityService.getInstance().getDiversityScore(playerId);

        UUID actionId = UUID.nameUUIDFromBytes(("pasture_" + playerId + "_" + pasturePos + "_" + System.currentTimeMillis() / 5000L).getBytes());
        JobActionContext ctx = JobActionContext.builder()
                .position(pasturePos != null ? pasturePos : "")
                .eventSource(eventSource)
                .customAttribute("item_id", itemId)
                .customAttribute("amount", String.valueOf(amount))
                .customAttribute("diversity_score", String.valueOf(diversity))
                .build();

        JobAction action = JobAction.createWithId(actionId, playerId, JobActionType.PASTURE_TASK_COMPLETED, "cobblemon_pasture", itemId, ctx);
        JobActionProcessor.getInstance().process(player, action);
    }
}
