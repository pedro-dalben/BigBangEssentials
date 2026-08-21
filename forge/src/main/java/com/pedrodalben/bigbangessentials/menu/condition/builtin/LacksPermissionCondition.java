package com.pedrodalben.bigbangessentials.menu.condition.builtin;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class LacksPermissionCondition implements MenuConditionHandler {
    @Override
    public String type() { return "lacks_permission"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        String perm = context.param("permission", String.class);
        if (perm == null) return CompletableFuture.completedFuture(ConditionResult.fail("missing permission param"));
        
        String resolvedPerm = PlaceholderService.resolve(perm, context.player(), context.context());
        boolean has = PermissionAPI.hasPermission(context.player().getUUID(), resolvedPerm);
        
        // Lacks permission means the player should NOT have it
        boolean result = !has;
        if (context.spec().negate()) result = !result;
        
        if (result) return CompletableFuture.completedFuture(ConditionResult.pass());
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
