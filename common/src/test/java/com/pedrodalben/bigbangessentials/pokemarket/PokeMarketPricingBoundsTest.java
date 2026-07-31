package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.pokemarket.service.MarketPricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Price bounds come from pokemarket.json (defaults min 0.01 / max 1000000.00); the sale tax is configurable and feeds fee(). */
class PokeMarketPricingBoundsTest {
    @Test void validPriceIsNormalized() {
        assertEquals(new BigDecimal("100.00"), MarketPricingService.validateBounds(new BigDecimal("100.00")));
        assertEquals(new BigDecimal("0.01"), MarketPricingService.validateBounds(new BigDecimal("0.01")));
    }

    @Test void priceAboveMaximumIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.validateBounds(new BigDecimal("1000000.01")));
    }

    @Test void nonPositivePriceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.validateBounds(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.validateBounds(new BigDecimal("-5.00")));
    }

    @Test void configurableTaxDrivesFee() {
        assertEquals(new BigDecimal("5.00"), MarketPricingService.fee(new BigDecimal("100.00"), new BigDecimal("5.0"), BigDecimal.ZERO, BigDecimal.ZERO));
        assertEquals(new BigDecimal("95.00"), MarketPricingService.net(new BigDecimal("100.00"), new BigDecimal("5.00")));
    }

    @Test void taxCannotConsumeTheSellerAmount() {
        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.net(new BigDecimal("100.00"), new BigDecimal("100.00")));
    }
}
