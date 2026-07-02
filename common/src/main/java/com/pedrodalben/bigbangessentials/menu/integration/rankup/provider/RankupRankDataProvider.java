package com.pedrodalben.bigbangessentials.menu.integration.rankup.provider;

import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.menu.RankupMenuSupport;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RankupRankDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "rankup.ranks";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        RankupManager mgr = RankupManager.getInstance();
        RankupConfig config = mgr.getConfig();
        List<RankupRank> ranks = config != null ? config.getOrderedRanks() : List.of();
        RankupRank current = mgr.getCurrentRank(player.getUUID());
        RankupRank next = mgr.getNextRank(player.getUUID());

        int totalItems = ranks.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                RankupRank rank = ranks.get(i);
                items.add(RankupMenuSupport.buildRankPlaceholders(player, rank, current, next));
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }
}
