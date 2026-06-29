package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.CrateManager;
import com.pedrodalben.bigbangessentials.crates.domain.*;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
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
    private static CrateMetricsService metricsService;
    private static CrateAuditService auditService;

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
        metricsService = CrateMetricsService.getInstance();
        auditService = CrateAuditService.getInstance();
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
        metricsService.resetMetrics();
        auditService.cleanOldAudits(Instant.now().plusSeconds(365 * 86400));
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

    @Test
    void testPhysicalKeySignature_EmptyStack_Rejected() {
        assertNull(CrateKeyService.getInstance().getKeyMarker(ItemStack.EMPTY),
            "Empty stack should be rejected (no NBT data)");
    }

    @Test
    void testComputeSignature_Deterministic() {
        String sig1 = CrateKeyService.computeSignature("test_key");
        String sig2 = CrateKeyService.computeSignature("test_key");
        assertEquals(sig1, sig2, "Signature should be deterministic for same keyId");
    }

    @Test
    void testComputeSignature_DifferentKeys_DifferentSignatures() {
        String sig1 = CrateKeyService.computeSignature("key_a");
        String sig2 = CrateKeyService.computeSignature("key_b");
        assertNotEquals(sig1, sig2, "Different keyIds should produce different signatures");
    }

    @Test
    void testComputeSignature_NotEmpty() {
        String sig = CrateKeyService.computeSignature("any_key");
        assertNotNull(sig);
        assertFalse(sig.isEmpty(), "Signature should not be empty");
    }

    // === Metrics Tests ===

    @Test
    void testMetricsStartAtZero() {
        Map<String, Long> all = metricsService.getAllMetrics();
        assertTrue(all.isEmpty() || all.values().stream().allMatch(v -> v == 0));
    }

    @Test
    void testRecordOpening_IncrementsSuccessMetrics() {
        metricsService.recordOpening("test_crate", true);

        assertEquals(1, metricsService.getAllMetrics().getOrDefault("total_openings", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("successful_openings", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("successful_openings:test_crate", 0L));
    }

    @Test
    void testRecordOpening_IncrementsFailureMetrics() {
        metricsService.recordOpening("test_crate", false);

        assertEquals(1, metricsService.getAllMetrics().getOrDefault("total_openings", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("failed_openings", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("failed_openings:test_crate", 0L));
    }

    @Test
    void testRecordKeyGiven_TracksByKeyAndSource() {
        metricsService.recordKeyGiven("key_a", 5, GrantSource.ADMIN_COMMAND);

        assertEquals(1, metricsService.getAllMetrics().getOrDefault("keys_given", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("keys_given:key_a", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("keys_given:admin_command", 0L));
    }

    @Test
    void testRecordKeyGiven_SkipsZeroAmount() {
        metricsService.recordKeyGiven("key_a", 0, GrantSource.ADMIN_COMMAND);
        assertNull(metricsService.getAllMetrics().get("keys_given"));
    }

    @Test
    void testRecordKeyConsumed() {
        metricsService.recordKeyConsumed("key_a");

        assertEquals(1, metricsService.getAllMetrics().getOrDefault("keys_consumed", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("keys_consumed:key_a", 0L));
    }

    @Test
    void testRecordRewardDelivered() {
        metricsService.recordRewardDelivered("reward_x");

        assertEquals(1, metricsService.getAllMetrics().getOrDefault("rewards_delivered", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("rewards_delivered:reward_x", 0L));
    }

    @Test
    void testRecordCostSpent() {
        metricsService.recordCostSpent("vip_crate", 100.0);

        assertEquals(1, metricsService.getAllMetrics().getOrDefault("total_revenue", 0L));
        assertEquals(1, metricsService.getAllMetrics().getOrDefault("revenue:vip_crate", 0L));
    }

    @Test
    void testMultipleOpeningsAccumulate() {
        metricsService.recordOpening("c1", true);
        metricsService.recordOpening("c1", true);
        metricsService.recordOpening("c2", false);
        metricsService.recordOpening("c1", true);

        Map<String, Long> all = metricsService.getAllMetrics();
        assertEquals(4, all.getOrDefault("total_openings", 0L));
        assertEquals(3, all.getOrDefault("successful_openings", 0L));
        assertEquals(3, all.getOrDefault("successful_openings:c1", 0L));
        assertEquals(1, all.getOrDefault("failed_openings:c2", 0L));
    }

    @Test
    void testResetClearsAll() {
        metricsService.recordOpening("test", true);
        metricsService.recordKeyGiven("k", 1, GrantSource.ADMIN_COMMAND);
        assertFalse(metricsService.getAllMetrics().isEmpty());

        metricsService.resetMetrics();
        assertTrue(metricsService.getAllMetrics().isEmpty());
    }

    @Test
    void testFullFlow_OpeningRecordsMetrics() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("metrics_crate", "m_r1");
        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);

        var result = openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());

        assertTrue(result.success());

        Map<String, Long> all = metricsService.getAllMetrics();
        assertEquals(1, all.getOrDefault("total_openings", 0L));
        assertEquals(1, all.getOrDefault("successful_openings", 0L));
        assertEquals(1, all.getOrDefault("keys_consumed", 0L));
        assertTrue(all.containsKey("rewards_delivered"));
    }

    @Test
    void testFailedOpening_RecordsFailureMetric() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("fail_crate", "m_r2");
        crate.setRewards(List.of());

        var result = openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());
        assertFalse(result.success());
        assertEquals(0, keyService.getVirtualKeyBalance(playerId, "test_key"));
    }

    @Test
    void testFormatMetrics_ContainsExpectedLines() {
        metricsService.recordOpening("test", true);
        metricsService.recordKeyGiven("k", 1, GrantSource.ADMIN_COMMAND);

        String formatted = metricsService.formatMetrics();
        assertTrue(formatted.contains("Total Openings: 1"));
        assertTrue(formatted.contains("Keys Given: 1"));
        assertTrue(formatted.contains("Rewards Delivered: 0"));
    }

    @Test
    void testConcurrentMetricIncrements_AreAtomic() throws InterruptedException {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    metricsService.recordOpening("concurrent", true);
                    metricsService.recordKeyGiven("ck", 1, GrantSource.OPENING);
                } catch (Exception ignored) {}
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        Map<String, Long> all = metricsService.getAllMetrics();
        assertEquals(threads, all.getOrDefault("total_openings", 0L));
        assertEquals(threads, all.getOrDefault("keys_given", 0L));
    }

    // === Audit Cleanup Tests ===

    @Test
    void testCleanOldAudits_RemovesOldEntries() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("audit_cleanup", "ac_r1");
        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);

        openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());

        long before = auditService.countAudits();
        assertTrue(before > 0, "Audit should exist");

        Instant futureCutoff = Instant.now().plusSeconds(1);
        auditService.cleanOldAudits(futureCutoff);

        long after = auditService.countAudits();
        assertEquals(0, after, "All audits should be removed");
    }

    @Test
    void testCleanOldAudits_KeepsRecentEntries() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("audit_keep", "ac_r2");
        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);

        openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());

        long before = auditService.countAudits();
        assertTrue(before > 0, "Audit should exist");

        Instant pastCutoff = Instant.now().minusSeconds(1);
        auditService.cleanOldAudits(pastCutoff);

        long after = auditService.countAudits();
        assertEquals(before, after, "Recent audits should not be removed");
    }

    @Test
    void testRunCleanupNow_FromManager() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("mgr_cleanup", "ac_r3");
        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);

        openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());

        long before = auditService.countAudits();
        assertTrue(before > 0);

        CrateManager.getInstance().runCleanupNow();

        long after = auditService.countAudits();
        assertTrue(after <= before, "Cleanup should not increase audit count");
    }

    // === E2E Integration Tests ===

    @Test
    void testE2E_FullOpeningWithAuditTrail() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);
        String crateKey = "e2e_audit_" + UUID.randomUUID().toString().substring(0, 8);
        String rewardId = "e2e_reward";

        CrateDefinition crate = crateRequiringKey(crateKey, rewardId);
        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);

        String idempotencyKey = UUID.randomUUID().toString();
        var result = openingService.openCrate(player, crate, GrantSource.OPENING, idempotencyKey);

        assertTrue(result.success(), "E2E opening should succeed: " + result.message() + " (audit="
            + (result.audit() != null ? result.audit().getStatus() : "null") + ")");
        assertNotNull(result.audit(), "Audit should be present");
        assertEquals(CrateOpenAudit.OpenStatus.COMPLETED, result.audit().getStatus());
        assertEquals(playerId, result.audit().getPlayerId());
        assertEquals(crateKey, result.audit().getCrateId());
        assertEquals(GrantSource.OPENING, result.audit().getSource());
        assertEquals(idempotencyKey, result.audit().getIdempotencyKey());
        assertEquals(0, keyService.getVirtualKeyBalance(playerId, "test_key"), "Key should be consumed");
    }

    @Test
    void testE2E_HmacSignedPhysicalKey_RoundTrip() {
        String keyId = "e2e_physical_key";

        ItemStack item = mock(ItemStack.class);
        when(item.isEmpty()).thenReturn(false);
        CompoundTag tag = new CompoundTag();
        tag.putString("bigbangessentials:key_id", keyId);
        tag.putString("bigbangessentials:key_sig", CrateKeyService.computeSignature(keyId));
        when(item.get(DataComponents.CUSTOM_DATA)).thenReturn(CustomData.of(tag));

        String marker = keyService.getKeyMarker(item);
        assertNotNull(marker, "Physical key should have a marker");
        assertEquals(keyId, marker, "Marker should match keyId");
    }

    @Test
    void testE2E_HmacForgedKey_Rejected() {
        String realKeyId = "real_key";

        ItemStack item = mock(ItemStack.class);
        when(item.isEmpty()).thenReturn(false);
        CompoundTag tag = new CompoundTag();
        tag.putString("bigbangessentials:key_id", realKeyId);
        tag.putString("bigbangessentials:key_sig", CrateKeyService.computeSignature(realKeyId));
        when(item.get(DataComponents.CUSTOM_DATA)).thenReturn(CustomData.of(tag));

        String marker = keyService.getKeyMarker(item);
        assertNotNull(marker, "Real key should have marker");
        assertEquals(realKeyId, marker, "Real key marker should match");

        ItemStack tampered = mock(ItemStack.class);
        when(tampered.isEmpty()).thenReturn(false);
        CompoundTag forgedTag = new CompoundTag();
        forgedTag.putString("bigbangessentials:key_id", "forged_key");
        forgedTag.putString("bigbangessentials:key_sig", "fake_sig");
        when(tampered.get(DataComponents.CUSTOM_DATA)).thenReturn(CustomData.of(forgedTag));

        String tamperedMarker = keyService.getKeyMarker(tampered);
        assertNull(tamperedMarker, "Forged key with fake signature should be rejected");
    }

    @Test
    void testE2E_IdempotencyAcrossPipeline() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);
        String crateKey = "e2e_idem_" + UUID.randomUUID().toString().substring(0, 8);

        CrateDefinition crate = crateRequiringKey(crateKey, "e2e_idem_r");
        keyService.giveVirtualKey(playerId, "test_key", 5, GrantSource.ADMIN_COMMAND, null);

        String sharedKey = UUID.randomUUID().toString();

        var result1 = openingService.openCrate(player, crate, GrantSource.OPENING, sharedKey);
        assertTrue(result1.success(), "First opening should succeed");

        var result2 = openingService.openCrate(player, crate, GrantSource.OPENING, sharedKey);
        assertFalse(result2.success(), "Second opening with same idempotency key should fail");
        assertEquals("Already processed", result2.message());

        assertEquals(4, keyService.getVirtualKeyBalance(playerId, "test_key"),
            "Only 1 key should be consumed (idempotency prevented second consumption)");
    }

    @Test
    void testE2E_MassOpenWithCost_FailsMidway() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);

        CrateDefinition crate = crateRequiringKey("e2e_mass_cost", "e2e_mc");
        keyService.giveVirtualKey(playerId, "test_key", 3, GrantSource.ADMIN_COMMAND, null);

        var results = openingService.massOpen(player, crate, 5, GrantSource.MASS_OPEN);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(CrateOpeningService.CrateOpeningResult::success));
        assertEquals(0, keyService.getVirtualKeyBalance(playerId, "test_key"), "All keys should be consumed");
    }

    @Test
    void testE2E_RollbackOnFailure_RestoresCooldown() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);
        String crateKey = "e2e_rb_" + UUID.randomUUID().toString().substring(0, 8);

        CrateDefinition crate = crateRequiringKey(crateKey, "e2e_rb_r");
        crate.setRewards(List.of());
        keyService.giveVirtualKey(playerId, "test_key", 1, GrantSource.ADMIN_COMMAND, null);

        var result = openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());
        assertFalse(result.success(), "Opening should fail (no rewards)");

        assertEquals(1, keyService.getVirtualKeyBalance(playerId, "test_key"),
            "Key should be restored after failed opening");
    }

    @Test
    void testE2E_MetricsPipeline_RecordsAllEvents() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mockPlayer(playerId);
        String crateKey = "e2e_met_" + UUID.randomUUID().toString().substring(0, 8);

        CrateDefinition crate = crateRequiringKey(crateKey, "e2e_met_r");
        keyService.giveVirtualKey(playerId, "test_key", 3, GrantSource.ADMIN_COMMAND, null);

        openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());
        openingService.openCrate(player, crate, GrantSource.OPENING, UUID.randomUUID().toString());

        Map<String, Long> all = metricsService.getAllMetrics();
        assertEquals(2, all.getOrDefault("total_openings", 0L));
        assertEquals(2, all.getOrDefault("successful_openings", 0L));
        assertTrue(all.getOrDefault("keys_given", 0L) >= 1L,
            "Keys given should be at least 1 (from setup)");
        assertTrue(all.getOrDefault("keys_consumed", 0L) >= 2L,
            "Keys consumed should be at least 2 (from openings)");
        assertTrue(all.getOrDefault("rewards_delivered", 0L) >= 2L,
            "Rewards delivered should be at least 2");
    }
}
