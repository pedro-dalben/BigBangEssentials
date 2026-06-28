package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.api.BigBangEssentialsApi;
import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemBalanceView;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservationStatus;
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
class GemExternalIntegrationContractTest {

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
    void testExternalIntegrationResizeAndPaymentFlow() {
        GemsService gems = BigBangEssentialsApi.requireGems();
        UUID playerUuid = UUID.randomUUID();

        gems.credit(new GemCreditRequest(
            playerUuid, 100L, "test-harness", "SETUP", null,
            UUID.randomUUID().toString(), null, Map.of()
        ));

        long cost = 50L;
        assertTrue(gems.hasAvailable(playerUuid, cost));

        UUID operationId = UUID.randomUUID();
        String idempotencyKey = "bigbangregions:resize:my-region-123:" + operationId;

        GemReservationRequest reserveReq = new GemReservationRequest(
            playerUuid, cost, "bigbangregions", "PLAYER_REGION_RESIZE",
            idempotencyKey, operationId.toString(), Duration.ofSeconds(900),
            Map.of("regionId", "my-region-123")
        );

        GemReservationResult reserveRes = gems.reserve(reserveReq);
        assertTrue(reserveRes.success());
        UUID reservationId = reserveRes.reservationId();
        assertNotNull(reservationId);

        GemBalanceView balAfterReserve = gems.getBalance(playerUuid);
        assertEquals(100L, balAfterReserve.totalBalance());
        assertEquals(50L, balAfterReserve.heldBalance());
        assertEquals(50L, balAfterReserve.availableBalance());

        GemReservationResult reserveRetry = gems.reserve(reserveReq);
        assertTrue(reserveRetry.success());
        assertEquals(reservationId, reserveRetry.reservationId());

        boolean resizeSuccess = true;

        GemCaptureRequest captureReq = new GemCaptureRequest(
            reservationId, "bigbangregions", "PLAYER_REGION_RESIZE", null,
            "bigbangregions:capture:" + operationId, operationId.toString(), Map.of()
        );

        GemOperationResult captureRes = gems.capture(captureReq);
        assertTrue(captureRes.success());

        GemBalanceView balAfterCapture = gems.getBalance(playerUuid);
        assertEquals(50L, balAfterCapture.totalBalance());
        assertEquals(0L, balAfterCapture.heldBalance());
        assertEquals(50L, balAfterCapture.availableBalance());

        GemReservation reservation = gems.findReservation(reservationId).orElseThrow();
        assertEquals(GemReservationStatus.CAPTURED, reservation.getStatus());

        GemOperationResult captureRetry = gems.capture(captureReq);
        assertTrue(captureRetry.success());
        assertEquals(50L, gems.getBalance(playerUuid).totalBalance());
    }
}
