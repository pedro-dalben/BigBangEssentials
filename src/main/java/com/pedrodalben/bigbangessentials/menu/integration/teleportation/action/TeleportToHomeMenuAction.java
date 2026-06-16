package com.pedrodalben.bigbangessentials.menu.integration.teleportation.action;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.teleportation.HomeManager;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class TeleportToHomeMenuAction implements MenuActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportToHomeMenuAction.class);

    @Override
    public String type() { return "teleport_home"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player only"));
        }
        if (player.getServer() == null) {
            LOGGER.debug("Cannot execute teleport_home: player.getServer() is null for player {}", player.getUUID());
            return CompletableFuture.completedFuture(ActionExecutionResult.failure("Server instance not available"));
        }
        return executeWithRunner(context, player, task -> player.getServer().submit(task));
    }

    public CompletionStage<ActionExecutionResult> executeWithRunner(ActionContext context, ServerPlayer player, Consumer<Runnable> runner) {
        String homeName = context.param("home-name", String.class);
        if (homeName == null) {
            homeName = context.param("home-id", String.class);
        }

        if (homeName != null) {
            String resolved = PlaceholderService.resolve(homeName, player, context.context());

            // Jail escape prevention
            if (com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isPreventJailEscapeEnabled()
                    && com.pedrodalben.bigbangessentials.moderation.JailManager.getInstance().isPlayerJailed(player.getUUID())) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.jail.prevent_escape"));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("Player is jailed"));
            }

            // Basic home permission
            if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasAnyPermission(player.getUUID(),
                    new String[]{"bigbangessentials.teleport.home", "bigbangessentials.home", "bigbangessentials.teleportation.home"})) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.general.no_permission"));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("No permission"));
            }

            // Re-validate home existence at click/teleport time (Task 4)
            if (!HomeManager.getInstance().getPlayerHomes(player).containsKey(resolved)) {
                player.sendSystemMessage(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.teleport.home.not_found", resolved));
                return CompletableFuture.completedFuture(ActionExecutionResult.failure("Home not found"));
            }

            if (context.clickType() == com.pedrodalben.bigbangessentials.menu.model.MenuClickType.RIGHT) {
                // Right click: open confirm delete menu
                java.util.Map<String, String> overrides = new java.util.HashMap<>();
                overrides.put("home_name", resolved);
                com.pedrodalben.bigbangessentials.menu.session.MenuContext menuCtx = 
                    new com.pedrodalben.bigbangessentials.menu.session.MenuContext(
                        player.getUUID(), "pt_BR", null, overrides, null, null, null
                    );
                
                runner.accept(() -> {
                    MenuSystem.getInstance().getMenuService().openMenu(player, "confirm_delete_home", menuCtx);
                });
            } else {
                // Left click: Teleport
                runner.accept(() -> {
                    HomeManager.getInstance().teleportToHome(player, resolved);
                });
            }
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.failure("Missing home-name"));
    }
}
