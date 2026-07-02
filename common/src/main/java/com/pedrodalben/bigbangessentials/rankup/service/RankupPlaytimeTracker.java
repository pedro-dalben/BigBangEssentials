package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankupPlaytimeTracker {
    private static final int TICKS_PER_MINUTE = 1200;
    private final RankupAntiExploitService antiExploit = RankupAntiExploitService.getInstance();
    private final RankupTaskProgressService progressService = RankupTaskProgressService.getInstance();
    private final Map<UUID, Integer> onlineTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Biome> lastBiome = new ConcurrentHashMap<>();

    public void onTick(ServerPlayer player) {
        if (player == null || antiExploit.isFakePlayer(player)) return;

        UUID uuid = player.getUUID();
        int ticks = onlineTicks.merge(uuid, 1, Integer::sum);
        if (ticks >= TICKS_PER_MINUTE) {
            onlineTicks.put(uuid, 0);
            ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.PLAYTIME_MINUTES)
                    .dimension(player.level().dimension().location().toString())
                    .fakePlayer(false)
                    .build();
            progressService.processActivity(ctx);
        }

        Biome current = player.level().getBiome(player.blockPosition()).value();
        Biome previous = lastBiome.put(uuid, current);
        if (previous != current) {
            ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.VISIT_BIOME)
                    .target(current)
                    .dimension(player.level().dimension().location().toString())
                    .fakePlayer(false)
                    .build();
            progressService.processActivity(ctx);
        }
    }

    public void removePlayer(UUID uuid) {
        onlineTicks.remove(uuid);
        lastBiome.remove(uuid);
    }
}
