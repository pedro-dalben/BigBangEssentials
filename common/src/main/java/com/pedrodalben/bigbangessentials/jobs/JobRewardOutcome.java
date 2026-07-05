package com.pedrodalben.bigbangessentials.jobs;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the calculated outcome of a job action reward evaluation.
 * Extensible to support side-effects (e.g., crate keys, fragments, audit events) without altering the core pipeline.
 */
public record JobRewardOutcome(
    double experience,
    double coins,
    List<JobRewardSideEffect> sideEffects,
    boolean success,
    String failureReason
) {
    public JobRewardOutcome {
        if (sideEffects == null) {
            sideEffects = Collections.emptyList();
        } else {
            sideEffects = Collections.unmodifiableList(sideEffects);
        }
        if (failureReason == null) {
            failureReason = "";
        }
    }

    public static JobRewardOutcome success(double experience, double coins, List<JobRewardSideEffect> sideEffects) {
        return new JobRewardOutcome(experience, coins, sideEffects, true, "");
    }

    public static JobRewardOutcome success(double experience, double coins) {
        return new JobRewardOutcome(experience, coins, Collections.emptyList(), true, "");
    }

    public static JobRewardOutcome failure(String reason) {
        return new JobRewardOutcome(0.0, 0.0, Collections.emptyList(), false, reason);
    }

    public long experienceAsLong() {
        return (long) experience;
    }

    public long coinsAsLong() {
        return (long) coins;
    }
}
