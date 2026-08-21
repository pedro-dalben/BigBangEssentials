package com.pedrodalben.bigbangessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.pedrodalben.bigbangessentials.economy.managers.PayToggleManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayToggleCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(PayToggleCommand.class);
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            net.minecraft.commands.Commands.literal("paytoggle")
                .requires(src -> src.hasPermission(2) || // Allow ops
                    (src.getPlayer() != null && hasPayTogglePermission(src.getPlayer().getUUID())))
                .executes(ctx -> execute(ctx))
        );
        
        // Register "pt" alias for paytoggle
        dispatcher.register(
            net.minecraft.commands.Commands.literal("pt")
                .requires(src -> src.hasPermission(2) || // Allow ops
                    (src.getPlayer() != null && hasPayTogglePermission(src.getPlayer().getUUID())))
                .executes(ctx -> execute(ctx))
        );
    }

    public static boolean hasPayTogglePermission(java.util.UUID uuid) {
        return com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(uuid, "bigbangessentials.economy.pay.toggle")
            || com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(uuid, "bigbangessentials.economy.paytoggle");
    }

    public static int execute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            java.util.UUID uuid = player.getUUID();
            
            LOGGER.debug("PayToggle command executed by player: {}", player.getName().getString());
            
            boolean current = PayToggleManager.getInstance().getPayToggle(uuid);
            boolean newState = !current;
            PayToggleManager.getInstance().setPayToggle(uuid, newState);
            
            LOGGER.debug("PayToggle state changed from {} to {} for player {}", current, newState, player.getName().getString());
            
            if (newState) {
                ctx.getSource().sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.paytoggle.enabled"), false);
            } else {
                ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.paytoggle.disabled"), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error executing paytoggle command", e);
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }
    }
}
