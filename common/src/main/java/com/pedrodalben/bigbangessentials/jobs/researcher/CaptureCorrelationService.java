package com.pedrodalben.bigbangessentials.jobs.researcher;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionContext;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class CaptureCorrelationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureCorrelationService.class);
    private static final CaptureCorrelationService INSTANCE = new CaptureCorrelationService();

    public static CaptureCorrelationService getInstance() {
        return INSTANCE;
    }

    private CaptureCorrelationService() {}

    public void processCapture(ServerPlayer player, UUID pokemonUuid, String species, String form, boolean isShiny, boolean isLegendary, String ballUsed, String biome, boolean isTraded, boolean isAdminSpawned, String eventSource) {
        if (player == null || pokemonUuid == null || species == null) return;

        UUID playerId = player.getUUID();
        String sessionStr = playerId.toString() + "_" + pokemonUuid.toString();
        UUID capActionId = UUID.nameUUIDFromBytes(("cap_" + sessionStr).getBytes());

        boolean isFirstDiscovery = !isTraded && !isAdminSpawned && DexDiscoveryService.getInstance().recordDiscoveryIfNew(playerId, species);

        if (isFirstDiscovery) {
            UUID dexActionId = UUID.nameUUIDFromBytes(("dex_" + playerId + "_" + species.toLowerCase()).getBytes());
            JobActionContext dexContext = JobActionContext.builder()
                    .pokemonSpecies(species)
                    .pokemonForm(form != null ? form : "")
                    .firstDiscovery(true)
                    .eventSource(eventSource != null ? eventSource : "cobblemon")
                    .biome(biome != null ? biome : "")
                    .customAttribute("shiny", String.valueOf(isShiny))
                    .customAttribute("legendary", String.valueOf(isLegendary))
                    .customAttribute("ball_used", ballUsed != null ? ballUsed : "")
                    .customAttribute("correlated_capture_id", capActionId.toString())
                    .build();

            JobAction dexAction = JobAction.createWithId(dexActionId, playerId, JobActionType.DEX_ENTRY_ADDED, eventSource != null ? eventSource : "cobblemon", species.toLowerCase(), dexContext);
            JobActionProcessor.getInstance().process(player, dexAction);
        }

        JobActionContext capContext = JobActionContext.builder()
                .pokemonSpecies(species)
                .pokemonForm(form != null ? form : "")
                .firstDiscovery(isFirstDiscovery)
                .eventSource(eventSource != null ? eventSource : "cobblemon")
                .biome(biome != null ? biome : "")
                .customAttribute("shiny", String.valueOf(isShiny))
                .customAttribute("legendary", String.valueOf(isLegendary))
                .customAttribute("ball_used", ballUsed != null ? ballUsed : "")
                .customAttribute("is_traded", String.valueOf(isTraded))
                .customAttribute("admin_spawned", String.valueOf(isAdminSpawned))
                .customAttribute("correlated_dex_entry", String.valueOf(isFirstDiscovery))
                .build();

        JobAction capAction = JobAction.createWithId(capActionId, playerId, JobActionType.POKEMON_CAPTURED, eventSource != null ? eventSource : "cobblemon", species.toLowerCase(), capContext);
        JobActionProcessor.getInstance().process(player, capAction);
    }
}
