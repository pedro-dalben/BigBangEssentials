package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.rankup.service.RankupPromotionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RankupRecoveryServiceTest {

    @Test
    void testActiveCompensationsLock() {
        RankupManager mockManager = Mockito.mock(RankupManager.class);
        RankupPromotionService service = new RankupPromotionService(mockManager);
        UUID uuid = UUID.randomUUID();
        RankupTransaction tx = new RankupTransaction("tx1", uuid, "ladder", "a", "b", BigDecimal.ZERO, 0, RankupTransactionStatus.PREPARED, "key", null, System.currentTimeMillis(), null);
        
        var fut = service.compensate(uuid, tx);
        assertNotNull(fut);
    }
}
