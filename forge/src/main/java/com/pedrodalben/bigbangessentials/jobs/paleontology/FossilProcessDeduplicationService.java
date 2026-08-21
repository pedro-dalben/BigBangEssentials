package com.pedrodalben.bigbangessentials.jobs.paleontology;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionContext;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FossilProcessDeduplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FossilProcessDeduplicationService.class);
    private static final FossilProcessDeduplicationService INSTANCE = new FossilProcessDeduplicationService();

    private final Map<String, Long> processedFossils = new ConcurrentHashMap<>();

    public static FossilProcessDeduplicationService getInstance() {
        return INSTANCE;
    }

    private FossilProcessDeduplicationService() {}

    public void processFossilRevived(ServerPlayer player, String fossilProcessId, String pokemonSpecies, String fossilItem, String machinePos) {
        if (player == null || fossilProcessId == null || fossilProcessId.trim().isEmpty()) return;

        if (processedFossils.putIfAbsent(fossilProcessId, System.currentTimeMillis()) != null) {
            LOGGER.debug("Fossil process {} already recorded. Ignoring duplicate.", fossilProcessId);
            return;
        }

        UUID actionId = UUID.nameUUIDFromBytes(("fossil_" + fossilProcessId).getBytes());
        JobActionContext ctx = JobActionContext.builder()
                .pokemonSpecies(pokemonSpecies != null ? pokemonSpecies : "")
                .position(machinePos != null ? machinePos : "")
                .eventSource("cobblemon_fossils")
                .customAttribute("fossil_process_id", fossilProcessId)
                .customAttribute("fossil_item", fossilItem != null ? fossilItem : "")
                .build();

        JobAction action = JobAction.createWithId(actionId, player.getUUID(), JobActionType.FOSSIL_REVIVED, "cobblemon_fossils", pokemonSpecies != null ? pokemonSpecies.toLowerCase() : "fossil", ctx);
        JobActionProcessor.getInstance().process(player, action);
    }
}
