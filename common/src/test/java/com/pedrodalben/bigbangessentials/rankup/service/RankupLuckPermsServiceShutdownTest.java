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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies RankUp mutation against LuckPerms is rejected quickly when the server is shutting down
 * and doesn't queue a saveUser into the dying LuckPerms worker pool.
 *
 * <p>End-to-end success-path (normal promotion → node mutation → saveUser) is intentionally NOT
 * exercised here because {@code InheritanceNode.builder(Group)} resolves through
 * {@code LuckPermsProvider.get()}, which requires the LuckPerms runtime implementation bundled
 * in the actual mod jar (not present in the unit-test classpath). Verifying that behaviour must
 * live in a Fabric runtime integration test (seeLuckPermsAdapterFabricWiringTest) or a manual
 * server /stop smoke test.
 */
class RankupLuckPermsServiceShutdownTest {

    private RankupLuckPermsService service;
    private LuckPermsAdapter adapter;
    private LuckPerms api;
    private RankupConfig config;
    private RankupRank fromRank;
    private RankupRank toRank;
    private UUID playerUuid;
    private boolean saveUserInvoked;

    @BeforeEach
    void setUp() throws Exception {
        BigBangEssentials.setServerStoppingForTest(false);
        saveUserInvoked = false;

        api = mock(LuckPerms.class);
        var um = mock(net.luckperms.api.model.user.UserManager.class);
        when(api.getUserManager()).thenReturn(um);
        var user = mock(User.class);
        when(um.getUser(any(UUID.class))).thenReturn(user);
        // saveUser tracked
        when(um.saveUser(any(User.class))).thenAnswer(inv -> {
            saveUserInvoked = true;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        });

        adapter = spy(new LuckPermsAdapter(true));
        adapter.setLuckPermsApiForTest(api);
        PermissionAPI.setExternalAdapter(adapter);

        service = new RankupLuckPermsService();

        var lpTo = new RankupLuckPermsSettings("champion", true, RankupPromotionMode.SET_PRIMARY_GROUP);
        var lpFrom = new RankupLuckPermsSettings("rookie", true, RankupPromotionMode.SET_PRIMARY_GROUP);
        fromRank = new RankupRank("rookie", 1, "Rookie", List.of(), null, lpFrom, null, null, true);
        toRank   = new RankupRank("champion", 2, "Champion", List.of(), null, lpTo, null, null, true);
        var ranks = new LinkedHashMap<String, RankupRank>();
        ranks.put(fromRank.id(), fromRank);
        ranks.put(toRank.id(), toRank);
        config = mock(RankupConfig.class);
        when(config.getRanks()).thenReturn(ranks);
        var ladder = new RankupLadder("main", "Ladder", "rookie",
            RankupPromotionMode.SET_PRIMARY_GROUP, true);
        when(config.getLadder()).thenReturn(ladder);
        when(config.getInitialRank()).thenReturn(fromRank);

        playerUuid = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws Exception {
        BigBangEssentials.setServerStoppingForTest(false);
        PermissionAPI.setExternalAdapter(null);
    }

    @Test
    void applyRankChangeRejectedFastWhenServerStopping() throws Exception {
        BigBangEssentials.setServerStoppingForTest(true);
        var result = service.applyRankChange(playerUuid, fromRank, toRank, config)
            .orTimeout(5, TimeUnit.SECONDS).get();
        assertFalse(result.success());
        assertFalse(saveUserInvoked, "saveUser was invoked during shutdown");
    }

    @Test
    void applyRankChangeRejectedFastAfterAdapterShutdown() throws Exception {
        adapter.shutdown();
        var result = service.applyRankChange(playerUuid, fromRank, toRank, config)
            .orTimeout(5, TimeUnit.SECONDS).get();
        assertFalse(result.success());
        assertFalse(saveUserInvoked);
    }

    @Test
    void revertRankChangeRejectedFastWhenServerStopping() throws Exception {
        BigBangEssentials.setServerStoppingForTest(true);
        var result = service.revertRankChange(playerUuid, toRank, fromRank, config)
            .orTimeout(5, TimeUnit.SECONDS).get();
        assertFalse(result.success());
        assertFalse(saveUserInvoked);
    }

    @Test
    void revertRankChangeRejectedFastAfterAdapterShutdown() throws Exception {
        adapter.shutdown();
        var result = service.revertRankChange(playerUuid, toRank, fromRank, config)
            .orTimeout(5, TimeUnit.SECONDS).get();
        assertFalse(result.success());
        assertFalse(saveUserInvoked);
    }
}