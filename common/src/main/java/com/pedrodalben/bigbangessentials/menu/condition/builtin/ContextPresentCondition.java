package com.pedrodalben.bigbangessentials.menu.condition.builtin;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ContextPresentCondition implements MenuConditionHandler {
    @Override
    public String type() { return "context_present"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        String key = context.param("key", String.class);
        if (key == null) return CompletableFuture.completedFuture(ConditionResult.fail("missing key param"));
        
        String resolvedKey = PlaceholderService.resolve(key, context.player(), context.context());
        
        boolean present = false;
        if (context.context() != null && context.context().values() != null && context.context().values().containsKey(resolvedKey)) {
            present = true;
        } else {
            var sessionOpt = MenuSystem.getInstance().getMenuService().getCurrentSession(context.player().getUUID());
            if (sessionOpt.isPresent() && sessionOpt.get().getSessionData() != null && sessionOpt.get().getSessionData().containsKey(resolvedKey)) {
                present = true;
            }
        }
        
        if (context.spec().negate()) present = !present;
        
        if (present) return CompletableFuture.completedFuture(ConditionResult.pass());
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
