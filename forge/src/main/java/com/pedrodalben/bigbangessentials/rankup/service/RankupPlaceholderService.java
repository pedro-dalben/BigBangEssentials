package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.RankupPlayerData;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankupPlaceholderService {
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();

    public void refresh(UUID uuid) {
        cache.remove(uuid);
    }

    public Map<String, String> resolve(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::compute);
    }

    public String get(UUID uuid, String key) {
        return resolve(uuid).getOrDefault(key, "");
    }

    private Map<String, String> compute(UUID uuid) {
        RankupManager manager = RankupManager.getInstance();
        Map<String, String> map = new ConcurrentHashMap<>();
        
        com.pedrodalben.bigbangessentials.rankup.domain.RankupEligibilitySnapshot snapshot = manager.getEligibilitySnapshot(uuid);
        RankupRank current = snapshot.currentRank();
        RankupRank next = snapshot.nextRank();

        map.put("current_id", current != null ? current.id() : "");
        map.put("current_name", current != null ? stripColor(current.displayName()) : "None");
        map.put("next_id", next != null ? next.id() : "");
        map.put("next_name", next != null ? stripColor(next.displayName()) : "Max Rank");

        map.put("money_required", String.valueOf(snapshot.moneyRequired()));
        map.put("gems_required", String.valueOf(snapshot.gemsRequired()));
        map.put("money_balance", String.valueOf(snapshot.moneyBalance()));
        map.put("gems_balance", String.valueOf(snapshot.gemsBalance()));
        
        map.put("money_status", (snapshot.moneySufficient() && snapshot.moneyRequired().compareTo(java.math.BigDecimal.ZERO) > 0) ? "\u00a7a\u2714" : (snapshot.moneyRequired().compareTo(java.math.BigDecimal.ZERO) > 0 ? "\u00a7c\u2718" : ""));
        map.put("gems_status", (snapshot.gemsSufficient() && snapshot.gemsRequired() > 0) ? "\u00a7a\u2714" : (snapshot.gemsRequired() > 0 ? "\u00a7c\u2718" : ""));

        map.put("tasks_completed", String.valueOf(snapshot.completedTasksCount()));
        map.put("tasks_total", String.valueOf(snapshot.totalTasksCount()));

        map.put("progress_percent", String.valueOf((int) Math.round(snapshot.progressPercentage())));

        return map;
    }

    private String stripColor(String input) {
        return net.minecraft.util.StringUtil.stripColor(input != null ? input : "");
    }
}
