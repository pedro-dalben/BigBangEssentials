package com.pedrodalben.bigbangessentials.menu.integration.rankup.action;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupEligibilitySnapshot;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.menu.RankupMenuSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RankupRankClickAction implements MenuActionHandler {
    @Override
    public String type() {
        return "rankup_rank_click";
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player unavailable"));
        }

        String rawRankId = context.param("rank-id", String.class);
        if (rawRankId == null || rawRankId.isBlank()) {
            rawRankId = context.param("rank", String.class);
        }

        String rankId = PlaceholderService.resolve(rawRankId, player, context.context());
        if (rankId == null || rankId.isBlank()) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Rank ID missing"));
        }

        RankupManager mgr = RankupManager.getInstance();
        RankupConfig cfg = mgr.getConfig();
        if (cfg == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Config not loaded"));

        RankupRank clickedRank = cfg.getRank(rankId);
        if (clickedRank == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Rank not found"));
        }

        RankupEligibilitySnapshot snapshot = mgr.getEligibilitySnapshot(player.getUUID());
        RankupRank current = snapshot.currentRank();
        RankupRank next = snapshot.nextRank();

        if (current != null && clickedRank.order() < current.order()) {
            player.sendSystemMessage(Component.literal("§eYou have already completed this rank."));
            return CompletableFuture.completedFuture(ActionExecutionResult.denied("Already completed"));
        } else if (current != null && clickedRank.id().equals(current.id())) {
            player.sendSystemMessage(Component.literal("§aThis is your current rank."));
            // Maybe we want to allow opening the detail menu to see what tasks we did? 
            // The prompt says: "clicar no rank atual mostra seu estado;"
        } else if (next != null && !clickedRank.id().equals(next.id()) && clickedRank.order() > next.order()) {
            player.sendSystemMessage(Component.literal("§cThis rank is locked."));
            return CompletableFuture.completedFuture(ActionExecutionResult.denied("Rank locked"));
        }

        // Open details menu
        String detailsMenuId = context.param("menu-id", String.class);
        if (detailsMenuId == null || detailsMenuId.isBlank()) {
            detailsMenuId = "rankup_rank_details_menu";
        }

        Map<String, Object> values = new HashMap<>();
        Map<String, String> overrides = new HashMap<>();
        if (context.context() != null) {
            if (context.context().values() != null) values.putAll(context.context().values());
            if (context.context().placeholderOverrides() != null) overrides.putAll(context.context().placeholderOverrides());
        }

        Map<String, Object> rankPlaceholders = RankupMenuSupport.buildRankPlaceholders(player, clickedRank, current, next);
        values.putAll(rankPlaceholders);
        for (Map.Entry<String, Object> entry : rankPlaceholders.entrySet()) {
            overrides.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }

        MenuContext detailsContext = new MenuContext(
            player.getUUID(),
            context.context() != null && context.context().locale() != null ? context.context().locale() : "en_US",
            values,
            overrides,
            "rankup",
            context.context() != null ? context.context().sourceCommand() : null,
            UUID.randomUUID()
        );

        return MenuSystem.getInstance().getMenuService()
            .openMenu(player, detailsMenuId, detailsContext)
            .thenApply(result -> {
                if (result.success()) return ActionExecutionResult.success();
                return ActionExecutionResult.failed(result.error());
            });
    }
}
