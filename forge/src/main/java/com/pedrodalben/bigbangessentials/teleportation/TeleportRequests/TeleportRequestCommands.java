package com.pedrodalben.bigbangessentials.teleportation.TeleportRequests;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakePlayerIntegration;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakePlayerSnapshot;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakeTpaManager;
import com.pedrodalben.bigbangessentials.teleportation.Misc.MiscTeleportManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TeleportRequestCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportRequestCommands.class);

    private static final String PERMISSION_TPA = "bigbangessentials.teleport.request.tpa";
    private static final String PERMISSION_TPAHERE = "bigbangessentials.teleport.request.tpahere";
    private static final String PERMISSION_ACCEPT = "bigbangessentials.teleport.request.accept";
    private static final String PERMISSION_DENY = "bigbangessentials.teleport.request.deny";
    private static final String PERMISSION_CANCEL = "bigbangessentials.teleport.request.cancel";
    private static final String[] PERMISSION_TPA_COMPAT = {
        PERMISSION_TPA,
        "bigbangessentials.teleport.tpa"
    };
    private static final String[] PERMISSION_TPAHERE_COMPAT = {
        PERMISSION_TPAHERE,
        "bigbangessentials.teleport.tpahere"
    };
    private static final String[] PERMISSION_ACCEPT_COMPAT = {
        PERMISSION_ACCEPT,
        "bigbangessentials.teleport.tpaccept"
    };
    private static final String[] PERMISSION_DENY_COMPAT = {
        PERMISSION_DENY,
        "bigbangessentials.teleport.tpdeny"
    };
    private static final String[] PERMISSION_CANCEL_COMPAT = {
        PERMISSION_CANCEL,
        "bigbangessentials.teleport.tpacancel"
    };

    private static final SuggestionProvider<CommandSourceStack> FAKE_PLAYER_SUGGESTIONS = (ctx, builder) -> {
        for (FakePlayerSnapshot fake : FakePlayerIntegration.getInstance().getAllFakePlayers()) {
            builder.suggest(fake.username());
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ConfigManager config = ConfigManager.getInstance();

        if (config.isTeleportationEnabled()) {
            if (config.isCommandEnabled("tpa")) {
                dispatcher.register(
                    Commands.literal("tpa")
                        .requires(source -> {
                            if (source.getEntity() instanceof ServerPlayer player) {
                                boolean hasPerm = PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_TPA_COMPAT);
                                LOGGER.debug("[TPA] Checking permission {} for {}: {}", PERMISSION_TPA, player.getName().getString(), hasPerm);
                                return hasPerm;
                            }
                            return false;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> executeTpa(context))
                        )
                        .then(Commands.argument("playername", StringArgumentType.word())
                            .suggests(FAKE_PLAYER_SUGGESTIONS)
                            .executes(context -> executeTpaFakeFallback(context))
                        )
                );
            }

            if (config.isCommandEnabled("tpahere")) {
                dispatcher.register(
                    Commands.literal("tpahere")
                        .requires(source -> {
                            if (source.getEntity() instanceof ServerPlayer player) {
                                boolean hasPerm = PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_TPAHERE_COMPAT);
                                LOGGER.debug("[TPAHERE] Checking permission {} for {}: {}", PERMISSION_TPAHERE, player.getName().getString(), hasPerm);
                                return hasPerm;
                            }
                            return false;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> executeTpaHere(context))
                        )
                        .then(Commands.argument("playername", StringArgumentType.word())
                            .suggests(FAKE_PLAYER_SUGGESTIONS)
                            .executes(context -> executeTpaHereFakeFallback(context))
                        )
                );
            }

            if (config.isCommandEnabled("tpaccept")) {
                dispatcher.register(
                    Commands.literal("tpaccept")
                        .requires(source -> {
                            if (source.getEntity() instanceof ServerPlayer player) {
                                boolean hasPerm = PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_ACCEPT_COMPAT);
                                LOGGER.debug("[TPACCEPT] Checking permission {} for {}: {}", PERMISSION_ACCEPT, player.getName().getString(), hasPerm);
                                return hasPerm;
                            }
                            return false;
                        })
                        .executes(context -> executeTpAccept(context))
                );
            }

            if (config.isCommandEnabled("tpdeny")) {
                dispatcher.register(
                    Commands.literal("tpdeny")
                        .requires(source -> {
                            if (source.getEntity() instanceof ServerPlayer player) {
                                boolean hasPerm = PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_DENY_COMPAT);
                                LOGGER.debug("[TPDENY] Checking permission {} for {}: {}", PERMISSION_DENY, player.getName().getString(), hasPerm);
                                return hasPerm;
                            }
                            return false;
                        })
                        .executes(context -> executeTpDeny(context))
                );
            }

            if (config.isCommandEnabled("tpcancel") || config.isCommandEnabled("tpacancel")) {
                registerTpCancelCommand(dispatcher, "tpcancel");
                registerTpCancelCommand(dispatcher, "tpacancel");
            }

            LOGGER.info("Registered enabled teleport request commands");
        }
    }

    private static int executeTpa(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer requester = context.getSource().getPlayerOrException();
            ServerPlayer target;
            try {
                target = EntityArgument.getPlayer(context, "player");
            } catch (CommandSyntaxException | NullPointerException e) {
                String targetName = null;
                for (com.mojang.brigadier.context.ParsedCommandNode<?> node : context.getNodes()) {
                    if (node.getNode().getName().equals("player")) {
                        targetName = node.getRange().get(context.getInput());
                        break;
                    }
                }
                if (targetName != null) {
                    if (requester.getName().getString().equalsIgnoreCase(targetName)) {
                        requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.request.self"));
                        return 0;
                    }
                    Optional<FakePlayerSnapshot> fakeOpt = FakePlayerIntegration.getInstance().findActiveFakePlayer(targetName);
                    if (fakeOpt.isPresent()) {
                        if (!ConfigManager.getInstance().isFakeCommandTpaEnabled()) {
                            requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.fakeplayer.not_available", targetName));
                            return 0;
                        }
                        FakePlayerSnapshot fakePlayer = fakeOpt.get();
                        FakeTpaManager.getInstance().scheduleFakeTpa(requester, fakePlayer.username(), 30, 60);
                        return 1;
                    }
                }
                LOGGER.error("Command syntax error in /tpa", e);
                return 0;
            }

            if (requester.getUUID().equals(target.getUUID())) {
                requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.request.self"));
                return 0;
            }

            TeleportRequestManager manager = TeleportRequestManager.getInstance();
            return manager.sendTeleportRequest(requester, target, TeleportRequestType.TPA) ? 1 : 0;

        } catch (Exception e) {
            LOGGER.error("Error executing /tpa command", e);
            return 0;
        }
    }

    private static int executeTpaFakeFallback(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer requester = context.getSource().getPlayerOrException();
            String targetName = StringArgumentType.getString(context, "playername");

            if (requester.getName().getString().equalsIgnoreCase(targetName)) {
                requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.request.self"));
                return 0;
            }

            ServerPlayer realTarget = null;
            MinecraftServer server = context.getSource().getServer();
            if (server != null) {
                realTarget = server.getPlayerList().getPlayerByName(targetName);
            }
            if (realTarget != null) {
                return 0;
            }

            Optional<FakePlayerSnapshot> fakeOpt = FakePlayerIntegration.getInstance().findActiveFakePlayer(targetName);
            if (fakeOpt.isPresent()) {
                if (!ConfigManager.getInstance().isFakeCommandTpaEnabled()) {
                    requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.fakeplayer.not_available", targetName));
                    return 0;
                }
                LOGGER.debug("Fake TPA: requester={}, target={}",
                    requester.getName().getString(), targetName);
                FakeTpaManager.getInstance().scheduleFakeTpa(
                    requester,
                    targetName,
                    ConfigManager.getInstance().getFakeTpaMinExpirationSeconds(),
                    ConfigManager.getInstance().getFakeTpaMaxExpirationSeconds()
                );
                return 1;
            }

            requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.request.target_busy", targetName));
            return 0;

        } catch (CommandSyntaxException e) {
            LOGGER.error("Command syntax error in /tpa", e);
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error executing /tpa command", e);
            return 0;
        }
    }

    private static int executeTpaHere(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer requester = context.getSource().getPlayerOrException();
            ServerPlayer target;
            try {
                target = EntityArgument.getPlayer(context, "player");
            } catch (CommandSyntaxException | NullPointerException e) {
                String targetName = null;
                for (com.mojang.brigadier.context.ParsedCommandNode<?> node : context.getNodes()) {
                    if (node.getNode().getName().equals("player")) {
                        targetName = node.getRange().get(context.getInput());
                        break;
                    }
                }
                if (targetName != null) {
                    if (requester.getName().getString().equalsIgnoreCase(targetName)) {
                        requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.request.self"));
                        return 0;
                    }
                    Optional<FakePlayerSnapshot> fakeOpt = FakePlayerIntegration.getInstance().findActiveFakePlayer(targetName);
                    if (fakeOpt.isPresent()) {
                        if (!ConfigManager.getInstance().isFakeCommandTpaEnabled()) {
                            requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.fakeplayer.not_available", targetName));
                            return 0;
                        }
                        FakePlayerSnapshot fakePlayer = fakeOpt.get();
                        FakeTpaManager.getInstance().scheduleFakeTpa(requester, fakePlayer.username(), 30, 60);
                        return 1;
                    }
                }
                LOGGER.error("Command syntax error in /tpahere", e);
                return 0;
            }

            if (requester.getUUID().equals(target.getUUID())) {
                requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.request.self"));
                return 0;
            }

            TeleportRequestManager manager = TeleportRequestManager.getInstance();
            return manager.sendTeleportRequest(requester, target, TeleportRequestType.TPAHERE) ? 1 : 0;

        } catch (Exception e) {
            LOGGER.error("Error executing /tpahere command", e);
            return 0;
        }
    }

    private static int executeTpaHereFakeFallback(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer requester = context.getSource().getPlayerOrException();
            String targetName = StringArgumentType.getString(context, "playername");

            if (requester.getName().getString().equalsIgnoreCase(targetName)) {
                requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.request.self"));
                return 0;
            }

            ServerPlayer realTarget = null;
            MinecraftServer server = context.getSource().getServer();
            if (server != null) {
                realTarget = server.getPlayerList().getPlayerByName(targetName);
            }
            if (realTarget != null) {
                return 0;
            }

            Optional<FakePlayerSnapshot> fakeOpt = FakePlayerIntegration.getInstance().findActiveFakePlayer(targetName);
            if (fakeOpt.isPresent()) {
                if (!ConfigManager.getInstance().isFakeCommandTpaEnabled()) {
                    requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.fakeplayer.not_available", targetName));
                    return 0;
                }
                LOGGER.debug("Fake TPAHERE: requester={}, target={}",
                    requester.getName().getString(), targetName);
                FakeTpaManager.getInstance().scheduleFakeTpa(
                    requester,
                    targetName,
                    ConfigManager.getInstance().getFakeTpaMinExpirationSeconds(),
                    ConfigManager.getInstance().getFakeTpaMaxExpirationSeconds()
                );
                return 1;
            }

            requester.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.request.target_busy", targetName));
            return 0;

        } catch (CommandSyntaxException e) {
            LOGGER.error("Command syntax error in /tpahere", e);
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error executing /tpahere command", e);
            return 0;
        }
    }

    private static int executeTpAccept(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer teleportedPlayer = context.getSource().getPlayerOrException();
            TeleportRequestManager manager = TeleportRequestManager.getInstance();
            MiscTeleportManager.getInstance().saveBackLocation(teleportedPlayer);
            return manager.acceptTeleportRequest(teleportedPlayer) ? 1 : 0;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Command syntax error in /tpaccept", e);
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error executing /tpaccept command", e);
            return 0;
        }
    }

    private static int executeTpDeny(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            TeleportRequestManager manager = TeleportRequestManager.getInstance();
            return manager.denyTeleportRequest(player) ? 1 : 0;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Command syntax error in /tpdeny", e);
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error executing /tpdeny command", e);
            return 0;
        }
    }

    private static int executeTpCancel(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            TeleportRequestManager manager = TeleportRequestManager.getInstance();
            return manager.cancelTeleportRequest(player) ? 1 : 0;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Command syntax error in /tpcancel", e);
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error executing /tpcancel command", e);
            return 0;
        }
    }

    private static void registerTpCancelCommand(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(
            Commands.literal(literal)
                .requires(source -> {
                    if (source.getEntity() instanceof ServerPlayer player) {
                        boolean hasPerm = PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_CANCEL_COMPAT);
                        LOGGER.debug("[TPCANCEL] Checking permission {} for {}: {}", PERMISSION_CANCEL, player.getName().getString(), hasPerm);
                        return hasPerm;
                    }
                    return false;
                })
                .executes(context -> executeTpCancel(context))
        );
    }
}
