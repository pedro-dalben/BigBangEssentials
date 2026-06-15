package com.pedrodalben.bigbangessentials.menu.action;

import com.pedrodalben.bigbangessentials.menu.model.ActionStatus;

public record ActionExecutionResult(ActionStatus status, String errorMessage) {
    public static ActionExecutionResult success() { return new ActionExecutionResult(ActionStatus.SUCCESS, null); }
    public static ActionExecutionResult denied() { return new ActionExecutionResult(ActionStatus.DENIED, null); }
    public static ActionExecutionResult failed(String msg) { return new ActionExecutionResult(ActionStatus.FAILED, msg); }
    public static ActionExecutionResult skipped() { return new ActionExecutionResult(ActionStatus.SKIPPED, null); }
}
