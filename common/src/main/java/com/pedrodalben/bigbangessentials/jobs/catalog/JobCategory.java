package com.pedrodalben.bigbangessentials.jobs.catalog;

public enum JobCategory {
    COMMON("COMMON"),
    POKEMON_SPECIALIZATION("POKEMON_SPECIALIZATION");

    private final String configKey;

    JobCategory(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static JobCategory fromString(String str) {
        if (str == null) return COMMON;
        return switch (str.toUpperCase()) {
            case "POKEMON_SPECIALIZATION" -> POKEMON_SPECIALIZATION;
            default -> COMMON;
        };
    }
}
