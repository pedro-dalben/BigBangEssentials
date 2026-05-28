
package com.zerog.bigbangessentials.chat.command;
import com.zerog.bigbangessentials.chat.ChatManager;
import com.zerog.bigbangessentials.util.MessageUtil;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Handles the /mutelist command for listing all muted players.
 */
public class MuteListCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mutelist")
            .executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                
                // Validate sender
                net.minecraft.server.level.ServerPlayer sender = source.getPlayer();
                if (sender == null) {
                    source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                    return 0;
                }
                
                // Check if command is enabled
                ChatManager chatManager = com.zerog.bigbangessentials.api.ChatAPI.getChatManager();
                if (chatManager != null && !chatManager.isMuteListEnabled()) {
                    source.sendFailure(MessageUtil.error("commands.bigbangessentials.mutelist.disabled"));
                    return 0;
                }
                
                // Check permissions
                if (!com.zerog.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.mute")) {
                    source.sendFailure(MessageUtil.error("commands.bigbangessentials.no_permission"));
                    return 0;
                }
                
                java.util.List<String> muted = new java.util.ArrayList<>(com.zerog.bigbangessentials.chat.MuteManager.getMutedPlayers());
                if (muted.isEmpty()) {
                    source.sendSuccess(() -> MessageUtil.component("commands.bigbangessentials.mutelist.empty"), false);
                } else {
                    String mutedList = String.join(", ", muted);
                    source.sendSuccess(() -> MessageUtil.component("commands.bigbangessentials.mutelist.list", mutedList), false);
                }
                return 1;
            })
        );
    }
}
