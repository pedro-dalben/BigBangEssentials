package com.pedrodalben.bigbangessentials.menu.condition.builtin;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ContextEqualsCondition implements MenuConditionHandler {
    @Override
    public String type() { return "context_equals"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        String key = context.param("key", String.class);
        Object expectedVal = context.param("value", Object.class);
        if (key == null) return CompletableFuture.completedFuture(ConditionResult.fail("missing key param"));
        
        String resolvedKey = PlaceholderService.resolve(key, context.player(), context.context());
        
        Object actualVal = null;
        if (context.context() != null && context.context().values() != null && context.context().values().containsKey(resolvedKey)) {
            actualVal = context.context().values().get(resolvedKey);
        } else {
            var sessionOpt = MenuSystem.getInstance().getMenuService().getCurrentSession(context.player().getUUID());
            if (sessionOpt.isPresent() && sessionOpt.get().getSessionData() != null) {
                actualVal = sessionOpt.get().getSessionData().get(resolvedKey);
            }
        }
        
        boolean equals = false;
        if (actualVal != null) {
            String actualStr = String.valueOf(actualVal);
            String expectedStr = String.valueOf(expectedVal);
            expectedStr = PlaceholderService.resolve(expectedStr, context.player(), context.context());
            equals = actualStr.equals(expectedStr);
        } else if (expectedVal == null) {
            equals = true;
        }
        
        if (context.spec().negate()) equals = !equals;
        
        if (equals) return CompletableFuture.completedFuture(ConditionResult.pass());
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
