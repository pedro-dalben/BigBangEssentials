package com.pedrodalben.bigbangessentials.shop;

import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferReceipt;
import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopTransactionEconomyFailureTest {
    @Test
    void technicalFailuresAreRetryableAfterCancellation() {
        for (CommercialTransferStatus status : new CommercialTransferStatus[]{
                CommercialTransferStatus.DATABASE_UNAVAILABLE,
                CommercialTransferStatus.EXECUTOR_SATURATED,
                CommercialTransferStatus.TECHNICAL_FAILURE}) {
            assertEquals(ShopTransaction.ResultType.ERROR,
                    ShopTransaction.classifyEconomyFailure(receipt(status), "tx-1", true).type);
        }
    }

    @Test
    void realReconciliationRemainsBlocked() {
        CommercialTransferReceipt receipt = receipt(CommercialTransferStatus.RECONCILIATION_REQUIRED);
        assertTrue(ShopTransaction.requiresEconomyReconciliation(receipt));
        assertEquals(ShopTransaction.ResultType.RECOVERY_REQUIRED,
                ShopTransaction.classifyEconomyFailure(receipt, "tx-2", true).type);
        assertTrue(ChestShopTransactionJournal.blocks(ChestShopTransactionJournal.Status.RECOVERY_REQUIRED));
    }

    @Test
    void failedCancellationPersistenceRequiresRecovery() {
        assertEquals(ShopTransaction.ResultType.RECOVERY_REQUIRED,
                ShopTransaction.classifyEconomyFailure(receipt(CommercialTransferStatus.TECHNICAL_FAILURE), "tx-3", false).type);
    }

    private static CommercialTransferReceipt receipt(CommercialTransferStatus status) {
        UUID sender = UUID.randomUUID();
        return new CommercialTransferReceipt(UUID.randomUUID(), sender, UUID.randomUUID(), BigDecimal.ONE,
                null, null, null, null, "key", null, status, status.name(), false, System.currentTimeMillis());
    }
}
