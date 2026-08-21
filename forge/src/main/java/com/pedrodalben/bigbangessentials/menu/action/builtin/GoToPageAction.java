package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GoToPageAction implements MenuActionHandler {
    @Override
    public String type() { return "go_to_page"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        String page = context.param("page", String.class);
        if (page == null) {
            // Check alternative param key "page_id"
            page = context.param("page_id", String.class);
        }
        
        if (page != null) {
            page = PlaceholderService.resolve(page, context.player(), context.context());
            MenuSystem.getInstance().getMenuService().goToPage(context.player(), context.session().getMenuId(), page);
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.failed("Missing 'page' parameter"));
    }
}
