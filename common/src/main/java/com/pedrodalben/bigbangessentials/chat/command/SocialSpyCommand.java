package com.pedrodalben.bigbangessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.api.ChatAPI;
import com.pedrodalben.bigbangessentials.chat.ChatManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;

/**
 * Handles the /socialspy command for toggling message spying for moderators/admins.
 */
public class SocialSpyCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerSocialSpyCommand(dispatcher, "socialspy");
        registerSocialSpyCommand(dispatcher, "ss");
        registerSocialSpyCommand(dispatcher, "spy");
    }
    
    private static void registerSocialSpyCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                
                // Validate sender
                ServerPlayer sender = source.getPlayer();
                if (sender == null) {
                    source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                    return 0;
                }
                
                ChatManager chatManager = ChatAPI.getChatManager();
                // Check if chat module is enabled
                if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isChatEnabled()) {
                    ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.socialspy.disabled"));
                    return 0;
                }
                
                // Check if individual socialspy command is enabled
                if (!com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isCommandEnabled("socialspy")) {
                    ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.socialspy.disabled"));
                    return 0;
                }
                
                // Legacy check for backwards compatibility
                if (chatManager != null && !chatManager.isSocialSpyEnabled()) {
                    sender.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.socialspy.disabled"));
                    return 0;
                }
                
                // Check if player has socialspy permission
                if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.socialspy")) {
                    // Debug: Check if the key exists
                    MessageUtil.debugKey("commands.bigbangessentials.socialspy.no_permission");
                    sender.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.socialspy.no_permission"));
                    return 0;
                }
                
                com.pedrodalben.bigbangessentials.chat.SocialSpyManager.toggleSocialSpy(sender);
                boolean isNowEnabled = com.pedrodalben.bigbangessentials.chat.SocialSpyManager.hasSocialSpy(sender);
                
                if (isNowEnabled) {
                    sender.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.socialspy.enabled"));
                } else {
                    sender.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.socialspy.disabled_status"));
                }
                return 1;
            })
        );
    }
}
