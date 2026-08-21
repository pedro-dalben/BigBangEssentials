package com.pedrodalben.bigbangessentials.jobs.crates;

public record CrateKeyGrantResult(boolean success, int grantedAmount, String keyId, String errorMessage) {
    public static CrateKeyGrantResult success(int amount, String keyId) {
        return new CrateKeyGrantResult(true, amount, keyId, null);
    }
    public static CrateKeyGrantResult failure(String errorMessage) {
        return new CrateKeyGrantResult(false, 0, null, errorMessage);
    }
}
