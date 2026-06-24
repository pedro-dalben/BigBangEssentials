package com.pedrodalben.bigbangessentials.menu.condition.builtin;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PermissionCondition implements MenuConditionHandler {
    @Override
    public String type() { return "permission"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        String perm = context.param("permission", String.class);
        if (perm == null) return CompletableFuture.completedFuture(ConditionResult.fail("missing permission param"));
        
        boolean has = PermissionAPI.hasPermission(context.player().getUUID(), perm);
        if (context.spec().negate()) has = !has;
        
        if (has) return CompletableFuture.completedFuture(ConditionResult.pass());
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
