package com.pedrodalben.bigbangessentials.menu.action;

import java.util.concurrent.CompletionStage;

public interface MenuActionHandler {
    String type();
    CompletionStage<ActionExecutionResult> execute(ActionContext context);
}
