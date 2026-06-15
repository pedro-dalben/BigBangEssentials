package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OpenMenuAction implements MenuActionHandler {
    @Override
    public String type() { return "open_menu"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        String targetMenuId = context.param("menu-id", String.class);
        if (targetMenuId == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Missing menu-id"));
        }
        
        return MenuSystem.getInstance().getMenuService()
            .openMenu(context.player(), targetMenuId, context.context())
            .thenApply(result -> {
                if (result.success()) return ActionExecutionResult.success();
                else return ActionExecutionResult.failed(result.error());
            });
    }
}
