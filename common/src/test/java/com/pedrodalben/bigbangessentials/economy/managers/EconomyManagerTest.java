package com.pedrodalben.bigbangessentials.economy.managers;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class EconomyManagerTest {
    @Test
    void rejectsCreditsThatWouldExceedTheBalanceLimit() {
        assertNull(EconomyManager.updatedBalance(new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("100"), false));
        assertEquals(new BigDecimal("120"), EconomyManager.updatedBalance(new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("1000"), false));
    }
}
