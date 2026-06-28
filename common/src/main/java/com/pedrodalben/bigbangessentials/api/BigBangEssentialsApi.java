package com.pedrodalben.bigbangessentials.api;

import com.pedrodalben.bigbangessentials.economy.gems.api.GemsService;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.economy.gems.service.GemsServiceImpl;
import java.util.Optional;

public final class BigBangEssentialsApi {
    private static final GemsService SERVICE = new GemsServiceImpl();

    public static Optional<GemsService> gems() {
        if (isGemsEnabled()) {
            return Optional.of(SERVICE);
        }
        return Optional.empty();
    }

    public static GemsService requireGems() {
        if (!isGemsEnabled()) {
            throw new IllegalStateException("Gems system is disabled");
        }
        return SERVICE;
    }

    public static boolean isGemsEnabled() {
        return GemsManager.getInstance().isGemsEnabled();
    }

    public static int gemsApiVersion() {
        return 1;
    }
}
