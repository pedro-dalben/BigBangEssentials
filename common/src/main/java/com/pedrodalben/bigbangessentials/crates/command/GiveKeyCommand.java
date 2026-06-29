package com.pedrodalben.bigbangessentials.crates.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.crates.command.config.CrateMessages;
import com.pedrodalben.bigbangessentials.crates.command.config.CratePermissions;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class GiveKeyCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(GiveKeyCommand.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("givekey")
            .requires(source -> {
                if (source.hasPermission(4)) return true;
                try {
                    ServerPlayer player = source.getPlayer();
                    if (player != null) {
                        return com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                            player.getUUID(), CratePermissions.KEY_GIVE);
                    }
                } catch (Exception ignored) {}
                return false;
            })
            .then(Commands.argument("key", StringArgumentType.word())
                .then(Commands.argument("player", EntityArgument.players())
                    .executes(ctx -> execute(ctx, 1))
                    .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                    )
                )
            )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!CrateService.getInstance().keyExists(keyId)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "player");
            for (ServerPlayer target : targets) {
                CrateKeyService.getInstance().giveVirtualKey(
                    target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND,
                    "givekey:" + target.getUUID() + ":" + keyId + ":" + amount + ":" + System.currentTimeMillis()
                );

                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                    String.format(CrateMessages.GIVE_SUCCESS, amount, keyId, target.getName().getString())), true);

                target.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    String.format(CrateMessages.GIVE_RECEIVE, amount, keyId)));
            }
        } catch (Exception e) {
            LOGGER.error("Error executing givekey command", e);
            source.sendFailure(net.minecraft.network.chat.Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }

        return 1;
    }
}
