
package com.zerog.bigbangessentials.chat.command;
import com.zerog.bigbangessentials.chat.ChatManager;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import com.zerog.bigbangessentials.util.MessageUtil;

/**
 * Handles the /unmute command for unmuting a player.
 */
public class UnmuteCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("unmute")
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer targetPlayer;
                    try {
                        targetPlayer = EntityArgument.getPlayer(ctx, "target");
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unmute.player_not_found"));
                        return 0;
                    }
                    String targetName = targetPlayer.getName().getString();
                    
                    // Validate sender
                    ServerPlayer sender = source.getPlayer();
                    if (sender == null) {
                        source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                        return 0;
                    }
                    
                    // Check if chat module is enabled
                    if (!com.zerog.bigbangessentials.config.ConfigManager.isChatEnabled()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unmute.disabled"));
                        return 0;
                    }
                    
                    // Check if individual unmute command is enabled
                    if (!com.zerog.bigbangessentials.config.ConfigManager.getInstance().isCommandEnabled("unmute")) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unmute.disabled"));
                        return 0;
                    }
                    
                    // Legacy check for backwards compatibility
                    ChatManager chatManager = com.zerog.bigbangessentials.api.ChatAPI.getChatManager();
                    if (chatManager != null && !chatManager.isUnmuteEnabled()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unmute.disabled"));
                        return 0;
                    }
                    
                    // Check permissions
                    if (!com.zerog.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.mute")) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.no_permission"));
                        return 0;
                    }
                    
                    // Check if player is actually muted
                    if (!com.zerog.bigbangessentials.chat.MuteManager.getMutedPlayers().contains(targetName.toLowerCase())) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.unmute.not_muted", targetName));
                        return 0;
                    }
                    
                    com.zerog.bigbangessentials.chat.MuteManager.unmute(sender, targetName);
                    // Notify Discord integrations
                    try {
                        com.zerog.bigbangessentials.integrations.ChatIntegrationManager.broadcastMuteEvent(targetPlayer, null, false);
                    } catch (Exception ignored) {}
                    source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.unmute.success", targetName), false);
                    return 1;
                })
            )
        );
    }
}
