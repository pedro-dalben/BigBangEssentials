package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.rankup.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RankupEligibilitySnapshotTest {

    @Test
    void testEvaluateReady() {
        UUID uuid = UUID.randomUUID();
        RankupRank current = new RankupRank("a", 0, "A", List.of(), null, null, null, null, true);
        RankupRank next = new RankupRank("b", 1, "B", List.of(), null, null,
                new RankupRequirements(BigDecimal.valueOf(100.0), 5, RankupTaskMode.ALL, List.of()), null, true);

        RankupEligibilitySnapshot snapshot = RankupEligibilitySnapshot.evaluate(
                uuid, current, next, null, List.of(), RankupTaskMode.ALL, BigDecimal.valueOf(100.0), 5, false
        );

        assertTrue(snapshot.isReadyForPromotion());
        assertEquals(BigDecimal.valueOf(100.0), snapshot.moneyRequired());
        assertEquals(5, snapshot.gemsRequired());
        assertTrue(snapshot.moneySufficient());
        assertTrue(snapshot.gemsSufficient());
    }

    @Test
    void testEvaluateBlockedByMoney() {
        UUID uuid = UUID.randomUUID();
        RankupRank current = new RankupRank("a", 0, "A", List.of(), null, null, null, null, true);
        RankupRank next = new RankupRank("b", 1, "B", List.of(), null, null,
                new RankupRequirements(BigDecimal.valueOf(100.0), 0, RankupTaskMode.ALL, List.of()), null, true);

        RankupEligibilitySnapshot snapshot = RankupEligibilitySnapshot.evaluate(
                uuid, current, next, null, List.of(), RankupTaskMode.ALL, BigDecimal.valueOf(50.0), 0, false
        );

        assertFalse(snapshot.isReadyForPromotion());
        assertEquals(RankupEligibilityState.BLOCKED_BY_MONEY, snapshot.state());
        assertEquals(BigDecimal.valueOf(50.0), snapshot.moneyMissing());
    }

    @Test
    void testEvaluateBlockedByGems() {
        UUID uuid = UUID.randomUUID();
        RankupRank current = new RankupRank("a", 0, "A", List.of(), null, null, null, null, true);
        RankupRank next = new RankupRank("b", 1, "B", List.of(), null, null,
                new RankupRequirements(BigDecimal.ZERO, 10, RankupTaskMode.ALL, List.of()), null, true);

        RankupEligibilitySnapshot snapshot = RankupEligibilitySnapshot.evaluate(
                uuid, current, next, null, List.of(), RankupTaskMode.ALL, BigDecimal.ZERO, 5, false
        );

        assertFalse(snapshot.isReadyForPromotion());
        assertEquals(RankupEligibilityState.BLOCKED_BY_GEMS, snapshot.state());
        assertEquals(5, snapshot.gemsMissing());
    }
}
