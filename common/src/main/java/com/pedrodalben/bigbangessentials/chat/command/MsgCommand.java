package com.pedrodalben.bigbangessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.api.ChatAPI;
import com.pedrodalben.bigbangessentials.chat.ChatManager;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakePlayerIntegration;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakePlayerSnapshot;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.ChatDebugUtil;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class MsgCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MsgCommand.class);

    private static final SuggestionProvider<CommandSourceStack> FAKE_PLAYER_SUGGESTIONS = (ctx, builder) -> {
        for (FakePlayerSnapshot fake : FakePlayerIntegration.getInstance().getAllFakePlayers()) {
            builder.suggest(fake.username());
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ChatDebugUtil.debug("MsgCommand - Registering /msg command");
        registerCommand(dispatcher, "msg");
        registerCommand(dispatcher, "tell");
        registerCommand(dispatcher, "w");
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
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | NullPointerException e) {
                            String targetName = null;
                            for (com.mojang.brigadier.context.ParsedCommandNode<?> node : ctx.getNodes()) {
                                if (node.getNode().getName().equals("target")) {
                                    targetName = node.getRange().get(ctx.getInput());
                                    break;
                                }
                            }
                            if (targetName != null) {
                                ServerPlayer sender = source.getPlayer();
                                if (sender != null) {
                                    if (sender.getName().getString().equalsIgnoreCase(targetName)) {
                                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.self"));
                                        return 0;
                                    }
                                    Optional<FakePlayerSnapshot> fakeOpt = FakePlayerIntegration.getInstance().findActiveFakePlayer(targetName);
                                    if (fakeOpt.isPresent()) {
                                        String message = StringArgumentType.getString(ctx, "message");
                                        return handleFakePlayerMsg(sender, fakeOpt.get(), message, source);
                                    }
                                }
                                source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.not_found", targetName));
                            } else {
                                source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.not_found", "Unknown"));
                            }
                            return 0;
                        }
                        String message = StringArgumentType.getString(ctx, "message");

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

                        if (sender.equals(target)) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Player trying to message self");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.self"));
                            return 0;
                        }

                        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isChatEnabled()) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Chat module is disabled");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.disabled"));
                            return 0;
                        }

                        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isCommandEnabled("msg")) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Msg command is disabled");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.disabled"));
                            return 0;
                        }

                        ChatManager chatManager = ChatAPI.getChatManager();
                        if (chatManager != null && !chatManager.isMsgEnabled()) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: Messaging is disabled (legacy check)");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.disabled"));
                            return 0;
                        }

                        boolean hasPermission = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.msg");
                        ChatDebugUtil.debug("MsgCommand - Permission check for %s: %s", sender.getName().getString(), hasPermission);
                        if (!hasPermission) {
                            ChatDebugUtil.debug("MsgCommand - FAILED: No permission for bigbangessentials.chat.msg");
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.no_permission"));
                            return 0;
                        }

                        String senderName = sender.getName().getString();
                        boolean isMuted = com.pedrodalben.bigbangessentials.chat.MuteManager.isMuted(sender);
                        ChatDebugUtil.debug("MsgCommand - Checking mute for %s, result: %s", senderName, isMuted);
                        if (isMuted) {
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
                            if (!sender.hasPermissions(4) && !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.msgtoggle.bypass")) {
                                ChatDebugUtil.debug("MsgCommand - FAILED: Target has messaging toggled off and sender lacks bypass");
                                source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.target_toggled_off", target.getName().getString()));
                                return 0;
                            }
                        }

                        ChatDebugUtil.debug("MsgCommand - SUCCESS: All checks passed, sending message");

                        String toTemplate = MessageUtil.localize("commands.bigbangessentials.msg.format.to");
                        String fromTemplate = MessageUtil.localize("commands.bigbangessentials.msg.format.from");

                        String toMessage = toTemplate.replace("{MESSAGE}", message);
                        String fromMessage = fromTemplate.replace("{MESSAGE}", message);

                        String resolvedToMessage = com.pedrodalben.bigbangessentials.api.PlaceholderAPI.setPlaceholders(target, toMessage);
                        String resolvedFromMessage = com.pedrodalben.bigbangessentials.api.PlaceholderAPI.setPlaceholders(sender, fromMessage);

                        target.sendSystemMessage(MessageUtil.coloredText(resolvedFromMessage));
                        sender.sendSystemMessage(MessageUtil.coloredText(resolvedToMessage));

                        ChatDebugUtil.debug("MsgCommand - Setting last messager: %s can reply to %s", target.getName().getString(), sender.getName().getString());
                        com.pedrodalben.bigbangessentials.chat.LastMessageManager.setLastMessager(target, sender);

                        ChatAPI.broadcastSocialSpy(sender, target, message);
                        com.pedrodalben.bigbangessentials.integrations.ChatIntegrationManager.broadcastPrivateMessage(sender, target, message);

                        return 1;
                    })
                )
            )
            .then(Commands.argument("targetname", StringArgumentType.word())
                .suggests(FAKE_PLAYER_SUGGESTIONS)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ChatDebugUtil.debug("MsgCommand - Fake player fallback path");
                        CommandSourceStack source = ctx.getSource();
                        String targetName = StringArgumentType.getString(ctx, "targetname");
                        String message = StringArgumentType.getString(ctx, "message");

                        ServerPlayer sender = source.getPlayer();
                        if (sender == null) {
                            source.sendFailure(MessageUtil.error("bigbangessentials.error.no_server"));
                            return 0;
                        }

                        MinecraftServer server = sender.getServer();
                        if (sender.getName().getString().equalsIgnoreCase(targetName)) {
                            source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.self"));
                            return 0;
                        }

                        ServerPlayer realTarget = null;
                        if (server != null) {
                            realTarget = server.getPlayerList().getPlayerByName(targetName);
                        }
                        if (realTarget != null) {
                            return 0;
                        }

                        Optional<FakePlayerSnapshot> fakeOpt = FakePlayerIntegration.getInstance().findActiveFakePlayer(targetName);
                        if (fakeOpt.isPresent()) {
                            return handleFakePlayerMsg(sender, fakeOpt.get(), message, source);
                        }

                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.msg.not_found", targetName));
                        return 0;
                    })
                )
            )
        );
    }

    private static int handleFakePlayerMsg(ServerPlayer sender, FakePlayerSnapshot fake, String message, CommandSourceStack source) {
        ChatDebugUtil.debug("MsgCommand - Sending message to fake player %s", fake.username());
        LOGGER.debug("Fake player msg: sender={}, target={}, message={}",
            sender.getName().getString(), fake.username(), message);

        String toTemplate = MessageUtil.localize("commands.bigbangessentials.fakeplayer.msg.sent");
        String toMessage = toTemplate.replace("{MESSAGE}", message)
            .replace("{0}", fake.username());

        sender.sendSystemMessage(MessageUtil.coloredText(toMessage));
        return 1;
    }

    private static boolean canUseMsgCommand(CommandSourceStack source) {
        ServerPlayer sender = source.getPlayer();
        return sender != null &&
            com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                sender.getUUID(), "bigbangessentials.chat.msg");
    }
}
