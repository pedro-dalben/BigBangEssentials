package com.pedrodalben.bigbangessentials.jobs.feedback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EarningsAggregator {
    private static final EarningsAggregator INSTANCE = new EarningsAggregator();
    private static final long DEFAULT_WINDOW_MS = 1500;

    private final Map<UUID, PlayerAggregation> pending = new ConcurrentHashMap<>();
    private EarningsFeedbackMode mode = EarningsFeedbackMode.ACTION_BAR;
    private long windowMs = DEFAULT_WINDOW_MS;
    private boolean showXp = true;
    private boolean showMoney = true;

    private EarningsAggregator() {}

    public static EarningsAggregator getInstance() { return INSTANCE; }

    public void accumulate(ServerPlayer player, String jobId, double xp, double money) {
        PlayerAggregation agg = pending.computeIfAbsent(player.getUUID(), k -> new PlayerAggregation());
        agg.add(jobId, xp, money);
        if (!agg.scheduled) {
            agg.scheduled = true;
            scheduleFlush(player);
        }
    }

    public void flush(ServerPlayer player) {
        PlayerAggregation agg = pending.remove(player.getUUID());
        if (agg == null || agg.isEmpty()) return;

        String msg = buildMessage(agg);
        if (msg.isEmpty()) return;

        send(player, msg);
    }

    private void scheduleFlush(ServerPlayer player) {
        java.util.concurrent.CompletableFuture.delayedExecutor(windowMs, TimeUnit.MILLISECONDS)
            .execute(() -> {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> flush(player));
                }
            });
    }

    private String buildMessage(PlayerAggregation agg) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, JobEarnings> entry : agg.earnings.entrySet()) {
            String jobName = entry.getKey();
            JobEarnings earn = entry.getValue();
            if (sb.length() > 0) sb.append(" §7•");
            sb.append(" §6").append(jobName);
            if (showXp && earn.xp > 0) sb.append(" §a+").append(formatValue(earn.xp)).append(" XP");
            if (showMoney && earn.money > 0) sb.append(" §e+$").append(formatValue(earn.money));
            if (earn.levelUp) sb.append(" §b§lNÍVEL UP!");
        }
        return sb.toString();
    }

    private String formatValue(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private void send(ServerPlayer player, String msg) {
        switch (mode) {
            case ACTION_BAR:
                player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(msg)));
                break;
            case CHAT:
                player.sendSystemMessage(Component.literal(msg));
                break;
            case BOSS_BAR:
                try {
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("")));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(msg)));
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal(msg));
                }
                break;
            case NONE:
                break;
        }
    }

    public void configure(EarningsFeedbackMode mode, long windowMs, boolean showXp, boolean showMoney) {
        this.mode = mode;
        this.windowMs = windowMs > 0 ? windowMs : DEFAULT_WINDOW_MS;
        this.showXp = showXp;
        this.showMoney = showMoney;
    }

    public void flushAll(ServerPlayer player) {
        flush(player);
    }

    private static class PlayerAggregation {
        final Map<String, JobEarnings> earnings = new ConcurrentHashMap<>();
        boolean scheduled = false;

        void add(String jobId, double xp, double money) {
            earnings.computeIfAbsent(jobId, k -> new JobEarnings()).add(xp, money);
        }

        boolean isEmpty() {
            return earnings.isEmpty();
        }
    }

    private static class JobEarnings {
        double xp;
        double money;
        boolean levelUp;

        void add(double xp, double money) {
            this.xp += xp;
            this.money += money;
        }

        void markLevelUp() { this.levelUp = true; }
    }
}
