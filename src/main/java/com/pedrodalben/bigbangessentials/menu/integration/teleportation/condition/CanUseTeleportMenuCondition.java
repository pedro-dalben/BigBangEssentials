package com.pedrodalben.bigbangessentials.menu.integration.teleportation.condition;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CanUseTeleportMenuCondition implements MenuConditionHandler {
    @Override
    public String type() { return "can_use_teleport_menu"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        if (TeleportMenuConfig.isEnabled()) {
            return CompletableFuture.completedFuture(ConditionResult.pass());
        }
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
