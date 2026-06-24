package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RefreshMenuAction implements MenuActionHandler {
    @Override
    public String type() { return "refresh_menu"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        if (context.player() != null && context.session() != null) {
            MenuSystem.getInstance().getMenuService().refreshMenu(context.player(), context.session().getMenuId());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
