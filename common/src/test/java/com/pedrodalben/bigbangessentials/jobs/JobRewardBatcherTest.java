package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobRewardBatcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class JobRewardBatcherTest {

    @Test
    void testRewardBatchingAccumulatesAndFlushesSingleCredit() {
        EconomyManager manager = Mockito.mock(EconomyManager.class);
        UUID player = UUID.randomUUID();

        EconomyOperationReceipt receipt = new EconomyOperationReceipt(
                UUID.randomUUID(), player, new BigDecimal("15.00"),
                EconomyOperationStatus.COMPLETED, BigDecimal.ZERO, new BigDecimal("15.00"), "key"
        );

        Mockito.when(manager.creditAsync(eq(player), any(BigDecimal.class), anyString(), anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(receipt));

        try (MockedStatic<EconomyManager> economyMock = Mockito.mockStatic(EconomyManager.class)) {
            economyMock.when(EconomyManager::getInstance).thenReturn(manager);

            JobRewardBatcher batcher = JobRewardBatcher.getInstance();
            batcher.addPendingReward(player, "miner", new BigDecimal("5.00"));
            batcher.addPendingReward(player, "miner", new BigDecimal("10.00"));

            batcher.flushPlayer(player).join();

            ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            Mockito.verify(manager, Mockito.times(1)).creditAsync(eq(player), amountCaptor.capture(), anyString(), anyString(), anyMap());

            assertEquals(new BigDecimal("15.00"), amountCaptor.getValue());
        }
    }

    @Test
    void testFailedFlushReEnqueuesAndRetries() {
        EconomyManager manager = Mockito.mock(EconomyManager.class);
        UUID player = UUID.randomUUID();

        EconomyOperationReceipt failed = new EconomyOperationReceipt(
                UUID.randomUUID(), player, new BigDecimal("12.00"),
                EconomyOperationStatus.FAILED, BigDecimal.ZERO, BigDecimal.ZERO, "key"
        );

        EconomyOperationReceipt success = new EconomyOperationReceipt(
                UUID.randomUUID(), player, new BigDecimal("12.00"),
                EconomyOperationStatus.COMPLETED, BigDecimal.ZERO, new BigDecimal("12.00"), "key2"
        );

        AtomicInteger callCount = new AtomicInteger(0);
        Mockito.when(manager.creditAsync(eq(player), any(BigDecimal.class), anyString(), anyString(), anyMap()))
                .thenAnswer(inv -> {
                    int count = callCount.incrementAndGet();
                    return CompletableFuture.completedFuture(count == 1 ? failed : success);
                });

        try (MockedStatic<EconomyManager> economyMock = Mockito.mockStatic(EconomyManager.class)) {
            economyMock.when(EconomyManager::getInstance).thenReturn(manager);

            JobRewardBatcher batcher = JobRewardBatcher.getInstance();
            batcher.addPendingReward(player, "miner", new BigDecimal("12.00"));

            batcher.flushPlayer(player).join();

            batcher.flushPlayer(player).join();

            Mockito.verify(manager, Mockito.times(2)).creditAsync(eq(player), any(BigDecimal.class), anyString(), anyString(), anyMap());
        }
    }

    @Test
    void testFlushAllReturnsCombinedFuture() {
        EconomyManager manager = Mockito.mock(EconomyManager.class);
        UUID player = UUID.randomUUID();

        EconomyOperationReceipt receipt = new EconomyOperationReceipt(
                UUID.randomUUID(), player, BigDecimal.ONE,
                EconomyOperationStatus.COMPLETED, BigDecimal.ZERO, BigDecimal.ONE, "key"
        );

        Mockito.when(manager.creditAsync(eq(player), any(BigDecimal.class), anyString(), anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(receipt));

        try (MockedStatic<EconomyManager> economyMock = Mockito.mockStatic(EconomyManager.class)) {
            economyMock.when(EconomyManager::getInstance).thenReturn(manager);

            JobRewardBatcher batcher = JobRewardBatcher.getInstance();
            batcher.addPendingReward(player, "miner", BigDecimal.ONE);

            CompletableFuture<Void> future = batcher.flushAll();
            assertDoesNotThrow(() -> future.join());
            Mockito.verify(manager, Mockito.atLeastOnce()).creditAsync(eq(player), any(BigDecimal.class), anyString(), anyString(), anyMap());
        }
    }
}
