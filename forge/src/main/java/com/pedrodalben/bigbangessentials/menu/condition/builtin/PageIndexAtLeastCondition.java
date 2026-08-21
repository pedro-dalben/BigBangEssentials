package com.pedrodalben.bigbangessentials.menu.condition.builtin;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PageIndexAtLeastCondition implements MenuConditionHandler {
    @Override
    public String type() { return "page_index_at_least"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        Integer value = context.param("value", Integer.class);
        if (value == null) return CompletableFuture.completedFuture(ConditionResult.fail("missing value param"));
        
        return MenuSystem.getInstance().getMenuService().getCurrentSession(context.player().getUUID())
            .map(session -> {
                boolean result = session.getCurrentPageIndex() >= value;
                if (context.spec().negate()) result = !result;
                return result ? CompletableFuture.completedFuture(ConditionResult.pass()) : CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
            })
            .orElse(CompletableFuture.completedFuture(ConditionResult.fail("No session")));
    }
}
