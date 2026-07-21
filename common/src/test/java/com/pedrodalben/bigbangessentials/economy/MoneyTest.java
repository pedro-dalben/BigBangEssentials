package com.pedrodalben.bigbangessentials.economy;

import com.pedrodalben.bigbangessentials.api.economy.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void convertsAndRejectsUnsafeValues() {
        assertEquals(1575, Money.from(new BigDecimal("15.75"), 2, RoundingMode.HALF_UP, false).minorUnits());
        assertEquals(158, Money.from(new BigDecimal("1.575"), 2, RoundingMode.HALF_UP, false).minorUnits());
        assertThrows(IllegalArgumentException.class, () -> Money.from(new BigDecimal("-1"), 2, RoundingMode.HALF_UP, false));
        assertThrows(IllegalArgumentException.class, () -> Money.from(new BigDecimal("1E100"), 2, RoundingMode.HALF_UP, false));
    }
}
