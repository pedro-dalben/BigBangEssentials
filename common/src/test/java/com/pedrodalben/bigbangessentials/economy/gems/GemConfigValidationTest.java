package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.config.GemConfig;
import com.pedrodalben.bigbangessentials.economy.gems.config.GemConfigValidator;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GemConfigValidationTest {

    @Test
    void testDefaultConfigIsValid() {
        GemConfig config = new GemConfig();
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertTrue(result.valid, "Default config should be valid");
        assertTrue(result.errors.isEmpty());
    }

    @Test
    void testInvalidTechnicalId() {
        GemConfig config = new GemConfig();
        config.technicalId = "custom:gems";
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertFalse(result.valid);
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("technicalId")));
    }

    @Test
    void testNegativeStartingBalance() {
        GemConfig config = new GemConfig();
        config.balances.startingBalance = -10;
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertFalse(result.valid);
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("startingBalance")));
    }

    @Test
    void testStartingBalanceGreaterThanMax() {
        GemConfig config = new GemConfig();
        config.balances.startingBalance = 1000;
        config.balances.maxBalance = 500;
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertFalse(result.valid);
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("startingBalance cannot be greater than maxBalance")));
    }

    @Test
    void testNegativeMaxBalance() {
        GemConfig config = new GemConfig();
        config.balances.maxBalance = -100;
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertFalse(result.valid);
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("maxBalance")));
    }

    @Test
    void testAllowNegativeBalancesMustBeFalse() {
        GemConfig config = new GemConfig();
        config.balances.allowNegativeBalances = true;
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertFalse(result.valid);
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("allowNegativeBalances")));
    }

    @Test
    void testEmptyCommandRoot() {
        GemConfig config = new GemConfig();
        config.commands.root = "";
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertFalse(result.valid);
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("Command root cannot be empty")));
    }

    @Test
    void testInvalidLeaseSeconds() {
        GemConfig config = new GemConfig();
        config.reservations.defaultLeaseSeconds = 0;
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertFalse(result.valid);
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("defaultLeaseSeconds")));
    }

    @Test
    void testMaxLeaseLessThanDefault() {
        GemConfig config = new GemConfig();
        config.reservations.defaultLeaseSeconds = 100;
        config.reservations.maxLeaseSeconds = 50;
        GemConfigValidator.ValidationResult result = GemConfigValidator.validate(config);
        assertFalse(result.valid);
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("maxLeaseSeconds must be >= defaultLeaseSeconds")));
    }
}
