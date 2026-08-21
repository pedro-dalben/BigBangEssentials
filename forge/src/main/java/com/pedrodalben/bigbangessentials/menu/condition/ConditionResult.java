package com.pedrodalben.bigbangessentials.menu.condition;

import com.pedrodalben.bigbangessentials.menu.model.ConditionResultType;

public record ConditionResult(ConditionResultType type, String failureMessageKey) {
    public static ConditionResult pass() { return new ConditionResult(ConditionResultType.PASS, null); }
    public static ConditionResult fail(String msg) { return new ConditionResult(ConditionResultType.FAIL, msg); }
}
