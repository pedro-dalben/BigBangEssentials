package com.pedrodalben.bigbangessentials.integrations.fakeplayer;

import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FakeTpaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(FakeTpaManager.class);
    private static FakeTpaManager instance;

    private ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> pendingFakeTpas = new ConcurrentHashMap<>();

    private FakeTpaManager() {
        this.scheduler = createScheduler();
    }

    private static ScheduledExecutorService createScheduler() {
        return Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "FakeTpa-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public static FakeTpaManager getInstance() {
        if (instance == null) {
            instance = new FakeTpaManager();
        }
        return instance;
    }

    static void resetInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public void scheduleFakeTpa(ServerPlayer requester, String targetName, int minSeconds, int maxSeconds) {
        String key = cacheKey(requester, targetName);

        if (pendingFakeTpas.containsKey(key)) {
            LOGGER.debug("Duplicate fake TPA for {} -> {}, blocked", requester.getName().getString(), targetName);
            return;
        }

        long delay = minSeconds + (long)(Math.random() * (maxSeconds - minSeconds));
        LOGGER.debug("Fake TPA scheduled: {} -> {}, {}s expiry",
            requester.getName().getString(), targetName, delay);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingFakeTpas.remove(key);
            if (!requester.hasDisconnected()) {
                requester.sendSystemMessage(MessageUtil.error(
                    "commands.bigbangessentials.fakeplayer.tpa.expired", targetName));
                LOGGER.debug("Fake TPA expired: {} -> {}", requester.getName().getString(), targetName);
            }
        }, delay, TimeUnit.SECONDS);

        pendingFakeTpas.put(key, future);

        requester.sendSystemMessage(MessageUtil.success(
            "commands.bigbangessentials.fakeplayer.tpa.sent", targetName));
        LOGGER.debug("TPA sent to fake player: requester={}, target={}, expiry={}s",
            requester.getName().getString(), targetName, delay);
    }

    public void cancelAllForPlayer(UUID playerUuid, String playerName) {
        String prefix = playerUuid.toString() + ":";
        pendingFakeTpas.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().cancel(false);
                LOGGER.debug("Fake TPA cancelled for disconnected player: {}", playerName);
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        pendingFakeTpas.values().forEach(f -> f.cancel(false));
        pendingFakeTpas.clear();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String cacheKey(ServerPlayer requester, String targetName) {
        return requester.getUUID().toString() + ":" + targetName.toLowerCase();
    }
}
