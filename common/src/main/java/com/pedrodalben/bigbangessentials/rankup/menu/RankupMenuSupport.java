package com.pedrodalben.bigbangessentials.rankup.menu;

import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.RankupPlayerData;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;

import java.util.*;

public class RankupMenuSupport {

    public static Map<String, Object> buildRankPlaceholders(ServerPlayer player, RankupRank rank, RankupRank current, RankupRank next) {
        Map<String, Object> map = new HashMap<>();
        map.put("rank_id", rank.id());
        map.put("rank_display_name", strip(rank.displayName()));
        map.put("rank_description", String.join("\n", rank.description().stream().map(RankupMenuSupport::strip).toList()));
        map.put("rank_order", String.valueOf(rank.order()));
        map.put("rank_icon", rank.icon().item());
        map.put("rank_luckperms_group", rank.luckPerms().group());
        map.put("rank_money", String.valueOf(rank.requirements().money()));
        map.put("rank_gems", String.valueOf(rank.requirements().gems()));
        map.put("rank_task_count", String.valueOf(rank.requirements().tasks().size()));
        map.put("rank_enabled", String.valueOf(rank.enabled()));

        String statusColor;
        String status;
        if (current != null && current.id().equals(rank.id())) {
            statusColor = "§6";
            status = "Current";
        } else if (next != null && next.id().equals(rank.id())) {
            statusColor = "§e";
            status = "Next";
        } else if (current != null && rank.order() < current.order()) {
            statusColor = "§a";
            status = "Completed";
        } else {
            statusColor = "§7";
            status = "Locked";
        }
        map.put("rank_status_color", statusColor);
        map.put("rank_status", status);
        return map;
    }

    public static Map<String, Object> buildTaskPlaceholders(ServerPlayer player, RankupRank rank, RankupTask task) {
        Map<String, Object> map = new HashMap<>();
        RankupPlayerData data = RankupManager.getInstance().getOrCreatePlayerData(player.getUUID());
        int progress = data.getTaskProgressValue(rank.id(), task.id());
        int target = task.target();
        boolean completed = progress >= target;
        map.put("task_id", task.id());
        map.put("task_display_name", strip(task.displayName()));
        map.put("task_description", String.join("\n", task.description().stream().map(RankupMenuSupport::strip).toList()));
        map.put("task_type", task.type().name());
        map.put("task_target", String.valueOf(target));
        map.put("task_progress", String.valueOf(progress));
        map.put("task_completed", String.valueOf(completed));
        map.put("task_enabled", String.valueOf(task.enabled()));
        map.put("task_symbol", completed ? "§a✔" : "§c✘");
        return map;
    }

    public static Map<String, Object> buildSummaryPlaceholders(ServerPlayer player) {
        Map<String, Object> map = new HashMap<>();
        RankupManager mgr = RankupManager.getInstance();
        RankupRank current = mgr.getCurrentRank(player.getUUID());
        RankupRank next = mgr.getNextRank(player.getUUID());
        map.put("current_id", current != null ? current.id() : "");
        map.put("current_name", current != null ? strip(current.displayName()) : "None");
        map.put("next_id", next != null ? next.id() : "");
        map.put("next_name", next != null ? strip(next.displayName()) : "Max Rank");

        double moneyRequired = next != null ? next.requirements().money() : 0;
        int gemsRequired = next != null ? next.requirements().gems() : 0;
        double moneyBalance = com.pedrodalben.bigbangessentials.api.EconomyAPI.getBalance(player.getUUID()).doubleValue();
        long gemsBalanceLong = com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager.getInstance().getBalanceView(player.getUUID()).availableBalance();

        map.put("money_required", String.valueOf(moneyRequired));
        map.put("gems_required", String.valueOf(gemsRequired));
        map.put("money_balance", String.valueOf(moneyBalance));
        map.put("gems_balance", String.valueOf(gemsBalanceLong));
        map.put("money_status", (moneyBalance >= moneyRequired && moneyRequired > 0) ? "\u00a7a\u2714" : (moneyRequired > 0 ? "\u00a7c\u2718" : ""));
        map.put("gems_status", (gemsBalanceLong >= gemsRequired && gemsRequired > 0) ? "\u00a7a\u2714" : (gemsRequired > 0 ? "\u00a7c\u2718" : ""));

        RankupPlayerData data = mgr.getOrCreatePlayerData(player.getUUID());
        int completed = next != null ? data.countCompletedTasks(next) : 0;
        int total = next != null ? (int) next.requirements().tasks().stream().filter(RankupTask::enabled).count() : 0;
        map.put("tasks_completed", String.valueOf(completed));
        map.put("tasks_total", String.valueOf(total));
        int percent = (total > 0) ? (completed * 100) / total : (next != null ? 100 : 0);
        map.put("progress_percent", String.valueOf(percent));
        return map;
    }

    private static String strip(String input) {
        return StringUtil.stripColor(input != null ? input : "");
    }
}
