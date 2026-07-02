package com.pedrodalben.bigbangessentials.menu.action;

import com.pedrodalben.bigbangessentials.menu.api.MenuActionRegistry;
import com.pedrodalben.bigbangessentials.menu.model.ActionSpec;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.Map;
import java.util.HashMap;

public class ActionExecutor {
    private final MenuActionRegistry registry;

    public ActionExecutor(MenuActionRegistry registry) {
        this.registry = registry;
    }

    public CompletionStage<Void> executeAll(List<ActionSpec> actions, ActionContext context) {
        if (actions == null || actions.isEmpty()) return CompletableFuture.completedFuture(null);
        
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        for (ActionSpec spec : actions) {
            future = future.thenCompose(v -> {
                // Click type filter check (Task 5)
                String clickName = context.clickType() != null ? context.clickType().name() : "";
                if (spec.clicks() != null && !spec.clicks().isEmpty()) {
                    if (!spec.clicks().contains(clickName)) {
                        return CompletableFuture.completedFuture(null);
                    }
                } else {
                    // Block advanced clicks by default unless explicitly allowed
                    if (context.clickType() != com.pedrodalben.bigbangessentials.menu.model.MenuClickType.LEFT &&
                        context.clickType() != com.pedrodalben.bigbangessentials.menu.model.MenuClickType.RIGHT) {
                        return CompletableFuture.completedFuture(null);
                    }
                }

                MenuActionHandler handler = registry.getHandler(spec.type()).orElse(null);
                if (handler == null) return CompletableFuture.completedFuture(null);

                Map<String, Object> resolvedParams = resolveParams(spec.params(), context);
                
                ActionContext newContext = new ActionContext(
                    context.player(), context.session(), context.menu(), context.page(),
                    context.item(), context.clickType(), context.context(), resolvedParams
                );
                
                return handler.execute(newContext).thenCompose(result -> {
                    if (result.status() == com.pedrodalben.bigbangessentials.menu.model.ActionStatus.SUCCESS) {
                        return executeAll(spec.onSuccess(), context);
                    } else if (result.status() == com.pedrodalben.bigbangessentials.menu.model.ActionStatus.DENIED) {
                        return executeAll(spec.onDeny(), context);
                    } else {
                        return executeAll(spec.onFailure(), context);
                    }
                });
            });
        }
        return future;
    }

    private Map<String, Object> resolveParams(Map<String, Object> rawParams, ActionContext context) {
        if (rawParams == null || rawParams.isEmpty()) return Map.of();
        Map<String, Object> resolved = new HashMap<>(rawParams.size());
        for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str && str.contains("{") && str.contains("}")) {
                resolved.put(entry.getKey(), com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService.resolve(str, context.player(), context.context()));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }
}
