package com.pedrodalben.bigbangessentials.menu.integration.teleportation.action;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class TeleportToPlayerWarpMenuAction implements MenuActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportToPlayerWarpMenuAction.class);

    @Override
    public String type() { return "teleport_pwarp"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player only"));
        }
        if (player.getServer() == null) {
            LOGGER.debug("Cannot execute teleport_pwarp: player.getServer() is null for player {}", player.getUUID());
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

            // Jail escape prevention
            if (com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isPreventJailEscapeEnabled()
                    && com.pedrodalben.bigbangessentials.moderation.JailManager.getInstance().isPlayerJailed(player.getUUID())) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.jail.prevent_escape"));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("Player is jailed"));
            }

            // Basic pwarp permission
            if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasAnyPermission(player.getUUID(),
                    new String[]{"bigbangessentials.teleport.pwarp", "bigbangessentials.pwarp"})) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.general.no_permission"));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("No permission"));
            }

            // Retrieve the location and re-validate existence (Task 4)
            com.pedrodalben.bigbangessentials.teleportation.TeleportLocation location =
                WarpManager.getInstance().getPlayerWarp(ownerUuid, resolvedWarp);

            if (location == null) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", resolvedWarp));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("Player warp not found"));
            }

            if (context.clickType() == com.pedrodalben.bigbangessentials.menu.model.MenuClickType.RIGHT && 
                player.getUUID().equals(ownerUuid)) {
                // Open pwarp delete confirmation
                java.util.Map<String, Object> values = new java.util.HashMap<>();
                values.put("pwarp_name", resolvedWarp);
                values.put("pwarp_owner_uuid", ownerUuid.toString());
                java.util.Map<String, String> overrides = new java.util.HashMap<>();
                overrides.put("pwarp_name", resolvedWarp);
                overrides.put("pwarp_owner_uuid", ownerUuid.toString());
                com.pedrodalben.bigbangessentials.menu.session.MenuContext menuCtx = 
                    new com.pedrodalben.bigbangessentials.menu.session.MenuContext(
                        player.getUUID(), "pt_BR", values, overrides, null, null, null
                    );
                
                runner.accept(() -> {
                    MenuSystem.getInstance().getMenuService().openMenu(player, "confirm_delete_pwarp", menuCtx);
                });
            } else {
                // Left click: Teleport
                final UUID finalOwnerUuid = ownerUuid;
                final String finalWarpName = resolvedWarp;
                runner.accept(() -> {
                    WarpManager.getInstance().teleportToPlayerWarp(player, finalOwnerUuid, finalWarpName, true);
                });
            }
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.failure("Missing pwarp-name"));
    }
}
