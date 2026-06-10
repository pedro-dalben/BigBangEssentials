package com.pedrodalben.bigbangessentials.kits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.kits.KitManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * /kitreset <kit> [player]
 *
 * Port of EssentialsX Commandkitreset:
 *  - /kitreset <kit>             → reset own kit cooldown
 *  - /kitreset <kit> <player>    → reset another player's cooldown (bigbangessentials.kitreset.others)
 *  - Console support
 */
public class KitResetCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(KitResetCommand.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isKitSystemEnabled()) return;

        dispatcher.register(Commands.literal("kitreset")
            .requires(src -> {
                var p = src.getPlayer();
                return p == null
                    || PermissionAPI.hasAnyPermission(
                        p.getUUID(),
                        "bigbangessentials.kitreset",
                        "bigbangessentials.kits.admin.reset",
                        "bigbangessentials.kit.reset",
                        "bigbangessentials.kits.admin"
                    );
            })
            // /kitreset <kitname>
            .then(Commands.argument("kitname", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                    KitManager.getInstance().getKitNames(), builder))
                // /kitreset <kitname>  (self)
                .executes(ctx -> executeReset(ctx,
                    StringArgumentType.getString(ctx, "kitname"), null))
                // /kitreset <kitname> <player>  (others)
                .then(Commands.argument("target", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getPlayerNames(), builder))
                    .requires(src -> {
                        var p = src.getPlayer();
                        return p == null
                            || PermissionAPI.hasPermission(p.getUUID(), "bigbangessentials.kitreset.others");
                    })
                    .executes(ctx -> executeReset(ctx,
                        StringArgumentType.getString(ctx, "kitname"),
                        StringArgumentType.getString(ctx, "target")))
                )
            )
        );
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx,
                                    String kitName, String targetName) {
        var source = ctx.getSource();
        var sender = source.getPlayer();

        // Verify kit exists
        if (KitManager.getInstance().getKit(kitName) == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.not_found", kitName));
            return 0;
        }

        // Resolve target
        ServerPlayer target;
        if (targetName != null) {
            target = source.getServer().getPlayerList().getPlayerByName(targetName);
            if (target == null) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_not_found", targetName));
                return 0;
            }
        } else {
            if (sender == null) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.console_needs_target"));
                return 0;
            }
            target = sender;
        }

        // Reset cooldown (Essentials: target.setKitTimestamp(kitName, 0))
        KitManager.getInstance().resetCooldown(target.getUUID(), kitName);

        final String tName = target.getName().getString();
        if (sender != null && target.getUUID().equals(sender.getUUID())) {
            // Self reset
            source.sendSuccess(() -> MessageUtil.success(
                "commands.bigbangessentials.kits.reset_self", kitName), false);
        } else {
            // Other reset (Essentials: kitResetOther)
            source.sendSuccess(() -> MessageUtil.success(
                "commands.bigbangessentials.kits.reset_other", kitName, tName), true);
            target.sendSystemMessage(MessageUtil.info(
                "commands.bigbangessentials.kits.reset_notify", kitName));
        }

        LOGGER.info("{} reset kit cooldown '{}' for {}",
            sender != null ? sender.getName().getString() : "Console",
            kitName, tName);
        return 1;
    }
}
