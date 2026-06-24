package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RefreshItemAction implements MenuActionHandler {
    @Override
    public String type() { return "refresh_item"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        String itemId = context.param("item_id", String.class);
        if (itemId == null) {
            itemId = context.param("item", String.class);
        }
        
        if (itemId != null && context.player() != null && context.session() != null) {
            itemId = PlaceholderService.resolve(itemId, context.player(), context.context());
            MenuSystem.getInstance().getMenuService().refreshItem(
                context.player(), 
                context.session().getMenuId(), 
                context.session().getCurrentPageId(), 
                itemId
            );
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        // If no specific item is provided, fallback to refreshing the page
        if (context.player() != null) {
            MenuSystem.getInstance().getMenuService().refreshCurrentPage(context.player());
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.failed("Missing 'item_id' parameter"));
    }
}
