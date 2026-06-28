package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class GemReservationIdempotencyTest {

    @BeforeEach
    void setUp() {
        cleanData();
        GemsManager.getInstance().reload();
    }

    private void cleanData() {
        File dataDir = new File("bigbangessentials");
        if (dataDir.exists()) {
            new File(dataDir, "gems_state.json").delete();
            new File(dataDir, "gems_transactions.jsonl").delete();
            new File(dataDir, "gems.json").delete();
            File backupDir = new File(dataDir, "gems_backups");
            if (backupDir.exists()) {
                File[] files = backupDir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                backupDir.delete();
            }
        }
    }

    @Test
    void testCreditIdempotency() {
        UUID playerId = UUID.randomUUID();
        String key = "credit-key-123";

        GemCreditRequest req1 = new GemCreditRequest(
            playerId, 100L, "test-source", "TEST", null,
            key, null, Map.of()
        );
        GemOperationResult res1 = GemsManager.getInstance().credit(req1);
        assertTrue(res1.success());

        // Repeat with identical details -> returns original result/success
        GemCreditRequest req2 = new GemCreditRequest(
            playerId, 100L, "test-source", "TEST", null,
            key, null, Map.of()
        );
        GemOperationResult res2 = GemsManager.getInstance().credit(req2);
        assertTrue(res2.success());
        assertEquals(res1.transactionId(), res2.transactionId());
        assertEquals(100L, res2.balance().totalBalance()); // No double credit!

        // Repeat with different details -> conflict
        GemCreditRequest req3 = new GemCreditRequest(
            playerId, 200L, "test-source", "TEST", null,
            key, null, Map.of()
        );
        GemOperationResult res3 = GemsManager.getInstance().credit(req3);
        assertFalse(res3.success());
        assertEquals(GemOperationFailure.IDEMPOTENCY_CONFLICT, res3.failure());
    }

    @Test
    void testReservationIdempotency() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(
            playerId, 100L, "test-source", "TEST", null,
            UUID.randomUUID().toString(), null, Map.of()
        ));

        String key = "reserve-key-456";
        GemReservationRequest req1 = new GemReservationRequest(
            playerId, 30L, "test-source", "TEST", key,
            "ref-op", Duration.ofSeconds(60), Map.of()
        );
        GemReservationResult res1 = GemsManager.getInstance().reserve(req1);
        assertTrue(res1.success());

        // Retry with same key -> should return original success and same reservation ID
        GemReservationResult res2 = GemsManager.getInstance().reserve(req1);
        assertTrue(res2.success());
        assertEquals(res1.reservationId(), res2.reservationId());

        // Retry with different amount -> should fail with conflict
        GemReservationRequest req3 = new GemReservationRequest(
            playerId, 40L, "test-source", "TEST", key,
            "ref-op", Duration.ofSeconds(60), Map.of()
        );
        GemReservationResult res3 = GemsManager.getInstance().reserve(req3);
        assertFalse(res3.success());
        assertEquals(GemOperationFailure.IDEMPOTENCY_CONFLICT, res3.failure());
    }
}
