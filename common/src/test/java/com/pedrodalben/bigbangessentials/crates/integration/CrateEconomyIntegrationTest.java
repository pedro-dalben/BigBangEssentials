package com.pedrodalben.bigbangessentials.crates.integration;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateEconomyIntegrationTest {
    @Test
    void creditsAndDebitsThroughTheEconomyManager() {
        EconomyManager manager = Mockito.mock(EconomyManager.class);
        UUID player = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(10.0);
        EconomyOperationReceipt completed = new EconomyOperationReceipt(UUID.randomUUID(), player, BigDecimal.TEN,
                EconomyOperationStatus.COMPLETED, BigDecimal.ZERO, BigDecimal.TEN, "key");
        when(manager.isEnabled()).thenReturn(true);
        when(manager.debit(eq(player), eq(amount), eq("crate-debit"), eq("Crate purchase"),
                eq(Map.of("source", "crates", "reference", "crate-debit")))).thenReturn(completed);
        when(manager.credit(eq(player), eq(amount), eq("crate-credit"), eq("Crate refund"),
                eq(Map.of("source", "crates", "reference", "crate-credit")))).thenReturn(completed);

        try (MockedStatic<EconomyManager> economy = Mockito.mockStatic(EconomyManager.class)) {
            economy.when(EconomyManager::getInstance).thenReturn(manager);
            CrateEconomyIntegration integration = CrateEconomyIntegration.getInstance();

            assertTrue(integration.withdraw(player, 10, "Crate purchase", "crate-debit"));
            assertTrue(integration.deposit(player, 10, "Crate refund", "crate-credit"));
        }

        verify(manager).debit(player, amount, "crate-debit", "Crate purchase",
                Map.of("source", "crates", "reference", "crate-debit"));
        verify(manager).credit(player, amount, "crate-credit", "Crate refund",
                Map.of("source", "crates", "reference", "crate-credit"));
    }
}
