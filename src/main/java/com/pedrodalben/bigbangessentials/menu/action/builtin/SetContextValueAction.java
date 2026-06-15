package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SetContextValueAction implements MenuActionHandler {
    @Override
    public String type() { return "set_context_value"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        String key = context.param("key", String.class);
        Object value = context.param("value", Object.class);

        if (key != null && value != null) {
            key = PlaceholderService.resolve(key, context.player(), context.context());
            
            Object resolvedValue = value;
            if (value instanceof String strVal) {
                resolvedValue = PlaceholderService.resolve(strVal, context.player(), context.context());
            }

            MenuSession session = context.session();
            if (session != null) {
                if (session.getSessionData() == null) {
                    session.setSessionData(new HashMap<>());
                }
                session.getSessionData().put(key, resolvedValue);
                return CompletableFuture.completedFuture(ActionExecutionResult.success());
            }
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("No active menu session"));
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.failed("Missing 'key' or 'value' parameters"));
    }
}
