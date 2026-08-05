package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.permissions.LuckPermsAdapter;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fabric-side parity for {@code common: RankupLuckPermsServiceShutdownTest}. Asserts the rejection
 * branches compile against the runtime fabric classpath and produce a fast failure (no `saveUser`
 * dispatched into the LuckPerms worker pool while the server is tearing down).
 */
class RankupLuckPermsServiceFabricShutdownTest {

    private RankupLuckPermsService service;
    private LuckPermsAdapter adapter;
    private boolean saveUserInvoked;

    @BeforeEach
    void setUp() {
        BigBangEssentials.setServerStoppingForTest(false);
        saveUserInvoked = false;

        LuckPerms api = mock(LuckPerms.class);
        var um = mock(net.luckperms.api.model.user.UserManager.class);
        when(api.getUserManager()).thenReturn(um);
        when(um.getUser(any(UUID.class))).thenReturn(mock(User.class));
        when(um.saveUser(any(User.class))).thenAnswer(inv -> {
            saveUserInvoked = true;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        });

        adapter = spy(new LuckPermsAdapter(true));
        adapter.setLuckPermsApiForTest(api);
        PermissionAPI.setExternalAdapter(adapter);

        service = new RankupLuckPermsService();
    }

    @AfterEach
    void tearDown() {
        BigBangEssentials.setServerStoppingForTest(false);
        PermissionAPI.setExternalAdapter(null);
    }

    @Test
    void applyRankChangeRejectedWhenServerStopping() throws Exception {
        BigBangEssentials.setServerStoppingForTest(true);
        var result = service.applyRankChange(UUID.randomUUID(), rank("rookie"), rank("champion"), config())
            .orTimeout(5, TimeUnit.SECONDS).get();
        assertFalse(result.success());
        assertFalse(saveUserInvoked);
    }

    @Test
    void revertRankChangeRejectedAfterAdapterShutdown() throws Exception {
        adapter.shutdown();
        var result = service.revertRankChange(UUID.randomUUID(), rank("champion"), rank("rookie"), config())
            .orTimeout(5, TimeUnit.SECONDS).get();
        assertFalse(result.success());
        assertFalse(saveUserInvoked);
    }

    private RankupRank rank(String id) {
        var lp = new RankupLuckPermsSettings(id, true, RankupPromotionMode.SET_PRIMARY_GROUP);
        return new RankupRank(id, id.equals("rookie") ? 1 : 2, id, List.of(), null, lp, null, null, true);
    }

    private RankupConfig config() {
        var ranks = new LinkedHashMap<String, RankupRank>();
        ranks.put("rookie", rank("rookie"));
        ranks.put("champion", rank("champion"));
        var cfg = mock(RankupConfig.class);
        when(cfg.getRanks()).thenReturn(ranks);
        var ladder = new RankupLadder("main", "L", "rookie", RankupPromotionMode.SET_PRIMARY_GROUP, true);
        when(cfg.getLadder()).thenReturn(ladder);
        when(cfg.getInitialRank()).thenReturn(rank("rookie"));
        return cfg;
    }
}