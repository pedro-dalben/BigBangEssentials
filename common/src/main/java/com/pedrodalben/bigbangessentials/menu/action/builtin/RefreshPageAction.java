package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RefreshPageAction implements MenuActionHandler {
    @Override
    public String type() { return "refresh_page"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        if (context.player() != null) {
            MenuSystem.getInstance().getMenuService().refreshCurrentPage(context.player());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
