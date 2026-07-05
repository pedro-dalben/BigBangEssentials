package com.pedrodalben.bigbangessentials.jobs.progression;

import java.util.Collections;
import java.util.List;

/**
 * Definition of a rank milestone configured in YAML/JSON that unlocks job slots and job licenses.
 */
public record RankMilestoneDefinition(
        String id,
        String displayName,
        String requiredRankId,
        int requiredRankOrder,
        List<String> unlockedSlots,
        List<String> eligibleJobs
) {
    public RankMilestoneDefinition {
        unlockedSlots = unlockedSlots != null ? Collections.unmodifiableList(unlockedSlots) : Collections.emptyList();
        eligibleJobs = eligibleJobs != null ? Collections.unmodifiableList(eligibleJobs) : Collections.emptyList();
    }
}
