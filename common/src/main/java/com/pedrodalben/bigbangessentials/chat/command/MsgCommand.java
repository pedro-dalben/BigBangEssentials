package com.pedrodalben.bigbangessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.api.ChatAPI;
import com.pedrodalben.bigbangessentials.chat.ChatManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.ChatDebugUtil;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the /msg command for private messaging between players.
 */
public class MsgCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MsgCommand.class);

    /**
     * Registers the /msg command with the dispatcher.
     * @param dispatcher The command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ChatDebugUtil.debug("MsgCommand - Registering /msg command");
        // Register with vanilla aliases to override vanilla behavior
        registerCommand(dispatcher, "msg");
        registerCommand(dispatcher, "tell");
        registerCommand(dispatcher, "w");
        
        // Also register with test names to see if custom commands work at all
        ChatDebugUtil.debug("MsgCommand - Also registering test commands: /message, /pm");
        registerCommand(dispatcher, "message");
        registerCommand(dispatcher, "pm");
    }
    
    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .requires(MsgCommand::canUseMsgCommand)
            .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ChatDebugUtil.debug("MsgCommand - Command executed!");
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer target;
                        try {
                            target = EntityArgument.getPlayer(ctx, "target");
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.not_found", "Unknown"));
                            return 0;
                        }
                        String message = StringArgumentType.getString(ctx, "message");
                        
                        // Validate sender
                        ServerPlayer sender = source.getPlayer();
                        if (sender == null) {
                            source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                            return 0;
                        }
                        
                        MinecraftServer server = sender.getServer();
                        if (server == null) {
                            source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                            return 0;
                        }
                        
                        ChatDebugUtil.debug("MsgCommand - Processing message from %s to %s", sender.getName().getString(), target.getName().getString());
                        
                        // Check if messaging self
                        if (sender.equals(target)) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Player trying to message self");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.self"));
                            return 0;
                        }
                        
                        // Check if chat module is enabled
                        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isChatEnabled()) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Chat module is disabled");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.disabled"));
                            return 0;
                        }
                        
                        // Check if individual msg command is enabled
                        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isCommandEnabled("msg")) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Msg command is disabled");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.disabled"));
                            return 0;
                        }
                        
                        // Legacy check for backwards compatibility
                        ChatManager chatManager = ChatAPI.getChatManager();
                        if (chatManager != null && !chatManager.isMsgEnabled()) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Messaging is disabled (legacy check)");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.disabled"));
                            return 0;
                        }
                        
                        // Proper permission validation using PermissionAPI
                        boolean hasPermission = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.msg");
                        ChatDebugUtil.debug("MsgCommand - Permission check for %s: %s", sender.getName().getString(), hasPermission);
                        if (!hasPermission) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: No permission for bigbangessentials.chat.msg");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.no_permission"));
                            return 0;
                        }
                        
                        // --- Mute/ignore/msgtoggle check ---
                        String senderName = sender.getName().getString();
                        boolean isMuted = com.pedrodalben.bigbangessentials.chat.MuteManager.isMuted(sender);
                        ChatDebugUtil.debug("MsgCommand - Checking mute for %s, result: %s", senderName, isMuted);
                        if (isMuted) {
                            // Log for debugging
                            ChatDebugUtil.debug("MsgCommand - FAILED: Player is muted");
                            LOGGER.debug("Blocked /msg from muted player: {}", senderName);
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.sender_muted"));
                            return 0;
                        }
                        
                        if (com.pedrodalben.bigbangessentials.chat.IgnoreManager.isIgnoring(target, sender)) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Target is ignoring sender");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.target_ignoring"));
                            return 0;
                        }
                        
                        if (com.pedrodalben.bigbangessentials.chat.MsgToggleManager.isMsgToggled(target)) {
                            // Check if sender has bypass permission
                            if (!sender.hasPermissions(4) && !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.msgtoggle.bypass")) {
                                ChatDebugUtil.debug("MsgCommand - FAILED: Target has messaging toggled off and sender lacks bypass");
                                source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.target_toggled_off", target.getName().getString()));
                                return 0;
                            }
                        }
                        
                        ChatDebugUtil.debug("MsgCommand - SUCCESS: All checks passed, sending message");
                        
                        // Send message using PlaceholderAPI for consistent placeholder support
                        String toTemplate = MessageUtil.localize("commands.bigbangessentials.msg.format.to");
                        String fromTemplate = MessageUtil.localize("commands.bigbangessentials.msg.format.from");
                        
                        // Add MESSAGE placeholder and resolve with PlaceholderAPI
                        String toMessage = toTemplate.replace("{MESSAGE}", message);
                        String fromMessage = fromTemplate.replace("{MESSAGE}", message);
                        
                        String resolvedToMessage = com.pedrodalben.bigbangessentials.api.PlaceholderAPI.setPlaceholders(target, toMessage);
                        String resolvedFromMessage = com.pedrodalben.bigbangessentials.api.PlaceholderAPI.setPlaceholders(sender, fromMessage);
                        
                        target.sendSystemMessage(MessageUtil.coloredText(resolvedFromMessage));
                        sender.sendSystemMessage(MessageUtil.coloredText(resolvedToMessage));
                        
                        // Update last message tracking for reply functionality
                        // Only the target should be able to reply to the sender
                        ChatDebugUtil.debug("MsgCommand - Setting last messager: %s can reply to %s", target.getName().getString(), sender.getName().getString());
                        com.pedrodalben.bigbangessentials.chat.LastMessageManager.setLastMessager(target, sender);
                        
                        // --- SocialSpy integration ---
                        ChatAPI.broadcastSocialSpy(sender, target, message);
                        
                        // --- External plugin integration ---
                        com.pedrodalben.bigbangessentials.integrations.ChatIntegrationManager.broadcastPrivateMessage(sender, target, message);
                        
                        return 1;
                    })
                )
            )
        );
    }

    private static boolean canUseMsgCommand(CommandSourceStack source) {
        ServerPlayer sender = source.getPlayer();
        return sender != null &&
            com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                sender.getUUID(), "bigbangessentials.chat.msg");
    }
}
