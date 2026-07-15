package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.integration.rankup.action.RankupRankClickAction;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RankupRankClickActionTest {

    @Test
    void testClickActionType() {
        RankupRankClickAction action = new RankupRankClickAction();
        assertEquals("rankup_rank_click", action.type());
    }

    @Test
    void testExecuteWithNullPlayer() {
        RankupRankClickAction action = new RankupRankClickAction();
        ActionContext context = new ActionContext(null, null, null, null, null, null, null, Map.of());
        var result = action.execute(context).toCompletableFuture().join();
        assertEquals(com.pedrodalben.bigbangessentials.menu.model.ActionStatus.FAILED, result.status());
    }
}
