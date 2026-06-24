package com.pedrodalben.bigbangessentials.menu.integration.teleportation.condition;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.teleportation.HomeManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HomeExistsCondition implements MenuConditionHandler {
    @Override
    public String type() { return "home_exists"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        String homeName = context.param("home-name", String.class);
        if (homeName != null && HomeManager.getInstance().getHome(context.player(), homeName) != null) {
            return CompletableFuture.completedFuture(ConditionResult.pass());
        }
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
