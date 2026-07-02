package com.pedrodalben.bigbangessentials.menu.integration.rankup.action;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RankupPromoteAction implements MenuActionHandler {
    @Override
    public String type() {
        return "rankup_promote";
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player unavailable"));
        }

        RankupManager mgr = RankupManager.getInstance();
        RankupRank next = mgr.getNextRank(player.getUUID());
        if (next == null) {
            player.sendSystemMessage(Component.literal("§cYou have already reached the highest rank."));
            return CompletableFuture.completedFuture(ActionExecutionResult.denied());
        }

        mgr.getPromotionService().promote(player, next)
                .thenAccept(result -> {
                    if (result.success()) {
                        player.sendSystemMessage(Component.literal("§a§lPromotion complete!"));
                        player.sendSystemMessage(Component.literal("§7" + result.message()));
                        MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                    } else {
                        player.sendSystemMessage(Component.literal("§c" + result.message()));
                    }
                });

        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
