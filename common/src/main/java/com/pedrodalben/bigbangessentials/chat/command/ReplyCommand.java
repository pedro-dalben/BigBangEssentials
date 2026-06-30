package com.pedrodalben.bigbangessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.ChatDebugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the /reply command for replying to the last private message sender.
 */
public class ReplyCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplyCommand.class);
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ChatDebugUtil.debug("ReplyCommand - Registering /reply command");
        // Register with vanilla aliases to override vanilla behavior
        registerCommand(dispatcher, "reply");
        registerCommand(dispatcher, "r");
    }
    
    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .requires(ReplyCommand::canUseReplyCommand)
            .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ChatDebugUtil.debug("ReplyCommand - Command executed!");
                    CommandSourceStack source = ctx.getSource();
                    String message = StringArgumentType.getString(ctx, "message");
                    
                    // Validate sender
                    ServerPlayer sender = source.getPlayer();
                    if (sender == null) {
                        source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                        return 0;
                    }
                    
                    // Find target from last message history
                    ChatDebugUtil.debug("ReplyCommand - Looking for last messager for %s", sender.getName().getString());
                    ServerPlayer target = com.pedrodalben.bigbangessentials.chat.LastMessageManager.getLastMessager(sender);
                    ChatDebugUtil.debug("ReplyCommand - Found target: %s", (target != null ? target.getName().getString() : "null"));
                    if (target == null) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.no_target"));
                        return 0;
                    }
                    
                    // Show who we're replying to for confirmation
                    LOGGER.debug("Player {} replying to {}", sender.getName().getString(), target.getName().getString());
                    
                    // Check if target is still online
                    if (!target.getServer().getPlayerList().getPlayers().contains(target)) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.target_offline"));
                        return 0;
                    }
                    
                    // Check if chat module is enabled
                    if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isChatEnabled()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.disabled"));
                        return 0;
                    }
                    
                    // Check if individual reply command is enabled
                    if (!com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isCommandEnabled("reply")) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.disabled"));
                        return 0;
                    }
                    
                    // Legacy check for backwards compatibility
                    com.pedrodalben.bigbangessentials.chat.ChatManager chatManager = com.pedrodalben.bigbangessentials.api.ChatAPI.getChatManager();
                    if (chatManager != null && !chatManager.isReplyEnabled()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.disabled"));
                        return 0;
                    }
                    
                    // Proper permission validation using PermissionAPI
                    if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.reply")) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.no_permission"));
                        return 0;
                    }
                    
                    // --- Mute/ignore/msgtoggle check ---
                    if (com.pedrodalben.bigbangessentials.chat.MuteManager.isMuted(sender)) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.sender_muted"));
                        return 0;
                    }
                    
                    if (com.pedrodalben.bigbangessentials.chat.IgnoreManager.isIgnoring(target, sender)) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.target_ignoring"));
                        return 0;
                    }
                    
                    if (com.pedrodalben.bigbangessentials.chat.MsgToggleManager.isMsgToggled(target)) {
                        // Check if sender has bypass permission
                        if (!sender.hasPermissions(4) && !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.msgtoggle.bypass")) {
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.reply.target_toggled_off", target.getName().getString()));
                            return 0;
                        }
                    }
                    
                    // Send reply messages using PlaceholderAPI for consistent placeholder support
                    String toTemplate = MessageUtil.localize("commands.bigbangessentials.reply.format.to");
                    String fromTemplate = MessageUtil.localize("commands.bigbangessentials.reply.format.from");
                    
                    // Add MESSAGE placeholder and resolve with PlaceholderAPI
                    String toMessage = toTemplate.replace("{MESSAGE}", message);
                    String fromMessage = fromTemplate.replace("{MESSAGE}", message);
                    
                    String resolvedToMessage = com.pedrodalben.bigbangessentials.api.PlaceholderAPI.setPlaceholders(target, toMessage);
                    String resolvedFromMessage = com.pedrodalben.bigbangessentials.api.PlaceholderAPI.setPlaceholders(sender, fromMessage);
                    
                    target.sendSystemMessage(MessageUtil.coloredText(resolvedFromMessage));
                    sender.sendSystemMessage(MessageUtil.coloredText(resolvedToMessage));
                    
                    // When replying, the target should now be able to reply back to the sender
                    ChatDebugUtil.debug("ReplyCommand - Setting last messager: %s can reply to %s", target.getName().getString(), sender.getName().getString());
                    com.pedrodalben.bigbangessentials.chat.LastMessageManager.setLastMessager(target, sender);
                    
                    com.pedrodalben.bigbangessentials.api.ChatAPI.broadcastSocialSpy(sender, target, message);
                    
                    // --- External plugin integration ---
                    com.pedrodalben.bigbangessentials.integrations.ChatIntegrationManager.broadcastPrivateMessage(sender, target, message);
                    
                    return 1;
                })
            )
        );
    }

    private static boolean canUseReplyCommand(CommandSourceStack source) {
        ServerPlayer sender = source.getPlayer();
        return sender != null &&
            com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                sender.getUUID(), "bigbangessentials.chat.reply");
    }
}
