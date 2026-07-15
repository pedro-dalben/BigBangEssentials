package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakePlayerIntegration;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakePlayerSnapshot;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;

import java.util.Optional;

public class PingCommand {

    private static final SuggestionProvider<CommandSourceStack> FAKE_PLAYER_SUGGESTIONS = (ctx, builder) -> {
        for (FakePlayerSnapshot fake : FakePlayerIntegration.getInstance().getAllFakePlayers()) {
            builder.suggest(fake.username());
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("ping")) return;

        dispatcher.register(
            Commands.literal("ping")
                .requires(source -> PermissionValidator.validateAnyPermission(
                    source,
                    "bigbangessentials.ping",
                    "bigbangessentials.ping.others"
                ).hasPermission())
                .then(Commands.argument("player", EntityArgument.player())
                    .requires(source -> PermissionValidator.validatePermission(
                        source, "bigbangessentials.ping.others").hasPermission())
                    .executes(ctx -> {
                        PermissionValidator.PermissionResult permResult =
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.ping.others");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }

                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        ServerPlayer requester = CommandSourceHelper.getPlayer(ctx.getSource());
                        showPingInfo(ctx.getSource(), target, requester);
                        return 1;
                    })
                )
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(FAKE_PLAYER_SUGGESTIONS)
                    .requires(source -> PermissionValidator.validatePermission(
                        source, "bigbangessentials.ping.others").hasPermission())
                    .executes(ctx -> {
                        PermissionValidator.PermissionResult permResult =
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.ping.others");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }

                        String targetName = StringArgumentType.getString(ctx, "player");
                        ServerPlayer requester = CommandSourceHelper.getPlayer(ctx.getSource());

                        ServerPlayer realTarget = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
                        if (realTarget != null) {
                            return 0;
                        }

                        Optional<FakePlayerSnapshot> fakeOpt = FakePlayerIntegration.getInstance().findActiveFakePlayer(targetName);
                        if (fakeOpt.isPresent()) {
                            showFakePingInfo(ctx.getSource(), fakeOpt.get(), requester);
                            return 1;
                        }

                        ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.ping.other",
                            targetName, "?", "Unknown"));
                        return 0;
                    })
                )
                .executes(ctx -> {
                    PermissionValidator.PermissionResult permResult =
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.ping");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }

                    ServerPlayer player = permResult.getPlayer();
                    showPingInfo(ctx.getSource(), player, player);
                    return 1;
                })
        );
    }

    private static void showPingInfo(CommandSourceStack source, ServerPlayer target, ServerPlayer requester) {
        int ping = target.connection.latency();
        String qualityDescription = getPingQuality(ping);

        if (requester != null && target == requester) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.ping.self", ping, qualityDescription), false);
        } else {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.ping.other",
                target.getName().getString(), ping, qualityDescription), false);
        }

        if (ping > 300) {
            source.sendSuccess(() -> MessageUtil.warning("commands.bigbangessentials.ping.high_warning"), false);
        } else if (ping > 150) {
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.ping.moderate_info"), false);
        } else if (ping < 50) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.ping.excellent_info"), false);
        }
    }

    private static void showFakePingInfo(CommandSourceStack source, FakePlayerSnapshot fake, ServerPlayer requester) {
        int ping = fake.ping();
        String qualityDescription = getPingQuality(ping);

        if (requester != null && requester.getName().getString().equalsIgnoreCase(fake.username())) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.ping.self", ping, qualityDescription), false);
        } else {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.fakeplayer.ping",
                fake.username(), ping, qualityDescription), false);
        }
    }

    private static String getPingQuality(int ping) {
        if (ping < 30) return "Excellent";
        if (ping < 60) return "Very Good";
        if (ping < 100) return "Good";
        if (ping < 150) return "Fair";
        if (ping < 250) return "Poor";
        if (ping < 400) return "Very Poor";
        return "Terrible";
    }
}
