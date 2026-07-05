package com.pedrodalben.bigbangessentials.jobs.action;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import java.time.Instant;
import java.util.Optional;

/**
 * Internal event emitted after a JobAction is processed by the execution pipeline.
 * Used by JobLicenseProgressService and other internal systems to track valid actions without polling or duplicated listeners.
 */
public record JobActionProcessedEvent(
        JobAction action,
        boolean accepted,
        Optional<String> rejectionReason,
        Instant processedAt
) {}
