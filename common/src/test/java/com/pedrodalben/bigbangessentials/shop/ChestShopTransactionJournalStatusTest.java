package com.pedrodalben.bigbangessentials.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChestShopTransactionJournalStatusTest {
    @Test
    void terminalJsonStatusesDoNotBlockTheNextClick() {
        assertFalse(ChestShopTransactionJournal.blocks(ChestShopTransactionJournal.Status.CANCELLED));
        assertFalse(ChestShopTransactionJournal.blocks(ChestShopTransactionJournal.Status.ROLLED_BACK));
        assertTrue(ChestShopTransactionJournal.blocks(ChestShopTransactionJournal.Status.PENDING));
        assertTrue(ChestShopTransactionJournal.blocks(ChestShopTransactionJournal.Status.RECOVERY_REQUIRED));
        assertEquals(ChestShopTransactionJournal.Status.CANCELLED,
                ChestShopTransactionJournal.statusOf("CANCELLED"));
        assertEquals(ChestShopTransactionJournal.Status.ROLLED_BACK,
                ChestShopTransactionJournal.statusOf("ROLLED_BACK"));
    }
}
