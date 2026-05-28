
package com.zerog.bigbangessentials.chat.command;
import com.zerog.bigbangessentials.chat.ChatManager;
import com.zerog.bigbangessentials.util.MessageUtil;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles the /unignore command for removing a player from the ignore list.
 */
public class UnignoreCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerUnignoreCommand(dispatcher, "unignore");
        registerUnignoreCommand(dispatcher, "unblock");
    }
    
    private static void registerUnignoreCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer targetPlayer;
                    try {
                        targetPlayer = EntityArgument.getPlayer(ctx, "target");
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unignore.player_not_found"));
                        return 0;
                    }
                    String targetName = targetPlayer.getName().getString();
                    
                    // Validate sender
                    ServerPlayer sender = source.getPlayer();
                    if (sender == null) {
                        source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                        return 0;
                    }
                    
                    // Check permissions
                    ChatManager chatManager = com.zerog.bigbangessentials.api.ChatAPI.getChatManager();
                    if (chatManager != null && !chatManager.isUnignoreEnabled()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unignore.disabled"));
                        return 0;
                    }
                    
                    // Proper permission validation using PermissionAPI
                    if (!com.zerog.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.ignore")) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unignore.no_permission"));
                        return 0;
                    }
                    
                    // Check if player is actually ignoring the target
                    if (!com.zerog.bigbangessentials.chat.IgnoreManager.isIgnoring(sender, targetName)) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unignore.not_ignored", targetName));
                        return 0;
                    }
                    
                    com.zerog.bigbangessentials.chat.IgnoreManager.unignore(sender, targetName);
                    source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.unignore.success", targetName), false);
                    return 1;
                })
            )
        );
    }
}
