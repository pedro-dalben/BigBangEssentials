package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RemoveContextValueAction implements MenuActionHandler {
    @Override
    public String type() { return "remove_context_value"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        String key = context.param("key", String.class);
        if (key != null) {
            key = PlaceholderService.resolve(key, context.player(), context.context());
            MenuSession session = context.session();
            if (session != null && session.getSessionData() != null) {
                session.getSessionData().remove(key);
            }
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.failed("Missing 'key' parameter"));
    }
}
