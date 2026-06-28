package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistence;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class GemReservationRecoveryTest {

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
    void testRecoveryRecalculatesHeldBalancesAndExpiresVenced() {
        UUID playerId = UUID.randomUUID();
        GemsState state = new GemsState();
        state.schemaVersion = 1;
        state.balances.put(playerId.toString(), 100L);

        UUID resId1 = UUID.randomUUID();
        long now = System.currentTimeMillis();
        GemReservation res1 = new GemReservation(
            resId1, playerId, 30L, GemReservationStatus.ACTIVE,
            "test", "test", null, null, Map.of(), now, now + 1000000L, null, null
        );
        state.reservations.put(resId1.toString(), res1);

        UUID resId2 = UUID.randomUUID();
        GemReservation res2 = new GemReservation(
            resId2, playerId, 20L, GemReservationStatus.ACTIVE,
            "test", "test", null, null, Map.of(), now - 10000L, now - 1000L, null, null
        );
        state.reservations.put(resId2.toString(), res2);

        GemsPersistence persistence = new GemsPersistence();
        persistence.saveState(state);

        GemsManager.getInstance().reload();

        GemReservation recoveredRes1 = GemsManager.getInstance().findReservation(resId1).orElseThrow();
        assertEquals(GemReservationStatus.ACTIVE, recoveredRes1.getStatus());

        GemReservation recoveredRes2 = GemsManager.getInstance().findReservation(resId2).orElseThrow();
        assertEquals(GemReservationStatus.EXPIRED, recoveredRes2.getStatus());

        GemBalanceView view = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, view.totalBalance());
        assertEquals(30L, view.heldBalance());
        assertEquals(70L, view.availableBalance());
    }
}
