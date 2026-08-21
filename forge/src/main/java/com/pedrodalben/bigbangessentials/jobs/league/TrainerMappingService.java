package com.pedrodalben.bigbangessentials.jobs.league;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TrainerMappingService {
    private static final TrainerMappingService INSTANCE = new TrainerMappingService();

    private final Map<String, String> customMappings = new ConcurrentHashMap<>();

    public static TrainerMappingService getInstance() {
        return INSTANCE;
    }

    private TrainerMappingService() {
        customMappings.put("gym_leader", "GYM_LEADER");
        customMappings.put("elite_four", "ELITE_FOUR");
        customMappings.put("champion", "CHAMPION");
    }

    public void registerMapping(String trainerIdOrTag, String tier) {
        if (trainerIdOrTag != null && tier != null) {
            customMappings.put(trainerIdOrTag.toLowerCase(), tier.toUpperCase());
        }
    }

    public String mapTrainerTier(String trainerId, String trainerName) {
        if (trainerId != null) {
            String mapped = customMappings.get(trainerId.toLowerCase());
            if (mapped != null) return mapped;
        }
        if (trainerName != null) {
            String lower = trainerName.toLowerCase();
            if (lower.contains("champion") || lower.contains("campeão")) return "CHAMPION";
            if (lower.contains("elite") || lower.contains("e4")) return "ELITE_FOUR";
            if (lower.contains("leader") || lower.contains("líder") || lower.contains("gym")) return "GYM_LEADER";
        }
        return "TRAINER_COMMON";
    }
}
