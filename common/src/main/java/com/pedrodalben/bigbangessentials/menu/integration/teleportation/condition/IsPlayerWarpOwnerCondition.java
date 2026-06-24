package com.pedrodalben.bigbangessentials.menu.integration.teleportation.condition;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class IsPlayerWarpOwnerCondition implements MenuConditionHandler {
    @Override
    public String type() { return "is_pwarp_owner"; }

    @Override
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        String ownerUuidStr = context.param("pwarp-owner-uuid", String.class);
        if (ownerUuidStr != null) {
            try {
                UUID ownerUuid = UUID.fromString(ownerUuidStr);
                if (context.player().getUUID().equals(ownerUuid)) {
                    return CompletableFuture.completedFuture(ConditionResult.pass());
                }
            } catch (Exception ignored) {}
        }
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
