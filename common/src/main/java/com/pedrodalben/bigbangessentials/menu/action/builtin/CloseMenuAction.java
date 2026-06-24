package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.model.MenuCloseReason;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CloseMenuAction implements MenuActionHandler {
    @Override
    public String type() { return "close_menu"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        MenuSystem.getInstance().getMenuService().closeMenu(context.player(), context.menu().id(), MenuCloseReason.PLUGIN_CLOSE);
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
