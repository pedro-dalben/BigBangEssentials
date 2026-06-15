package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SendMessageAction implements MenuActionHandler {
    @Override
    public String type() { return "send_message"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        String message = context.param("message", String.class);
        if (message != null) {
            context.player().sendSystemMessage(Component.literal(message.replace("<gold>", "§6").replace("<red>", "§c").replace("<green>", "§a")));
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
