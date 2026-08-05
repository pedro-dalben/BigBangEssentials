package com.pedrodalben.bigbangessentials.economy.magnata;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class MagnataCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("magnata")
                .executes(MagnataCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MagnataManager manager = MagnataManager.getInstance();

        if (!manager.isEnabled()) {
            src.sendFailure(ChatComponentUtil.parseColorCodes(manager.getModuleDisabledMessage()));
            return 0;
        }

        String msg = manager.getMagnataInfoMessage();
        src.sendSuccess(() -> ChatComponentUtil.parseColorCodes(msg), false);
        return 1;
    }
}
