package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary;
import com.pedrodalben.bigbangessentials.pokemarket.service.MarketPricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

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

    @Test void dynamicMinimumPriceByPerfectIvs() {
        var summaryF5 = new PokemonSummary(UUID.randomUUID(), "Pikachu", "", false, 50, 5);
        var summaryF6 = new PokemonSummary(UUID.randomUUID(), "Pikachu", "", false, 50, 6);

        assertEquals(new BigDecimal("10.00"), MarketPricingService.calculateMinimumPrice(summaryF5));
        assertEquals(new BigDecimal("20.00"), MarketPricingService.calculateMinimumPrice(summaryF6));

        // Accept at or above minimum
        assertEquals(new BigDecimal("10.00"), MarketPricingService.validateBounds(new BigDecimal("10.00"), summaryF5));
        assertEquals(new BigDecimal("20.00"), MarketPricingService.validateBounds(new BigDecimal("20.00"), summaryF6));

        // Reject below trait minimum
        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.validateBounds(new BigDecimal("9.99"), summaryF5));
        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.validateBounds(new BigDecimal("19.99"), summaryF6));
    }

    @Test void dynamicMinimumPriceByLegendaryMythicalSpeciesAndShiny() {
        var legendary = new PokemonSummary(UUID.randomUUID(), "Zapdos", "", false, 70, 3, true, false, false);
        var mythical = new PokemonSummary(UUID.randomUUID(), "Mew", "", false, 70, 3, false, true, false);
        var shiny = new PokemonSummary(UUID.randomUUID(), "Rattata", "", true, 10, 0, false, false, false);
        var speciesMewtwo = new PokemonSummary(UUID.randomUUID(), "Mewtwo", "", false, 70, 3, true, false, false);

        assertEquals(new BigDecimal("100.00"), MarketPricingService.calculateMinimumPrice(legendary));
        assertEquals(new BigDecimal("100.00"), MarketPricingService.calculateMinimumPrice(mythical));
        assertEquals(new BigDecimal("50.00"), MarketPricingService.calculateMinimumPrice(shiny));
        // Mewtwo is both Legendary (100.00) and in minBySpecies (500.00), max rule applies -> 500.00
        assertEquals(new BigDecimal("500.00"), MarketPricingService.calculateMinimumPrice(speciesMewtwo));

        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.validateBounds(new BigDecimal("99.99"), legendary));
        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.validateBounds(new BigDecimal("499.99"), speciesMewtwo));
    }
}
