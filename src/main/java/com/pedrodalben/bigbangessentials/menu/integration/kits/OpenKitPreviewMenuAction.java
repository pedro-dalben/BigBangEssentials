package com.pedrodalben.bigbangessentials.menu.integration.kits;

import com.pedrodalben.bigbangessentials.kits.Kit;
import com.pedrodalben.bigbangessentials.kits.KitManager;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OpenKitPreviewMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "open_kit_preview";
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

        String kitName = PlaceholderService.resolve(rawKitName, player, context.context());
        if (kitName == null || kitName.isBlank()) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.not_found", ""));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Kit name missing"));
        }

        Kit kit = KitManager.getInstance().getKit(kitName);
        if (kit == null) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.kits.not_found", kitName));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Kit not found"));
        }

        String previewMenuId = context.param("menu-id", String.class);
        if (previewMenuId == null || previewMenuId.isBlank()) {
            previewMenuId = KitMenuConfig.getPreviewMenuId();
        }
        if (previewMenuId == null || previewMenuId.isBlank()) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Missing preview menu id"));
        }

        Map<String, Object> values = new HashMap<>();
        Map<String, String> overrides = new HashMap<>();
        if (context.context() != null) {
            if (context.context().values() != null) {
                values.putAll(context.context().values());
            }
            if (context.context().placeholderOverrides() != null) {
                overrides.putAll(context.context().placeholderOverrides());
            }
        }

        Map<String, Object> kitPlaceholders = KitMenuSupport.buildKitPlaceholders(player, kit);
        values.putAll(kitPlaceholders);
        for (Map.Entry<String, Object> entry : kitPlaceholders.entrySet()) {
            overrides.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }

        MenuContext previewContext = new MenuContext(
            player.getUUID(),
            context.context() != null && context.context().locale() != null ? context.context().locale() : "pt_BR",
            values,
            overrides,
            context.context() != null ? context.context().sourceModule() : "kits",
            context.context() != null ? context.context().sourceCommand() : null,
            UUID.randomUUID()
        );

        return MenuSystem.getInstance().getMenuService()
            .openMenu(player, previewMenuId, previewContext)
            .thenApply(result -> {
                if (result.success()) {
                    return ActionExecutionResult.success();
                }
                player.sendSystemMessage(MessageUtil.coloredText("§cNão foi possível abrir o preview do kit."));
                return ActionExecutionResult.failed(result.error() != null ? result.error() : "Preview menu unavailable");
            });
    }
}
