package com.pedrodalben.bigbangessentials.jobs.progression;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface connecting RankUp progression with Jobs milestones and unlocks.
 */
public interface JobRankProgressionProvider {
    /**
     * Retrieves a snapshot of the player's current progression standing and unlocked milestones.
     */
    CompletableFuture<RankProgressionSnapshot> getProgression(UUID playerId);

    /**
     * Synchronizes and persists unlocked milestones based on the player's current rank.
     * Guaranteed never to revoke already unlocked milestones even if rank decreased.
     */
    CompletableFuture<Set<String>> synchronizeMilestones(UUID playerId);

    /**
     * Checks if a player has reached a specific milestone (using cached/synchronized data).
     */
    boolean hasReachedMilestone(UUID playerId, String milestoneId);

    /**
     * Retrieves the configuration definition for a given milestone ID.
     */
    Optional<RankMilestoneDefinition> getMilestoneDefinition(String milestoneId);
}
