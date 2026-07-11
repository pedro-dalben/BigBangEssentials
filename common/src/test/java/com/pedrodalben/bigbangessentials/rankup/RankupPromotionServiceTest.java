package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.rankup.service.RankupPromotionService;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RankupPromotionServiceTest {

    @Test
    void testPromotionQueueBehavior() {
        RankupPromotionService service = new RankupPromotionService();
        UUID uuid = UUID.randomUUID();
        assertFalse(service.isPromotionInProgress(uuid));
        
        ServerPlayer player = Mockito.mock(ServerPlayer.class);
        Mockito.when(player.getUUID()).thenReturn(uuid);
        
        RankupRank targetRank = new RankupRank("test", 1, "Test", java.util.List.of(), null, null, null, null, false);
        
        // When promotion is run and configuration is missing/invalid, it should fail fast and complete the future
        var fut = service.promote(player, targetRank);
        assertNotNull(fut);
        assertTrue(fut.isDone());
        assertFalse(fut.join().success());
    }
}
