package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.PlayerMilestoneRecord;
import java.util.Optional;
import java.util.UUID;

public interface PlayerMilestoneRepository {
    Optional<PlayerMilestoneRecord> find(UUID playerUuid, String crateId, String milestoneId, int thresholdMult);
    boolean recordDelivery(UUID playerUuid, String crateId, String milestoneId, int thresholdMult, long reachedAt, long deliveredAt, String openingId, boolean repeatable);
}
