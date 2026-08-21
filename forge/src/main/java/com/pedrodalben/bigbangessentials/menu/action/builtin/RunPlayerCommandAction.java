package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RunPlayerCommandAction implements MenuActionHandler {
    @Override
    public String type() { return "run_player_command"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        String command = context.param("command", String.class);
        if (command != null) {
            String finalCmd = com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService.resolve(command, context.player(), context.context());
            context.player().getServer().submit(() -> {
                context.player().getServer().getCommands().performPrefixedCommand(context.player().createCommandSourceStack(), finalCmd);
            });
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
