package com.pedrodalben.bigbangessentials.economy.gems.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GemConfigValidator {
    public static class ValidationResult {
        public boolean valid = true;
        public List<String> errors = new ArrayList<>();
        public boolean isValid() { return valid; }
        
        public void addError(String msg) {
            this.errors.add(msg);
            this.valid = false;
        }
    }
    
    public static ValidationResult validate(GemConfig config) {
        ValidationResult result = new ValidationResult();
        
        if (config.technicalId == null || !Pattern.matches("^[a-z0-9_]+$", config.technicalId)) {
            result.addError("technicalId must be lowercase alphanumeric and underscores only");
        }
        if (config.balances.startingBalance < 0) {
            result.addError("startingBalance cannot be negative");
        }
        if (config.balances.maxBalance < 0) {
            result.addError("maxBalance cannot be negative");
        }
        if (config.balances.startingBalance > config.balances.maxBalance) {
            result.addError("startingBalance cannot be greater than maxBalance");
        }
        if (config.balances.allowNegativeBalances) {
            result.addError("allowNegativeBalances must be false for gems");
        }
        if (config.commands.root == null || config.commands.root.trim().isEmpty()) {
            result.addError("Command root cannot be empty");
        }
        if (config.reservations.defaultLeaseSeconds <= 0) {
            result.addError("defaultLeaseSeconds must be greater than 0");
        }
        if (config.reservations.maxLeaseSeconds < config.reservations.defaultLeaseSeconds) {
            result.addError("maxLeaseSeconds must be >= defaultLeaseSeconds");
        }
        
        return result;
    }
}
