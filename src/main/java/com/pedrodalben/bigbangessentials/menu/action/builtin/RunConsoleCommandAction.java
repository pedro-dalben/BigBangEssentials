package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RunConsoleCommandAction implements MenuActionHandler {
    @Override
    public String type() { return "run_console_command"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        String command = context.param("command", String.class);
        if (command != null) {
            String finalCmd = command.replace("{player_name}", context.player().getGameProfile().getName());
            context.player().getServer().submit(() -> {
                context.player().getServer().getCommands().performPrefixedCommand(context.player().getServer().createCommandSourceStack(), finalCmd);
            });
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
