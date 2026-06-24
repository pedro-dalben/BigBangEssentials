package com.pedrodalben.bigbangessentials.menu.condition;

import java.util.concurrent.CompletionStage;

public interface MenuConditionHandler {
    String type();
    CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context);
}
