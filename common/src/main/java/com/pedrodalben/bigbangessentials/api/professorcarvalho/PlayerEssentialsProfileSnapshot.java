package com.pedrodalben.bigbangessentials.api.professorcarvalho;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Read-only profile for the Professor Carvalho gateway. */
public record PlayerEssentialsProfileSnapshot(
        UUID playerUuid,
        Optional<String> rankId,
        Optional<String> rankDisplayName,
        OptionalLong playtimeSeconds,
        Optional<BigDecimal> coinBalance,
        OptionalLong gemBalance,
        List<JobProgressSnapshot> jobs,
        Instant capturedAt) {
    public PlayerEssentialsProfileSnapshot {
        rankId = rankId == null ? Optional.empty() : rankId;
        rankDisplayName = rankDisplayName == null ? Optional.empty() : rankDisplayName;
        playtimeSeconds = playtimeSeconds == null ? OptionalLong.empty() : playtimeSeconds;
        coinBalance = coinBalance == null ? Optional.empty() : coinBalance;
        gemBalance = gemBalance == null ? OptionalLong.empty() : gemBalance;
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }
}
