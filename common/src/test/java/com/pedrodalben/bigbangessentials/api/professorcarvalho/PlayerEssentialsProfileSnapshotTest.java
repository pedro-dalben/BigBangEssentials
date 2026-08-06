package com.pedrodalben.bigbangessentials.api.professorcarvalho;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerEssentialsProfileSnapshotTest {
    @Test
    void normalizesOptionalFieldsWithoutLeakingMutableJobs() {
        PlayerEssentialsProfileSnapshot snapshot = new PlayerEssentialsProfileSnapshot(
                UUID.randomUUID(), null, null, null, null, null, null, Instant.EPOCH);

        assertTrue(snapshot.jobs().isEmpty());
        assertFalse(snapshot.coinBalance().isPresent());
        assertFalse(snapshot.gemBalance().isPresent());
        assertFalse(snapshot.playtimeSeconds().isPresent());
    }
}
