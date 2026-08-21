package com.pedrodalben.bigbangessentials.menu.integration.kits;

import com.pedrodalben.bigbangessentials.kits.Kit;
import com.pedrodalben.bigbangessentials.kits.KitManager;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class KitClaimMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "claim_kit";
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player unavailable"));
        }

        String rawKitName = context.param("kit-name", String.class);
        if (rawKitName == null || rawKitName.isBlank()) {
            rawKitName = context.param("kit", String.class);
        }
        if (rawKitName == null || rawKitName.isBlank()) {
            rawKitName = context.param("name", String.class);
        }

        String kitName = com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService.resolve(rawKitName, player, context.context());
        if (kitName == null || kitName.isBlank()) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.not_found", ""));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Kit name missing"));
        }

        Kit kit = KitManager.getInstance().getKit(kitName);
        if (kit == null) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.not_found", kitName));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Kit not found"));
        }

        KitMenuSupport.KitStatus status = KitMenuSupport.classify(player, kit);
        if (!status.claimable()) {
            String reason = status.reason() != null ? status.reason().toLowerCase(java.util.Locale.ROOT) : "";
            if ("cooldown".equals(status.key())) {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.cooldown", status.remainingDisplay()));
            } else if ("disabled".equals(status.key())) {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.cannot_use", "Kit desativado"));
            } else if ("used".equals(status.key())) {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.cannot_use", "Limite de usos atingido"));
            } else if (reason.contains("permission")) {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.no_permission_kit", kit.getDisplayName()));
            } else if (reason.contains("maximum number of kits on cooldown")) {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.cannot_use", "Limite de kits em espera atingido"));
            } else {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.cannot_use", status.reason().isBlank() ? status.remainingDisplay() : status.reason()));
            }
            return CompletableFuture.completedFuture(ActionExecutionResult.denied());
        }

        KitManager.KitUsageResult giveResult = KitManager.getInstance().giveKit(player, kitName);
        if (!giveResult.isAllowed()) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.give_failed", giveResult.getMessage()));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(giveResult.getMessage()));
        }

        player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.kits.given", kit.getDisplayName()));
        KitMenuIntegration.refreshOpenMenus();
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
