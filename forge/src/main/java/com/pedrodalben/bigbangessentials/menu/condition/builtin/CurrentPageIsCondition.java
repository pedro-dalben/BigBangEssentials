package com.pedrodalben.bigbangessentials.menu.condition.builtin;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CurrentPageIsCondition implements MenuConditionHandler {
    @Override
    public String type() { return "current_page_is"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        String page = context.param("page", String.class);
        if (page == null) {
            page = context.param("page_id", String.class);
        }
        if (page == null) return CompletableFuture.completedFuture(ConditionResult.fail("missing page param"));
        
        String resolvedPage = PlaceholderService.resolve(page, context.player(), context.context());
        
        return MenuSystem.getInstance().getMenuService().getCurrentSession(context.player().getUUID())
            .map(session -> {
                boolean result = session.getCurrentPageId().equals(resolvedPage);
                if (context.spec().negate()) result = !result;
                return result ? CompletableFuture.completedFuture(ConditionResult.pass()) : CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
            })
            .orElse(CompletableFuture.completedFuture(ConditionResult.fail("No session")));
    }
}
