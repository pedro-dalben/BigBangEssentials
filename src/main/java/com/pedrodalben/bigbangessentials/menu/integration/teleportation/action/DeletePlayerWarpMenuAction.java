package com.pedrodalben.bigbangessentials.menu.integration.teleportation.action;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.teleportation.TeleportLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class DeletePlayerWarpMenuAction implements MenuActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeletePlayerWarpMenuAction.class);

    @Override
    public String type() { return "delete_pwarp"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player only"));
        }
        if (player.getServer() == null) {
            LOGGER.debug("Cannot execute delete_pwarp: player.getServer() is null for player {}", player.getUUID());
            return CompletableFuture.completedFuture(ActionExecutionResult.failure("Server instance not available"));
        }
        return executeWithRunner(context, player, task -> player.getServer().submit(task));
    }

    public CompletionStage<ActionExecutionResult> executeWithRunner(ActionContext context, ServerPlayer player, Consumer<Runnable> runner) {
        String warpName = context.param("pwarp-name", String.class);
        if (warpName == null) {
            warpName = context.param("pwarp-id", String.class);
        }
        String ownerUuidStr = context.param("pwarp-owner-uuid", String.class);

        if (warpName != null) {
            String resolvedWarp = PlaceholderService.resolve(warpName, player, context.context());
            UUID ownerUuid = player.getUUID();
            if (ownerUuidStr != null) {
                String resolvedOwner = PlaceholderService.resolve(ownerUuidStr, player, context.context());
                try {
                    ownerUuid = UUID.fromString(resolvedOwner);
                } catch (Exception ignored) {}
            }

            // Re-validate permission: player must be owner or admin (Task 4)
            boolean isOwner = player.getUUID().equals(ownerUuid);
            boolean isAdmin = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.pwarp.admin");
            
            if (!isOwner && !isAdmin) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.general.no_permission"));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("Not the owner or admin"));
            }

            // Re-validate pwarp existence (Task 4)
            java.util.Map<String, TeleportLocation> userWarps = WarpManager.getInstance().getAllPlayerWarps().get(ownerUuid);
            boolean exists = false;
            if (userWarps != null) {
                exists = userWarps.containsKey(WarpManager.getInstance().isCaseSensitiveNames() ? resolvedWarp : resolvedWarp.toLowerCase());
            }
            if (!exists) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", resolvedWarp));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("Player warp not found"));
            }

            final UUID finalOwnerUuid = ownerUuid;
            runner.accept(() -> {
                boolean success = WarpManager.getInstance().deletePlayerWarp(finalOwnerUuid, resolvedWarp);
                if (success) {
                    player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.success("commands.bigbangessentials.teleport.warp.playerwarp_deleted", resolvedWarp));
                    LOGGER.info("Player {} deleted player warp '{}' owned by UUID {}", player.getName().getString(), resolvedWarp, finalOwnerUuid);
                } else {
                    player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", resolvedWarp));
                }
            });
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.failure("Missing pwarp-name"));
    }
}
