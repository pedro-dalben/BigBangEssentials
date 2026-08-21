package com.pedrodalben.bigbangessentials.jobs.breeding;

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

public class EggLifecycleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EggLifecycleService.class);
    private static final EggLifecycleService INSTANCE = new EggLifecycleService();

    private final Map<UUID, Long> processedEggs = new ConcurrentHashMap<>();

    public static EggLifecycleService getInstance() {
        return INSTANCE;
    }

    private EggLifecycleService() {}

    public void processEggCreated(ServerPlayer player, UUID eggUuid, String species, String parentA, String parentB, boolean isTraded, boolean isAdminSpawned) {
        if (player == null || eggUuid == null) return;
        if (isTraded || isAdminSpawned) return;

        UUID actionId = UUID.nameUUIDFromBytes(("egg_create_" + eggUuid).getBytes());
        JobActionContext ctx = JobActionContext.builder()
                .pokemonSpecies(species != null ? species : "unknown")
                .eventSource("cobbreeding")
                .customAttribute("egg_uuid", eggUuid.toString())
                .customAttribute("parent_a", parentA != null ? parentA : "")
                .customAttribute("parent_b", parentB != null ? parentB : "")
                .build();

        JobAction action = JobAction.createWithId(actionId, player.getUUID(), JobActionType.EGG_CREATED, "cobbreeding", species != null ? species.toLowerCase() : "egg", ctx);
        JobActionProcessor.getInstance().process(player, action);
    }

    public void processEggHatched(ServerPlayer player, UUID eggUuid, String species, boolean isShiny, boolean isLegendary, boolean isTraded, boolean isAdminSpawned) {
        if (player == null || eggUuid == null) return;

        if (processedEggs.putIfAbsent(eggUuid, System.currentTimeMillis()) != null) {
            LOGGER.debug("Egg {} already hatched or processed.", eggUuid);
            return;
        }

        UUID actionId = UUID.nameUUIDFromBytes(("egg_hatch_" + eggUuid).getBytes());
        JobActionContext ctx = JobActionContext.builder()
                .pokemonSpecies(species != null ? species : "unknown")
                .eventSource("cobbreeding")
                .customAttribute("egg_uuid", eggUuid.toString())
                .customAttribute("shiny", String.valueOf(isShiny))
                .customAttribute("legendary", String.valueOf(isLegendary))
                .customAttribute("is_traded", String.valueOf(isTraded))
                .customAttribute("admin_spawned", String.valueOf(isAdminSpawned))
                .build();

        JobAction action = JobAction.createWithId(actionId, player.getUUID(), JobActionType.EGG_HATCHED, "cobbreeding", species != null ? species.toLowerCase() : "egg", ctx);
        JobActionProcessor.getInstance().process(player, action);
    }
}
