package com.pedrodalben.bigbangessentials.rankup.menu;
import java.math.BigDecimal;

import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;

import java.util.*;

public class RankupMenuSupport {

    public static Map<String, Object> buildRankPlaceholders(ServerPlayer player, RankupRank rank, RankupRank current, RankupRank next) {
        Map<String, Object> map = new HashMap<>();
        if (rank == null) return map;
        map.put("rank_id", rank.id());
        map.put("rank_display_name", strip(rank.displayName()));
        map.put("rank_description", String.join("\n", rank.description().stream().map(RankupMenuSupport::strip).toList()));
        map.put("rank_order", String.valueOf(rank.order()));
        map.put("rank_icon", rank.icon() != null && rank.icon().item() != null ? rank.icon().item() : "");
        map.put("rank_luckperms_group", rank.luckPerms() != null && rank.luckPerms().group() != null ? rank.luckPerms().group() : "");
        map.put("rank_money", String.valueOf(rank.requirements() != null ? rank.requirements().money() : 0.0));
        map.put("rank_gems", String.valueOf(rank.requirements() != null ? rank.requirements().gems() : 0));
        map.put("rank_task_count", String.valueOf(rank.requirements() != null && rank.requirements().tasks() != null ? rank.requirements().tasks().size() : 0));
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
        if (player == null || task == null) return map;
        RankupEligibilitySnapshot snapshot = RankupManager.getInstance().getEligibilitySnapshot(player.getUUID());
        RankupTaskEligibility eligibility = null;
        for (RankupTaskEligibility te : snapshot.taskEligibilities()) {
            if (te.task().id().equalsIgnoreCase(task.id())) {
                eligibility = te;
                break;
            }
        }
        if (eligibility == null) {
            int progress = RankupManager.getInstance().getOrCreatePlayerData(player.getUUID())
                    .getTaskProgressValue(rank != null ? rank.id() : "", task.id());
            eligibility = RankupTaskEligibility.evaluate(task, progress);
        }

        map.put("task_id", task.id());
        map.put("task_display_name", strip(task.displayName()));
        map.put("task_description", String.join("\n", task.description() != null ? task.description().stream().map(RankupMenuSupport::strip).toList() : List.of()));
        map.put("task_type", task.type().name());
        map.put("task_target", String.valueOf(eligibility.target()));
        map.put("task_progress", String.valueOf(eligibility.progress()));
        map.put("task_effective_progress", String.valueOf(eligibility.effectiveProgress()));
        map.put("task_percentage", String.format(Locale.ROOT, "%.1f", eligibility.percentage()));
        map.put("task_completed", String.valueOf(eligibility.completed()));
        map.put("task_enabled", String.valueOf(task.enabled()));
        map.put("task_symbol", eligibility.completed() ? "§a✔" : "§c✘");
        map.put("task_filter_summary", eligibility.filterSummary());
        return map;
    }

    public static Map<String, Object> buildSummaryPlaceholders(ServerPlayer player) {
        Map<String, Object> map = new HashMap<>();
        if (player == null) return map;
        RankupManager mgr = RankupManager.getInstance();
        RankupEligibilitySnapshot snapshot = mgr.getEligibilitySnapshot(player.getUUID());
        RankupRank current = snapshot.currentRank();
        RankupRank next = snapshot.nextRank();

        map.put("current_id", current != null ? current.id() : "");
        map.put("current_name", current != null ? strip(current.displayName()) : "None");
        map.put("next_id", next != null ? next.id() : "");
        map.put("next_name", next != null ? strip(next.displayName()) : "Max Rank");

        java.math.BigDecimal moneyRequired = snapshot.moneyRequired();
        int gemsRequired = snapshot.gemsRequired();
        java.math.BigDecimal moneyBalance = snapshot.moneyBalance();
        long gemsBalanceLong = snapshot.gemsBalance();

        map.put("money_required", String.valueOf(moneyRequired));
        map.put("gems_required", String.valueOf(gemsRequired));
        map.put("money_balance", String.valueOf(moneyBalance));
        map.put("gems_balance", String.valueOf(gemsBalanceLong));
        map.put("money_missing", String.valueOf(snapshot.moneyMissing()));
        map.put("gems_missing", String.valueOf(snapshot.gemsMissing()));
        map.put("money_status", (snapshot.moneySufficient() && moneyRequired.compareTo(BigDecimal.ZERO) > 0) ? "§a✔" : (moneyRequired.compareTo(BigDecimal.ZERO) > 0 ? "§c✘" : ""));
        map.put("gems_status", (snapshot.gemsSufficient() && gemsRequired > 0) ? "§a✔" : (gemsRequired > 0 ? "§c✘" : ""));

        map.put("tasks_completed", String.valueOf(snapshot.completedTasksCount()));
        map.put("tasks_total", String.valueOf(snapshot.totalTasksCount()));
        int percentRounded = (int) Math.round(snapshot.progressPercentage());
        map.put("progress_percent", String.valueOf(percentRounded));
        map.put("progress_percentage_accurate", String.format(Locale.ROOT, "%.1f", snapshot.progressPercentage()));
        map.put("eligibility_state", snapshot.state().name());
        map.put("eligibility_status_text", snapshot.state().defaultStatusText());
        map.put("ready_for_promotion", String.valueOf(snapshot.isReadyForPromotion()));
        return map;
    }

    private static String strip(String input) {
        return StringUtil.stripColor(input != null ? input : "");
    }
}
