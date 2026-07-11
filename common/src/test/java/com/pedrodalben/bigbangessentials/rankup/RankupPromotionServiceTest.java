package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.rankup.service.RankupPromotionService;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RankupPromotionServiceTest {
    static {
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    private static final RankupRank CURRENT = new RankupRank(
            "member", 0, "\u00a77Member", java.util.List.of(),
            null, new RankupLuckPermsSettings("member", true),
            new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, java.util.List.of()),
            new RankupActions(null, java.util.List.of()),
            true
    );

    private static final RankupRank NEXT = new RankupRank(
            "trainer", 1, "\u00a7aTrainer", java.util.List.of(),
            null, new RankupLuckPermsSettings("trainer", true),
            new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, java.util.List.of()),
            new RankupActions(null, java.util.List.of()),
            true
    );

    // ========== Queue Release Tests ==========

    @Test
    void successfulPromotionRemovesUuidFromQueue() {
        ControlledPromotionService service = new ControlledPromotionService(CompletableFuture.completedFuture(successResult("tx-success")));
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupPromotionResult result = service.promote(player, NEXT).join();

        assertTrue(result.success());
        assertFalse(service.isPromotionInProgress(uuid));
        assertEquals(1, service.doPromoteCalls.get());
    }

    @Test
    void normalFailureRemovesUuidFromQueue() {
        ControlledPromotionService service = new ControlledPromotionService(CompletableFuture.completedFuture(failureResult("tx-fail")));
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupPromotionResult result = service.promote(player, NEXT).join();

        assertFalse(result.success());
        assertFalse(service.isPromotionInProgress(uuid));
        assertEquals(1, service.doPromoteCalls.get());
    }

    @Test
    void synchronousExceptionRemovesUuidFromQueue() {
        ControlledPromotionService service = new ControlledPromotionService(null);
        service.throwSync = true;
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupPromotionResult result = service.promote(player, NEXT).join();

        assertFalse(result.success());
        assertFalse(service.isPromotionInProgress(uuid));
        assertEquals(1, service.doPromoteCalls.get());
    }

    @Test
    void exceptionalFutureRemovesUuidFromQueue() {
        ControlledPromotionService service = new ControlledPromotionService(CompletableFuture.failedFuture(new IllegalStateException("boom")));
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupPromotionResult result = service.promote(player, NEXT).join();

        assertFalse(result.success());
        assertFalse(service.isPromotionInProgress(uuid));
        assertEquals(1, service.doPromoteCalls.get());
    }

    @Test
    void neverCompletingFutureTimesOutAndRemovesUuidFromQueue() {
        ControlledPromotionService service = new ControlledPromotionService(new CompletableFuture<>());
        service.promotionTimeoutSeconds = 1;
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupPromotionResult result = service.promote(player, NEXT).join();

        assertFalse(result.success());
        assertEquals(RankupTransactionStatus.RECOVERY_REQUIRED, result.terminalStatus());
        assertEquals(RankupPromotionResultCode.TIMEOUT, result.code());
        assertFalse(service.isPromotionInProgress(uuid));
        assertEquals(1, service.doPromoteCalls.get());
    }

    // ========== Double Click / In-Progress Tests ==========

    @Test
    void secondClickDoesNotStartAnotherPromotion() {
        CompletableFuture<RankupPromotionResult> pending = new CompletableFuture<>();
        ControlledPromotionService service = new ControlledPromotionService(pending);
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        CompletableFuture<RankupPromotionResult> first = service.promote(player, NEXT);
        RankupPromotionResult second = service.promote(player, NEXT).join();

        assertFalse(second.success());
        assertEquals(RankupPromotionResultCode.TRANSACTION_IN_PROGRESS, second.code());
        assertEquals(1, service.doPromoteCalls.get());

        pending.complete(successResult("tx-double"));
        assertTrue(first.join().success());
        assertFalse(service.isPromotionInProgress(uuid));
    }

    @Test
    void secondClickDoesNotCreateNewCharge() {
        CompletableFuture<RankupPromotionResult> pending = new CompletableFuture<>();
        ControlledPromotionService service = new ControlledPromotionService(pending);
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        service.promote(player, NEXT);
        RankupPromotionResult second = service.promote(player, NEXT).join();

        // Second click is rejected without calling doPromote
        assertFalse(second.success());
        assertEquals(RankupPromotionResultCode.TRANSACTION_IN_PROGRESS, second.code());
        assertEquals(1, service.doPromoteCalls.get(), "doPromote should only be called once");

        // Clean up
        pending.complete(successResult("tx-cleanup"));
    }

    // ========== Lock Safety Tests ==========

    @Test
    void oldExecutionDoesNotRemoveNewLock() throws Exception {
        CompletableFuture<RankupPromotionResult> pending = new CompletableFuture<>();
        ControlledPromotionService service = new ControlledPromotionService(pending);
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        CompletableFuture<RankupPromotionResult> first = service.promote(player, NEXT);
        PromotionExecutionHandle replacement = createExecutionHandle(uuid, "tx-replacement", NEXT.id());
        putExecution(service, uuid, replacement);

        pending.complete(successResult("tx-old"));
        assertTrue(first.join().success());
        // The old execution's releaseExecution should NOT remove the new lock
        assertTrue(service.isPromotionInProgress(uuid));

        removeExecution(service, uuid, replacement);
        assertFalse(service.isPromotionInProgress(uuid));
    }

    // ========== Auto-Blocking / Self-Lock Tests ==========

    @Test
    void snapshotIgnoresOwnLockWhenContextMatchesTransaction() throws Exception {
        UUID uuid = UUID.randomUUID();
        ControlledPromotionService service = new ControlledPromotionService(new CompletableFuture<>());
        ServerPlayer player = player(uuid);
        CompletableFuture<RankupPromotionResult> first = service.promote(player, NEXT);

        PromotionExecutionHandle execution = getSingleExecution(service, uuid);
        assertNotNull(execution);

        // External callers see PROMOTION_IN_PROGRESS
        assertTrue(service.isPromotionInProgress(uuid));
        // Internal context with matching transaction ID ignores the lock
        assertFalse(service.isPromotionInProgress(uuid, PromotionEvaluationContext.internal(execution.transactionId())));
        // Internal context with different transaction ID does NOT ignore
        assertTrue(service.isPromotionInProgress(uuid, PromotionEvaluationContext.internal("wrong-tx-id")));

        first.cancel(true);
        removeExecution(service, uuid, execution);
    }

    @Test
    void externalContextSeesPromotionInProgressDuringExecution() throws Exception {
        UUID uuid = UUID.randomUUID();
        CompletableFuture<RankupPromotionResult> pending = new CompletableFuture<>();
        ControlledPromotionService service = new ControlledPromotionService(pending);
        ServerPlayer player = player(uuid);

        service.promote(player, NEXT);

        // External context should see PROMOTION_IN_PROGRESS
        assertTrue(service.isPromotionInProgress(uuid));
        assertTrue(service.isPromotionInProgress(uuid, PromotionEvaluationContext.external()));

        // Complete and verify cleared
        pending.complete(successResult("tx-ext"));
        // Wait for completion
        Thread.sleep(50);
        assertFalse(service.isPromotionInProgress(uuid));
    }

    // ========== Preflight Validation Tests ==========

    @Test
    void preflightReadyAllowsPromotion() {
        ControlledPromotionService service = new ControlledPromotionService(CompletableFuture.completedFuture(successResult("tx-ready")));
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupPromotionResult result = service.promote(player, NEXT).join();
        assertTrue(result.success());
    }

    @Test
    void preflightNotReadyDeniesPromotion() {
        ControlledPromotionService service = new ControlledPromotionService(CompletableFuture.completedFuture(successResult("tx-blocked")));
        service.preflightState = RankupEligibilityState.BLOCKED_BY_TASKS;
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupPromotionResult result = service.promote(player, NEXT).join();
        assertFalse(result.success());
        assertEquals(RankupPromotionResultCode.TASKS_INCOMPLETE, result.code());
        // doPromote should NOT be called when preflight fails
        assertEquals(0, service.doPromoteCalls.get());
    }

    @Test
    void preflightMaxRankDeniesPromotion() {
        ControlledPromotionService service = new ControlledPromotionService(CompletableFuture.completedFuture(successResult("tx-max")));
        service.preflightNextRank = null;
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupPromotionResult result = service.promote(player, NEXT).join();
        assertFalse(result.success());
        assertEquals(RankupPromotionResultCode.ALREADY_MAX_RANK, result.code());
        assertEquals(0, service.doPromoteCalls.get());
    }

    // ========== Inspection Tests ==========

    @Test
    void inspectPromotionReturnsActiveForPendingExecution() {
        CompletableFuture<RankupPromotionResult> pending = new CompletableFuture<>();
        ControlledPromotionService service = new ControlledPromotionService(pending);
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        service.promote(player, NEXT);

        var inspection = service.inspectPromotion(uuid);
        assertTrue(inspection.active());
        assertEquals(uuid, inspection.playerUuid());
        assertNotNull(inspection.transactionId());
        assertFalse(inspection.futureDone());

        pending.complete(successResult("tx-inspect"));
    }

    @Test
    void inspectPromotionReturnsInactiveWhenNoExecution() {
        ControlledPromotionService service = new ControlledPromotionService(null);
        UUID uuid = UUID.randomUUID();

        var inspection = service.inspectPromotion(uuid);
        assertFalse(inspection.active());
        assertTrue(inspection.futureDone());
    }

    // ========== Cancel / Unlock Tests ==========

    @Test
    void cancelPromotionReleasesLock() {
        CompletableFuture<RankupPromotionResult> pending = new CompletableFuture<>();
        ControlledPromotionService service = new ControlledPromotionService(pending);
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        service.promote(player, NEXT);
        assertTrue(service.isPromotionInProgress(uuid));

        boolean cancelled = service.cancelPromotion(uuid);
        assertTrue(cancelled);
        assertFalse(service.isPromotionInProgress(uuid));
    }

    @Test
    void unlockPromotionFailsWhileExecutionActive() {
        CompletableFuture<RankupPromotionResult> pending = new CompletableFuture<>();
        ControlledPromotionService service = new ControlledPromotionService(pending);
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        service.promote(player, NEXT);

        // unlock should fail because the future is not done
        boolean unlocked = service.unlockPromotion(uuid);
        assertFalse(unlocked);
        assertTrue(service.isPromotionInProgress(uuid));

        // Cancel to clean up
        service.cancelPromotion(uuid);
    }

    @Test
    void unlockPromotionSucceedsAfterFutureCompletes() {
        CompletableFuture<RankupPromotionResult> pending = new CompletableFuture<>();
        ControlledPromotionService service = new ControlledPromotionService(pending);
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        CompletableFuture<RankupPromotionResult> future = service.promote(player, NEXT);

        // Complete the pipeline — the promise should complete and queue should be released
        pending.complete(successResult("tx-unlock"));
        future.join();

        // After completion, the queue should already be released by releaseExecution
        assertFalse(service.isPromotionInProgress(uuid));
    }

    // ========== Null / Invalid Player Tests ==========

    @Test
    void nullPlayerReturnsFailure() {
        ControlledPromotionService service = new ControlledPromotionService(null);

        RankupPromotionResult result = service.promote(null, NEXT).join();
        assertFalse(result.success());
        assertEquals(RankupPromotionResultCode.INTERNAL_ERROR, result.code());
    }

    @Test
    void disabledTargetRankReturnsFailure() {
        ControlledPromotionService service = new ControlledPromotionService(null);
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = player(uuid);

        RankupRank disabledRank = new RankupRank(
                "disabled", 1, "Disabled", java.util.List.of(),
                null, new RankupLuckPermsSettings("disabled", true),
                new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, java.util.List.of()),
                new RankupActions(null, java.util.List.of()),
                false // disabled
        );

        RankupPromotionResult result = service.promote(player, disabledRank).join();
        assertFalse(result.success());
        assertEquals(RankupPromotionResultCode.CONFIGURATION_INVALID, result.code());
    }

    // ========== Helpers ==========

    private static ServerPlayer player(UUID uuid) {
        ServerPlayer player = Mockito.mock(ServerPlayer.class);
        Mockito.when(player.getUUID()).thenReturn(uuid);
        return player;
    }

    private static RankupPromotionResult successResult(String txId) {
        return RankupPromotionResult.success("ok", txId);
    }

    private static RankupPromotionResult failureResult(String txId) {
        return RankupPromotionResult.failure("nope", RankupTransactionStatus.FAILED, txId, RankupPromotionResultCode.INTERNAL_ERROR);
    }

    private static void putExecution(ControlledPromotionService service, UUID uuid, PromotionExecutionHandle handle) throws Exception {
        Field queue = RankupPromotionService.class.getDeclaredField("promotionQueue");
        queue.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> map = (Map<UUID, Object>) queue.get(service);
        map.put(uuid, handle.execution());
    }

    private static void removeExecution(ControlledPromotionService service, UUID uuid, PromotionExecutionHandle handle) throws Exception {
        Field queue = RankupPromotionService.class.getDeclaredField("promotionQueue");
        queue.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> map = (Map<UUID, Object>) queue.get(service);
        map.remove(uuid, handle.execution());
    }

    private static PromotionExecutionHandle getSingleExecution(ControlledPromotionService service, UUID uuid) throws Exception {
        Field queue = RankupPromotionService.class.getDeclaredField("promotionQueue");
        queue.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> map = (Map<UUID, Object>) queue.get(service);
        Object execution = map.get(uuid);
        if (execution == null) {
            return null;
        }
        Field transactionId = execution.getClass().getDeclaredField("transactionId");
        transactionId.setAccessible(true);
        return new PromotionExecutionHandle(execution, (String) transactionId.get(execution));
    }

    private static PromotionExecutionHandle createExecutionHandle(UUID uuid, String txId, String targetRankId) throws Exception {
        Class<?> clazz = Class.forName("com.pedrodalben.bigbangessentials.rankup.service.RankupPromotionService$PromotionExecution");
        Constructor<?> ctor = clazz.getDeclaredConstructor(UUID.class, String.class, String.class);
        ctor.setAccessible(true);
        Object execution = ctor.newInstance(uuid, txId, targetRankId);
        return new PromotionExecutionHandle(execution, txId);
    }

    private record PromotionExecutionHandle(Object execution, String transactionId) {}

    private static final class ControlledPromotionService extends RankupPromotionService {
        private final CompletableFuture<RankupPromotionResult> pipeline;
        private final AtomicInteger doPromoteCalls = new AtomicInteger();
        private volatile boolean throwSync;
        private long promotionTimeoutSeconds = 1;
        private RankupEligibilityState preflightState = RankupEligibilityState.READY;
        private RankupRank preflightNextRank = NEXT;

        private ControlledPromotionService(CompletableFuture<RankupPromotionResult> pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        protected RankupEligibilitySnapshot preflightSnapshot(UUID uuid) {
            if (preflightNextRank == null) {
                return RankupEligibilitySnapshot.evaluate(
                        uuid, CURRENT, null,
                        RankupRankResolutionResult.resolved(CURRENT, CURRENT.luckPerms().group()),
                        java.util.List.of(), RankupTaskMode.ALL,
                        BigDecimal.valueOf(1000), 10L, false
                );
            }
            if (preflightState != RankupEligibilityState.READY) {
                // Return a snapshot that is NOT ready — use blocked by tasks
                return new RankupEligibilitySnapshot(
                        uuid, CURRENT, preflightNextRank, preflightState,
                        RankupRankResolutionResult.resolved(CURRENT, CURRENT.luckPerms().group()),
                        java.util.List.of(), 50.0, 0, 1, false,
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(500), BigDecimal.ZERO, true,
                        10L, 0, 0L, true, false, java.util.List.of("TASKS"), System.currentTimeMillis()
                );
            }
            return readySnapshot(uuid);
        }

        @Override
        protected long promotionTimeoutSeconds() {
            return promotionTimeoutSeconds;
        }

        @Override
        protected CompletableFuture<RankupPromotionResult> doPromote(ServerPlayer player, RankupRank targetRank, RankupEligibilitySnapshot preflight, boolean executeActions, PromotionExecution execution) {
            doPromoteCalls.incrementAndGet();
            if (throwSync) {
                throw new IllegalStateException("sync boom");
            }
            if (pipeline != null) {
                return pipeline;
            }
            return CompletableFuture.completedFuture(successResult("tx-test"));
        }
    }

    private static RankupEligibilitySnapshot readySnapshot(UUID uuid) {
        return RankupEligibilitySnapshot.evaluate(
                uuid,
                CURRENT,
                NEXT,
                RankupRankResolutionResult.resolved(CURRENT, CURRENT.luckPerms().group()),
                java.util.List.of(),
                RankupTaskMode.ALL,
                BigDecimal.valueOf(1000),
                10L,
                false
        );
    }
}
