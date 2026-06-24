
package com.pedrodalben.bigbangessentials.chat.command;
import com.pedrodalben.bigbangessentials.chat.ChatManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.pedrodalben.bigbangessentials.util.MessageUtil;

/**
 * Handles the /mute command for muting a player.
 */
public class MuteCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerMuteCommand(dispatcher, "mute");
        registerMuteCommand(dispatcher, "silence");  
    }
    
    private static void registerMuteCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> executeMute(ctx, ""))
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> executeMute(ctx, StringArgumentType.getString(ctx, "reason")))
                )
            )
        );
    }
    
    private static int executeMute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String reason) {
        CommandSourceStack source = ctx.getSource();
        
        // Check if chat module is enabled
        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isChatEnabled()) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.mute.disabled"));
            return 0;
        }
        
        // Check if individual mute command is enabled
        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isCommandEnabled("mute")) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.mute.disabled"));
            return 0;
        }
        
        net.minecraft.server.level.ServerPlayer targetPlayer;
        try {
            targetPlayer = EntityArgument.getPlayer(ctx, "target");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.mute.player_not_found"));
            return 0;
        }
        String targetName = targetPlayer.getName().getString();
        
        // Validate sender
        net.minecraft.server.level.ServerPlayer sender = source.getPlayer();
        if (sender == null) {
            source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
            return 0;
        }
        
        // Check if command is enabled
        ChatManager chatManager = com.pedrodalben.bigbangessentials.api.ChatAPI.getChatManager();
        if (chatManager != null && !chatManager.isMuteEnabled()) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.mute.disabled"));
            return 0;
        }
        
        // Check permissions
        if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.mute")) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.no_permission"));
            return 0;
        }
        
        // Check if trying to mute self
        if (sender.getName().getString().equalsIgnoreCase(targetName)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.mute.self"));
            return 0;
        }
        
        // Check if target has exempt permission
        if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(targetPlayer.getUUID(), "bigbangessentials.chat.mute.exempt")) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.mute.exempt", targetName));
            return 0;
        }
        
        com.pedrodalben.bigbangessentials.chat.MuteManager.mute(sender, targetName);
        // Notify Discord integrations
        try {
            com.pedrodalben.bigbangessentials.integrations.ChatIntegrationManager.broadcastMuteEvent(targetPlayer, reason.isEmpty() ? "No reason given" : reason, true);
        } catch (Exception ignored) {}
        if (reason.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.mute.success", targetName), false);
        } else {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.mute.success_with_reason", targetName, reason), false);
        }
        return 1;
    }
}
