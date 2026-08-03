package com.pedrodalben.bigbangessentials.api.permissions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.pedrodalben.bigbangessentials.util.commands.PlayerStateCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerStateCommandsPermissionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
        }
    }

    @Test
    void flyAndSpeedOtherTargetsRequireExplicitOthersPermission() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(player.getUUID()).thenReturn(playerId);
        when(source.getPlayer()).thenReturn(player);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PlayerStateCommands.register(dispatcher);

        CommandNode<CommandSourceStack> fly = dispatcher.getRoot().getChild("fly");
        CommandNode<CommandSourceStack> flyTarget = fly.getChild("target");
        CommandNode<CommandSourceStack> speed = dispatcher.getRoot().getChild("speed");
        CommandNode<CommandSourceStack> speedTarget = speed.getChild("speed").getChild("target");
        CommandNode<CommandSourceStack> walkTarget = speed.getChild("walk").getChild("speed").getChild("target");
        CommandNode<CommandSourceStack> flySpeedTarget = speed.getChild("fly").getChild("speed").getChild("target");

        try (MockedStatic<PermissionAPI> permissions = Mockito.mockStatic(PermissionAPI.class)) {
            permissions.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.fly")).thenReturn(true);
            permissions.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.speed")).thenReturn(true);

            assertTrue(fly.getRequirement().test(source));
            assertFalse(flyTarget.getRequirement().test(source));
            assertTrue(speed.getRequirement().test(source));
            assertFalse(speedTarget.getRequirement().test(source));
            assertFalse(walkTarget.getRequirement().test(source));
            assertFalse(flySpeedTarget.getRequirement().test(source));

            permissions.when(() -> PermissionAPI.hasTargetPermission(playerId, "bigbangessentials.fly.others"))
                .thenReturn(true);
            permissions.when(() -> PermissionAPI.hasTargetPermission(playerId, "bigbangessentials.speed.others"))
                .thenReturn(true);

            assertTrue(flyTarget.getRequirement().test(source));
            assertTrue(speedTarget.getRequirement().test(source));
            assertTrue(walkTarget.getRequirement().test(source));
            assertTrue(flySpeedTarget.getRequirement().test(source));
        }
    }
}
