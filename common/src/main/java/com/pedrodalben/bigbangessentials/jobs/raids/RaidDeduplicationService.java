package com.pedrodalben.bigbangessentials.jobs.raids;

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

public class RaidDeduplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaidDeduplicationService.class);
    private static final RaidDeduplicationService INSTANCE = new RaidDeduplicationService();

    private final Map<String, Long> processedPlayerRaids = new ConcurrentHashMap<>();

    public static RaidDeduplicationService getInstance() {
        return INSTANCE;
    }

    private RaidDeduplicationService() {}

    public void processRaidCleared(ServerPlayer player, String raidId, String tier, String bossSpecies, double damageDealt, int turnsPlayed, boolean noContribution, String eventSource) {
        if (player == null || raidId == null || raidId.trim().isEmpty()) return;

        UUID playerId = player.getUUID();
        String dedupeKey = playerId.toString() + "_" + raidId;

        if (processedPlayerRaids.putIfAbsent(dedupeKey, System.currentTimeMillis()) != null) {
            LOGGER.debug("Player {} already rewarded for raid {}. Ignoring duplicate.", playerId, raidId);
            return;
        }

        UUID actionId = UUID.nameUUIDFromBytes(("raid_" + dedupeKey).getBytes());
        JobActionContext ctx = JobActionContext.builder()
                .raidTier(tier != null ? tier : "1")
                .pokemonSpecies(bossSpecies != null ? bossSpecies : "boss")
                .eventSource(eventSource != null ? eventSource : "raiddens")
                .customAttribute("raid_id", raidId)
                .customAttribute("damage_dealt", String.valueOf(damageDealt))
                .customAttribute("turns_played", String.valueOf(turnsPlayed))
                .customAttribute("no_contribution", String.valueOf(noContribution))
                .build();

        JobAction action = JobAction.createWithId(actionId, playerId, JobActionType.RAID_CLEARED, eventSource != null ? eventSource : "raiddens", raidId, ctx);
        JobActionProcessor.getInstance().process(player, action);
    }
}
