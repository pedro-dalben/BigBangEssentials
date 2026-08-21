package com.pedrodalben.bigbangessentials.jobs.progression;

import java.util.Set;
import java.util.UUID;

/**
 * Snapshot of a player's current RankUp standing and unlocked Job milestones.
 */
public record RankProgressionSnapshot(
        UUID playerId,
        String currentRankId,
        String currentRankDisplayName,
        int currentRankOrder,
        Set<String> unlockedMilestoneIds
) {
    public boolean hasMilestone(String milestoneId) {
        return milestoneId != null && unlockedMilestoneIds != null && unlockedMilestoneIds.contains(milestoneId.toLowerCase());
    }
}
