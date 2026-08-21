package com.pedrodalben.bigbangessentials.menu.integration.teleportation.action;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class TeleportToWarpMenuAction implements MenuActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportToWarpMenuAction.class);

    @Override
    public String type() { return "teleport_warp"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player only"));
        }
        if (player.getServer() == null) {
            LOGGER.debug("Cannot execute teleport_warp: player.getServer() is null for player {}", player.getUUID());
            return CompletableFuture.completedFuture(ActionExecutionResult.failure("Server instance not available"));
        }
        return executeWithRunner(context, player, task -> player.getServer().submit(task));
    }

    public CompletionStage<ActionExecutionResult> executeWithRunner(ActionContext context, ServerPlayer player, Consumer<Runnable> runner) {
        String warpId = context.param("warp-name", String.class);
        if (warpId == null) {
            warpId = context.param("warp-id", String.class);
        }
        if (warpId != null) {
            String resolved = PlaceholderService.resolve(warpId, player, context.context());

            // Jail escape prevention
            if (com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isPreventJailEscapeEnabled()
                    && com.pedrodalben.bigbangessentials.moderation.JailManager.getInstance().isPlayerJailed(player.getUUID())) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.jail.prevent_escape"));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("Player is jailed"));
            }

            // Basic warp permission
            if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasAnyPermission(player.getUUID(),
                    new String[]{"bigbangessentials.teleport.warp", "bigbangessentials.warp"})
                    && !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.warps.*")) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.general.no_permission"));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("No permission"));
            }

            // Per-warp permission check
            if (com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isPerWarpPermissionEnabled()
                    && !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.warps." + resolved)
                    && !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.warps.*")) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.teleport.warp.no_per_warp_permission", resolved));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("No per-warp permission"));
            }

            // Check if warp exists
            if (!WarpManager.getInstance().hasWarp(resolved)) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", resolved));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("Warp not found"));
            }

            runner.accept(() -> {
                WarpManager.getInstance().teleportToWarp(player, resolved);
            });
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.failure("Missing warp-id"));
    }
}
