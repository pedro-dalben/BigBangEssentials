package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.rankup.database.RankupRepository;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTransaction;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTransactionStatus;
import com.pedrodalben.bigbangessentials.rankup.service.RankupPromotionService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankupEconomyFacadeTest {
    @Test
    void compensationCreditsThroughTheEconomyManagerWithTheOriginalKey() {
        UUID player = UUID.randomUUID();
        RankupManager rankup = Mockito.mock(RankupManager.class);
        RankupRepository repository = Mockito.mock(RankupRepository.class);
        EconomyManager economy = Mockito.mock(EconomyManager.class);
        RankupTransaction charged = new RankupTransaction("transaction", player, "ladder", "member", "trainer",
                BigDecimal.TEN, 0, RankupTransactionStatus.MONEY_DEBITED, "rankup-key", null,
                System.currentTimeMillis(), null).withMoneyDebited(true);
        EconomyOperationReceipt credited = new EconomyOperationReceipt(UUID.randomUUID(), player, BigDecimal.TEN,
                EconomyOperationStatus.COMPLETED, BigDecimal.ZERO, BigDecimal.TEN, "rankup:refund:rankup-key");
        when(rankup.getRepository()).thenReturn(repository);
        when(repository.saveTransaction(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(economy.credit(eq(player), eq(BigDecimal.TEN), eq("rankup:refund:rankup-key"), eq("Rankup refund"),
                eq(Map.of("source", "rankup", "reference", "transaction")))).thenReturn(credited);

        RankupTransaction result;
        try (MockedStatic<EconomyManager> manager = Mockito.mockStatic(EconomyManager.class)) {
            manager.when(EconomyManager::getInstance).thenReturn(economy);
            result = new RankupPromotionService(rankup).compensate(player, charged).join();
        }

        assertFalse(result.moneyDebited());
        assertTrue(result.compensated());
        verify(economy).credit(player, BigDecimal.TEN, "rankup:refund:rankup-key", "Rankup refund",
                Map.of("source", "rankup", "reference", "transaction"));
    }
}
