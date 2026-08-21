package com.pedrodalben.bigbangessentials.api.rankup;

public class RankupAPI {
    private static RankProgressionApi provider;

    public static void setProvider(RankProgressionApi api) {
        provider = api;
    }

    public static RankProgressionApi get() {
        if (provider == null) {
            throw new IllegalStateException("RankProgressionApi is not initialized yet.");
        }
        return provider;
    }
}
