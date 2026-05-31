
package com.pedrodalben.bigbangessentials.chat.command;
import com.pedrodalben.bigbangessentials.chat.ChatManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles the /ignore command for ignoring messages from a player.
 */
public class IgnoreCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register main command
        registerIgnoreCommand(dispatcher, "ignore");
        // Register alias
        registerIgnoreCommand(dispatcher, "block");
    }
    
    private static void registerIgnoreCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer targetPlayer;
                    try {
                        targetPlayer = EntityArgument.getPlayer(ctx, "target");
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.ignore.player_not_found"));
                        return 0;
                    }
                    String targetName = targetPlayer.getName().getString();
                    
                    // Validate sender
                    ServerPlayer sender = source.getPlayer();
                    if (sender == null) {
                        source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                        return 0;
                    }
                    
                    // Check if trying to ignore self
                    if (sender.getName().getString().equalsIgnoreCase(targetName)) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.ignore.self"));
                        return 0;
                    }
                    
                    // Check permissions
                    ChatManager chatManager = com.pedrodalben.bigbangessentials.api.ChatAPI.getChatManager();
                    // Check if chat module is enabled
                    if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isChatEnabled()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.ignore.disabled"));
                        return 0;
                    }
                    
                    // Check if individual ignore command is enabled
                    if (!com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isCommandEnabled("ignore")) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.ignore.disabled"));
                        return 0;
                    }
                    
                    // Legacy check for backwards compatibility
                    if (chatManager != null && !chatManager.isIgnoreEnabled()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.ignore.disabled"));
                        return 0;
                    }
                    
                    // Proper permission validation using PermissionAPI
                    if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.ignore")) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.ignore.no_permission"));
                        return 0;
                    }
                    
                    // Check if player is already ignoring target
                    if (com.pedrodalben.bigbangessentials.chat.IgnoreManager.isIgnoring(sender, targetName)) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.ignore.already_ignored", targetName));
                        return 0;
                    }
                    
                    // Check if target has exempt permission
                    if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(targetPlayer.getUUID(), "bigbangessentials.chat.ignore.exempt")) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.ignore.exempt", targetName));
                        return 0;
                    }
                    
                    com.pedrodalben.bigbangessentials.chat.IgnoreManager.ignore(sender, targetName);
                    source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.ignore.success", targetName), false);
                    return 1;
                })
            )
        );
    }
}
