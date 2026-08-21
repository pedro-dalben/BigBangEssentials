package com.pedrodalben.bigbangessentials.integrations;

import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.UUID;

/** Optional BigBangRegions hooks for player warp policy and containment. */
public final class BigBangRegionsPwarpBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(BigBangRegionsPwarpBridge.class);
    private static final String REGIONS_CLASS = "com.bigbangcraft.regions.BigBangRegions";
    private static final Class<?>[] LOCATION_TYPES = {UUID.class, String.class, int.class, int.class, int.class};

    private boolean failureLogged;

    private BigBangRegionsPwarpBridge() {}

    public static BigBangRegionsPwarpBridge create() {
        return Platform.isModLoaded("bigbangregions") ? new BigBangRegionsPwarpBridge() : null;
    }

    public boolean canCreate(UUID creatorUuid, String dimension, int x, int y, int z) {
        return invokeBoolean("canCreatePlayerWarp", creatorUuid, dimension, x, y, z);
    }

    public boolean canUse(UUID warpOwnerUuid, String dimension, int x, int y, int z) {
        return invokeBoolean("canUsePlayerWarp", warpOwnerUuid, dimension, x, y, z);
    }

    public void recordArrival(UUID playerUuid, UUID warpOwnerUuid, String dimension, int x, int y, int z) {
        try {
            invoke("recordPlayerWarpArrival",
                new Class<?>[]{UUID.class, UUID.class, String.class, int.class, int.class, int.class},
                playerUuid, warpOwnerUuid, dimension, x, y, z);
        } catch (Throwable t) {
            reportFailure(t);
        }
    }

    private boolean invokeBoolean(String methodName, UUID uuid, String dimension, int x, int y, int z) {
        try {
            return Boolean.TRUE.equals(invoke(methodName, LOCATION_TYPES, uuid, dimension, x, y, z));
        } catch (Throwable t) {
            reportFailure(t);
            return false;
        }
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Class<?> regionsClass = Class.forName(REGIONS_CLASS);
        Object api = regionsClass.getMethod("getApi").invoke(null);
        if (api == null) {
            throw new IllegalStateException("BigBangRegions API is not initialized");
        }
        Method method = api.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(api, args);
    }

    private synchronized void reportFailure(Throwable error) {
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.error("BigBangRegions pwarp hook is incompatible; denying region pwarps", error);
        }
    }
}
