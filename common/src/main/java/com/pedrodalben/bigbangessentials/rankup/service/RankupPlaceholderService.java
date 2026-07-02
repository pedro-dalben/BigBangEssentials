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
        RankupConfig config = manager.getConfig();
        Map<String, String> map = new ConcurrentHashMap<>();
        if (config == null) {
            map.put("current_id", "");
            map.put("current_name", "");
            map.put("next_id", "");
            map.put("next_name", "");
            map.put("progress_percent", "0");
            map.put("money_required", "0");
            map.put("gems_required", "0");
            map.put("money_balance", "0");
            map.put("gems_balance", "0");
            map.put("money_status", "");
            map.put("gems_status", "");
            map.put("tasks_completed", "0");
            map.put("tasks_total", "0");
            return map;
        }

        RankupPlayerData data = manager.getOrCreatePlayerData(uuid);
        RankupRank current = data.getCurrentRank(config);
        RankupRank next = config.getNextEnabledRank(current);

        map.put("current_id", current != null ? current.id() : "");
        map.put("current_name", current != null ? stripColor(current.displayName()) : "None");
        map.put("next_id", next != null ? next.id() : "");
        map.put("next_name", next != null ? stripColor(next.displayName()) : "Max Rank");

        double moneyRequired = next != null ? next.requirements().money() : 0;
        int gemsRequired = next != null ? next.requirements().gems() : 0;
        double moneyBalance = com.pedrodalben.bigbangessentials.api.EconomyAPI.getBalance(uuid).doubleValue();
        long gemsBalanceLong = com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager.getInstance().getBalanceView(uuid).availableBalance();

        map.put("money_required", String.valueOf(moneyRequired));
        map.put("gems_required", String.valueOf(gemsRequired));
        map.put("money_balance", String.valueOf(moneyBalance));
        map.put("gems_balance", String.valueOf(gemsBalanceLong));
        map.put("money_status", (moneyBalance >= moneyRequired && moneyRequired > 0) ? "\u00a7a\u2714" : (moneyRequired > 0 ? "\u00a7c\u2718" : ""));
        map.put("gems_status", (gemsBalanceLong >= gemsRequired && gemsRequired > 0) ? "\u00a7a\u2714" : (gemsRequired > 0 ? "\u00a7c\u2718" : ""));

        int completed = next != null ? data.countCompletedTasks(next) : 0;
        int total = next != null ? (int) next.requirements().tasks().stream().filter(t -> t.enabled()).count() : 0;
        map.put("tasks_completed", String.valueOf(completed));
        map.put("tasks_total", String.valueOf(total));

        int percent = 0;
        if (next != null && total > 0) {
            percent = (completed * 100) / total;
        } else if (next != null && total == 0) {
            percent = 100;
        }
        map.put("progress_percent", String.valueOf(percent));

        return map;
    }

    private String stripColor(String input) {
        return net.minecraft.util.StringUtil.stripColor(input != null ? input : "");
    }
}
