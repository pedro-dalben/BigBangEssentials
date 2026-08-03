package com.pedrodalben.bigbangessentials.util;

import com.mojang.authlib.GameProfile;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionValidatorExactTest {
    @Test
    void targetValidationDoesNotInheritBasePermission() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(player.getUUID()).thenReturn(playerId);
        when(player.getGameProfile()).thenReturn(new GameProfile(playerId, "tester"));
        when(source.getPlayer()).thenReturn(player);

        try (MockedStatic<PermissionAPI> permissions = Mockito.mockStatic(PermissionAPI.class)) {
            permissions.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.ping.others"))
                .thenReturn(true);
            permissions.when(() -> PermissionAPI.hasTargetPermission(playerId, "bigbangessentials.ping.others"))
                .thenReturn(false);

            assertTrue(PermissionValidator.validatePermission(source, "bigbangessentials.ping.others").hasPermission());
            assertFalse(PermissionValidator.validateExactPermission(source, "bigbangessentials.ping.others").hasPermission());
        }
    }
}
