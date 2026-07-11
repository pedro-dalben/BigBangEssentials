package com.pedrodalben.bigbangessentials.menu.integration.rankup.provider;

import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTask;
import com.pedrodalben.bigbangessentials.rankup.menu.RankupMenuSupport;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RankupTaskDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "rankup.tasks";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        String rankId = context.values() != null ? (String) context.values().get("rank_id") : null;
        if (rankId == null || rankId.isBlank()) {
            return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
        }

        RankupManager mgr = RankupManager.getInstance();
        RankupConfig config = mgr.getConfig();
        if (config == null) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));

        RankupRank rank = config.getRank(rankId);
        if (rank == null || rank.requirements() == null || rank.requirements().tasks() == null) {
            return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
        }

        List<RankupTask> tasks = rank.requirements().tasks();
        int totalItems = tasks.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                RankupTask task = tasks.get(i);
                items.add(RankupMenuSupport.buildTaskPlaceholders(player, rank, task));
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }
}
