package com.pedrodalben.bigbangessentials.jobs.league;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrainerCooldownService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainerCooldownService.class);
    private static final TrainerCooldownService INSTANCE = new TrainerCooldownService();

    private final Map<UUID, Map<String, Long>> playerTrainerCooldowns = new ConcurrentHashMap<>();

    // 24 hours for Gym Leaders/E4/Champion, 1 hour for common trainers
    private static final long GYM_COOLDOWN_MS = 24 * 60 * 60 * 1000L;
    private static final long COMMON_COOLDOWN_MS = 60 * 60 * 1000L;

    public static TrainerCooldownService getInstance() {
        return INSTANCE;
    }

    private TrainerCooldownService() {}

    public boolean isOnCooldown(UUID playerId, String trainerId, String tier) {
        if (playerId == null || trainerId == null) return false;
        long now = System.currentTimeMillis();
        Map<String, Long> trainerTimes = playerTrainerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        long lastTime = trainerTimes.getOrDefault(trainerId.toLowerCase(), 0L);
        long cooldown = "TRAINER_COMMON".equals(tier) ? COMMON_COOLDOWN_MS : GYM_COOLDOWN_MS;

        return (now - lastTime) < cooldown;
    }

    public void recordBattleVictory(UUID playerId, String trainerId) {
        if (playerId == null || trainerId == null) return;
        playerTrainerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap()).put(trainerId.toLowerCase(), System.currentTimeMillis());
        LOGGER.debug("Recorded trainer victory for player {} against {}", playerId, trainerId);
    }
}
