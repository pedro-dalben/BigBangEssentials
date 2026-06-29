package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.*;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrateTransactionalTest {

    private static CrateOpeningService openingService;
    private static CrateKeyService keyService;
    private static RewardService rewardService;
    private static CrateService crateService;

    @BeforeAll
    static void setup() throws IOException {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {}

        Path configDir = Paths.get("config");
        if (!Files.exists(configDir)) Files.createDirectories(configDir);

        String dbConfig = "{"
            + "\"enabled\": true,"
            + "\"type\": \"SQLITE\","
            + "\"path\": \"config/test_crates.db\","
            + "\"required\": false"
            + "}";
        Files.writeString(configDir.resolve("database.json"), dbConfig);

        DatabaseManager.getInstance().initialize();

        openingService = CrateOpeningService.getInstance();
        keyService = CrateKeyService.getInstance();
        rewardService = RewardService.getInstance();
        crateService = CrateService.getInstance();
    }

    @AfterAll
    static void tearDown() {
        DatabaseManager.getInstance().shutdown();
    }

    @BeforeEach
    void clearData() {
        crateService.reload();
        keyService.reload();
        rewardService.reload();
    }

    private static ServerPlayer mockPlayer(UUID playerId) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);
        when(player.getName()).thenReturn(Component.literal("TestPlayer"));
        return player;
    }

    private static CrateDefinition crateRequiringKey(String crateKey, String rewardId) {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), crateKey, "Test Crate");
        crate.setEnabled(true);
        CrateReward reward = new CrateReward(rewardId, crateKey, "Reward", RewardType.COMMAND, "common");
        reward.setCommands(List.of("say test"));
        crate.setRewards(List.of(reward));
        crate.setRarities(List.of(new CrateRarity("common", "Common", "#FFFFFF", 1.0)));
        crate.getRequirements().addAcceptedKeyId("test_key");
        crate.getRequirements().setRequireVirtualKey(true);
        return crate;
    }

    @Test
    void testConcurrentOpenings_ShouldOnlySucceedOnce() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("concurrent_crate", "r1");

        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    var result = openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());
                    if (result.success()) successCount.incrementAndGet();
                    else failureCount.incrementAndGet();
                } catch (Exception ignored) {}
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "Only one opening should succeed with 1 key");
        assertEquals(threads - 1, failureCount.get(), "Other attempts should fail");
    }

    @Test
    void testAtomicDecrement_ShouldNotConsumeWhenInsufficient() {
        UUID playerId = UUID.randomUUID();

        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);
        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"));

        boolean taken = keyService.takeVirtualKey(playerId, "test_key", 2, GrantSource.OPENING);
        assertFalse(taken);
        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"));
    }

    @Test
    void testMassOpen_ConsumesCorrectAmount() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("mass_crate", "r2");

        keyService.giveVirtualKey(playerId, "test_key", 5, GrantSource.ADMIN_COMMAND, null);

        var results = openingService.massOpen(player, crate, 5, GrantSource.MASS_OPEN);

        assertEquals(5, results.size());
        assertTrue(results.stream().allMatch(CrateOpeningService.CrateOpeningResult::success));
        assertEquals(0, keyService.getVirtualKeyBalance(playerId, "test_key"));
    }

    @Test
    void testMassOpen_StopsWhenOutOfKeys() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("mass_crate2", "r3");

        keyService.giveVirtualKey(playerId, "test_key", 3, GrantSource.ADMIN_COMMAND, null);

        var results = openingService.massOpen(player, crate, 10, GrantSource.MASS_OPEN);

        assertEquals(4, results.size(), "Should have 3 successes + 1 failure (break includes the failure)");
        assertEquals(3, results.stream().filter(CrateOpeningService.CrateOpeningResult::success).count(), "3 should succeed");
        assertEquals(0, keyService.getVirtualKeyBalance(playerId, "test_key"));
    }

    @Test
    void testBasicOpenSucceeds() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("basic_crate", "r0");
        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);

        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"));

        var result = openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());
        assertTrue(result.success(), "Basic open should succeed: " + result.message()
            + " (audit=" + (result.audit() != null ? result.audit().getStatus() : "null") + ")");
        assertEquals(0, keyService.getVirtualKeyBalance(playerId, "test_key"), "Key should be consumed");
    }

    @Test
    void testIdempotencyKey_PreventsDuplicateOpening() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("idempotent_crate", "r4");
        keyService.giveVirtualKey(playerId, "test_key", 2, GrantSource.ADMIN_COMMAND, null);

        String idempotencyKey = "test-idempotent-" + UUID.randomUUID();

        var first = openingService.openCrate(player, crate, GrantSource.OPENING, idempotencyKey);
        assertTrue(first.success(), "First open should succeed, got: " + first.message());
        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"));

        var second = openingService.openCrate(player, crate, GrantSource.OPENING, idempotencyKey);
        assertFalse(second.success(), "Duplicate idempotency key should be rejected");
        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"), "Balance must not change on duplicate");
    }

    @Test
    void testRecordOpening_IncrementsTotalOpened() {
        UUID playerId = UUID.randomUUID();
        crateService.reload();

        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository();

        var state1 = repo.recordOpening(playerId, "atomic_crate");
        assertEquals(1, state1.getTotalOpened());

        var state2 = repo.recordOpening(playerId, "atomic_crate");
        assertEquals(2, state2.getTotalOpened());

        var state3 = repo.recordOpening(playerId, "atomic_crate");
        assertEquals(3, state3.getTotalOpened());
    }

    @Test
    void testStartCooldown_PersistsAcrossSessions() {
        UUID playerId = UUID.randomUUID();
        crateService.reload();

        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository();

        long cooldownUntil = System.currentTimeMillis() + 60000;
        repo.startCooldown(playerId, "cooldown_crate", cooldownUntil);

        var state = repo.findByPlayerAndCrate(playerId, "cooldown_crate").orElseThrow();
        assertTrue(state.isOnCooldown(), "Should be on cooldown");
        assertTrue(state.getCooldownUntil() >= cooldownUntil,
            "Cooldown timestamp should be >= set value");
    }

    @Test
    void testClearCooldown_ResetsCooldown() {
        UUID playerId = UUID.randomUUID();
        crateService.reload();

        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository();

        repo.startCooldown(playerId, "clear_crate", System.currentTimeMillis() + 60000);
        assertTrue(repo.findByPlayerAndCrate(playerId, "clear_crate")
            .orElseThrow().isOnCooldown());

        repo.clearCooldown(playerId, "clear_crate");
        assertFalse(repo.findByPlayerAndCrate(playerId, "clear_crate")
            .orElseThrow().isOnCooldown());
    }

    @Test
    void testConcurrentRecordOpening_ShouldIncrementAtomically() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        crateService.reload();

        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository();

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    repo.recordOpening(playerId, "concurrent_atomic_crate");
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(0, errors.get(), "No errors should occur during concurrent recordOpening");

        var finalState = repo.findByPlayerAndCrate(playerId, "concurrent_atomic_crate").orElseThrow();
        assertEquals(threads, finalState.getTotalOpened(),
            "total_opened should equal number of concurrent calls (%d)".formatted(threads));
    }

    @Test
    void testRecordOpeningWithExistingCooldown_PreservesCooldown() {
        UUID playerId = UUID.randomUUID();
        crateService.reload();

        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository();

        long cooldownUntil = System.currentTimeMillis() + 120000;
        repo.startCooldown(playerId, "preserve_crate", cooldownUntil);

        repo.recordOpening(playerId, "preserve_crate");

        var state = repo.findByPlayerAndCrate(playerId, "preserve_crate").orElseThrow();
        assertEquals(1, state.getTotalOpened(), "Opening should be recorded");
        assertTrue(state.getCooldownUntil() >= cooldownUntil, "Cooldown should be preserved after recordOpening");
    }

    @Test
    void testGlobalLimit_IncrementIsAtomic() {
        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcRewardRollStateRepository();
        String rewardId = "reward_limit_" + UUID.randomUUID().toString().substring(0, 8);

        int result1 = repo.incrementGlobalCount(rewardId);
        assertEquals(1, result1, "First increment should return 1");

        int result2 = repo.incrementGlobalCount(rewardId);
        assertEquals(2, result2, "Second increment should return 2");
    }

    @Test
    void testPlayerLimit_TracksPerPlayer() {
        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcRewardRollStateRepository();
        String rewardId = "reward_player_" + UUID.randomUUID().toString().substring(0, 8);
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        assertEquals(1, repo.incrementPlayerCount(rewardId, playerA));
        assertEquals(1, repo.incrementPlayerCount(rewardId, playerB));
        assertEquals(2, repo.incrementPlayerCount(rewardId, playerA));

        assertEquals(2, repo.getPlayerCount(rewardId, playerA));
        assertEquals(1, repo.getPlayerCount(rewardId, playerB));
    }

    @Test
    void testConcurrentGlobalLimit_IncrementsAtomically() throws InterruptedException {
        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcRewardRollStateRepository();
        String rewardId = "concurrent_global_limit_" + UUID.randomUUID().toString().substring(0, 8);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    repo.incrementGlobalCount(rewardId);
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(0, errors.get(), "No errors during concurrent global increments");

        var state = repo.findByRewardId(rewardId).orElseThrow();
        assertEquals(threads, state.getGlobalCount(),
            "global_count should equal number of concurrent calls");
    }

    @Test
    void testConcurrentPlayerLimit_IncrementsAtomically() throws InterruptedException {
        var repo = new com.pedrodalben.bigbangessentials.crates.persistence.JdbcRewardRollStateRepository();
        String rewardId = "concurrent_player_limit_" + UUID.randomUUID().toString().substring(0, 8);
        UUID playerId = UUID.randomUUID();

        int threads = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    repo.incrementPlayerCount(rewardId, playerId);
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(0, errors.get(), "No errors during concurrent player increments");
        assertEquals(threads, repo.getPlayerCount(rewardId, playerId),
            "player_count should equal number of concurrent calls");
    }

    @Test
    void testGiveVirtualKey_IdempotentDuplicate_DoesNotIncrement() {
        UUID playerId = UUID.randomUUID();
        String idempotencyKey = "give-idem-" + UUID.randomUUID();

        assertEquals(0, keyService.getVirtualKeyBalance(playerId, "test_key"));

        keyService.giveVirtualKey(playerId, "test_key", 5, GrantSource.ADMIN_COMMAND, idempotencyKey);
        assertEquals(5, keyService.getVirtualKeyBalance(playerId, "test_key"),
            "First call should grant 5 keys");

        keyService.giveVirtualKey(playerId, "test_key", 5, GrantSource.ADMIN_COMMAND, idempotencyKey);
        assertEquals(5, keyService.getVirtualKeyBalance(playerId, "test_key"),
            "Duplicate call with same idempotency key should NOT grant again");
    }

    @Test
    void testGiveVirtualKey_DifferentIdempotencyKeys_BothIncrement() {
        UUID playerId = UUID.randomUUID();

        keyService.giveVirtualKey(playerId, "test_key", 3, GrantSource.ADMIN_COMMAND, "key-1-" + UUID.randomUUID());
        assertEquals(3, keyService.getVirtualKeyBalance(playerId, "test_key"));

        keyService.giveVirtualKey(playerId, "test_key", 2, GrantSource.ADMIN_COMMAND, "key-2-" + UUID.randomUUID());
        assertEquals(5, keyService.getVirtualKeyBalance(playerId, "test_key"),
            "Different idempotency keys should both grant");
    }

    @Test
    void testTakeVirtualKey_IdempotentDuplicate_DoesNotDoubleConsume() {
        UUID playerId = UUID.randomUUID();
        String idempotencyKey = "take-idem-" + UUID.randomUUID();

        keyService.giveVirtualKey(playerId, "test_key", 3, GrantSource.ADMIN_COMMAND, "give-" + UUID.randomUUID());
        assertEquals(3, keyService.getVirtualKeyBalance(playerId, "test_key"));

        boolean taken1 = keyService.takeVirtualKey(playerId, "test_key", 2, GrantSource.OPENING, idempotencyKey);
        assertTrue(taken1, "First take should succeed");
        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"));

        boolean taken2 = keyService.takeVirtualKey(playerId, "test_key", 2, GrantSource.OPENING, idempotencyKey);
        assertTrue(taken2, "Duplicate idempotent take should report success (already processed)");
        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"),
            "Duplicate call with same idempotency key should NOT consume again");
    }

    @Test
    void testGiveVirtualKey_NullIdempotencyKey_AlwaysExecutes() {
        UUID playerId = UUID.randomUUID();

        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);
        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"));

        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);
        assertEquals(2, keyService.getVirtualKeyBalance(playerId, "test_key"),
            "null idempotency key should always execute");
    }
}
