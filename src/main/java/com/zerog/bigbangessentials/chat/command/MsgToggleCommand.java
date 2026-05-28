package com.zerog.bigbangessentials.chat.command;
import com.zerog.bigbangessentials.chat.MsgToggleManager;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import com.zerog.bigbangessentials.api.ChatAPI;
import com.zerog.bigbangessentials.chat.ChatManager;
import com.zerog.bigbangessentials.util.MessageUtil;

/**
 * Handles the /msgtoggle command for toggling private message reception.
 */
public class MsgToggleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerMsgToggleCommand(dispatcher, "msgtoggle");
        registerMsgToggleCommand(dispatcher, "togglemsg");
        registerMsgToggleCommand(dispatcher, "mt");
    }
    
    private static void registerMsgToggleCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                ServerPlayer sender = source.getPlayer();
                ChatManager chatManager = ChatAPI.getChatManager();
                if (chatManager != null && !chatManager.isMsgToggleEnabled()) {
                    source.sendFailure(MessageUtil.error("commands.bigbangessentials.msgtoggle.disabled"));
                    return 0;
                }
                // Check permissions with op bypass
                if (!sender.hasPermissions(4) && !com.zerog.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.msgtoggle")) {
                    source.sendFailure(MessageUtil.error("commands.bigbangessentials.no_permission"));
                    return 0;
                }
                boolean newState = MsgToggleManager.toggleMsg(sender);
                if (newState) {
                    source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.msgtoggle.enabled"), false);
                } else {
                    source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.msgtoggle.disabled_status"), false);
                }
                return 1;
            })
        );
    }
}
