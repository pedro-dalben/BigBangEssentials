package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Money validation and deterministic fee math; Pokémon classification belongs to the Cobblemon adapter. */
public final class MarketPricingService {
    private MarketPricingService() {}

    public static BigDecimal normalize(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0 || value.scale() > 2) throw new IllegalArgumentException("Money must be positive with at most two decimals");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Calculates the minimum price based on global min price and Pokémon-specific rules (IVs, rarity, shiny, species). */
    public static BigDecimal calculateMinimumPrice(PokemonSummary summary) {
        BigDecimal min = ConfigManager.getPokeMarketMinPriceDecimal();
        if (summary == null) return min;

        BigDecimal ivMin = ConfigManager.getPokeMarketMinPriceByPerfectIvsDecimal(summary.perfectIvs());
        if (ivMin.compareTo(min) > 0) min = ivMin;

        if (summary.shiny()) {
            BigDecimal shinyMin = ConfigManager.getPokeMarketMinShinyPriceDecimal();
            if (shinyMin.compareTo(min) > 0) min = shinyMin;
        }

        if (summary.isLegendary()) {
            BigDecimal legMin = ConfigManager.getPokeMarketMinLegendaryPriceDecimal();
            if (legMin.compareTo(min) > 0) min = legMin;
        }

        if (summary.isMythical()) {
            BigDecimal mythMin = ConfigManager.getPokeMarketMinMythicalPriceDecimal();
            if (mythMin.compareTo(min) > 0) min = mythMin;
        }

        if (summary.isUltraBeast()) {
            BigDecimal ubMin = ConfigManager.getPokeMarketMinUltraBeastPriceDecimal();
            if (ubMin.compareTo(min) > 0) min = ubMin;
        }

        if (summary.species() != null) {
            BigDecimal specMin = ConfigManager.getPokeMarketMinPriceBySpeciesDecimal(summary.species());
            if (specMin.compareTo(min) > 0) min = specMin;
        }

        return min;
    }

    /** Normalizes and enforces the configured min/max listing price bounds. */
    public static BigDecimal validateBounds(BigDecimal value) {
        return validateBounds(value, null);
    }

    /** Normalizes and enforces the configured min/max listing price bounds for a specific Pokémon summary. */
    public static BigDecimal validateBounds(BigDecimal value, PokemonSummary summary) {
        BigDecimal normalized = normalize(value);
        BigDecimal min = normalize(calculateMinimumPrice(summary));
        BigDecimal max = normalize(ConfigManager.getPokeMarketMaxPriceDecimal());
        if (min.compareTo(max) > 0) throw new IllegalStateException("Configured minimum price (" + min.toPlainString() + ") exceeds maximum price (" + max.toPlainString() + ")");
        if (normalized.compareTo(min) < 0) throw new IllegalArgumentException("Price below minimum (" + min.toPlainString() + ")");
        if (normalized.compareTo(max) > 0) throw new IllegalArgumentException("Price above maximum (" + max.toPlainString() + ")");
        return normalized;
    }

    public static BigDecimal fee(BigDecimal amount, BigDecimal percentage, BigDecimal fixed, BigDecimal minimum) {
        BigDecimal base = normalize(amount);
        BigDecimal result = base.multiply(percentage.max(BigDecimal.ZERO)).movePointLeft(2).add(fixed.max(BigDecimal.ZERO));
        return result.max(minimum.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal net(BigDecimal gross, BigDecimal saleTax) {
        BigDecimal result = normalize(gross).subtract(saleTax.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
        if (result.signum() <= 0) throw new IllegalArgumentException("Sale tax must leave a positive seller amount");
        return result;
    }
}
