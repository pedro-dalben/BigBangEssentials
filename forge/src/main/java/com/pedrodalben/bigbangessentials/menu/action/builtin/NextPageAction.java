package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class NextPageAction implements MenuActionHandler {
    @Override
    public String type() { return "next_page"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        MenuSystem.getInstance().getMenuService().nextPage(context.player(), context.menu().id());
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
