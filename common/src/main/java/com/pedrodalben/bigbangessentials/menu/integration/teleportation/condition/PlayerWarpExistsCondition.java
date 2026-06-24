package com.pedrodalben.bigbangessentials.menu.integration.teleportation.condition;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PlayerWarpExistsCondition implements MenuConditionHandler {
    @Override
    public String type() { return "pwarp_exists"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        String warpName = context.param("pwarp-name", String.class);
        if (warpName != null && WarpManager.getInstance().getPlayerWarp(context.player(), warpName) != null) {
            return CompletableFuture.completedFuture(ConditionResult.pass());
        }
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
