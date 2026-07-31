package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.config.ConfigManager;
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

    /** Normalizes and enforces the configured min/max listing price bounds. */
    public static BigDecimal validateBounds(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        BigDecimal min = normalize(ConfigManager.getPokeMarketMinPriceDecimal());
        BigDecimal max = normalize(ConfigManager.getPokeMarketMaxPriceDecimal());
        if (min.compareTo(max) > 0) throw new IllegalStateException("Configured minimum price exceeds maximum price");
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
