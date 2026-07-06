package com.pedrodalben.bigbangessentials.holograms;

import com.mojang.brigadier.CommandDispatcher;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.holograms.command.HologramCommand;
import com.pedrodalben.bigbangessentials.holograms.command.HologramPermissions;
import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HologramCommandTest {
    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
        }
    }

    @BeforeEach
    void setUp() {
        PermissionAPI.setExternalAdapter(null);
    }

    @AfterEach
    void tearDown() {
        PermissionAPI.setExternalAdapter(null);
    }

    @Test
    void registersMainAliasesAndCleanupCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        HologramCommand.register(dispatcher);

        assertNotNull(dispatcher.getRoot().getChild("hologram"));
        assertNotNull(dispatcher.getRoot().getChild("holograms"));
        assertNotNull(dispatcher.getRoot().getChild("hologram").getChild("list"));
        assertNotNull(dispatcher.getRoot().getChild("hologram").getChild("inspect"));
        assertNotNull(dispatcher.getRoot().getChild("hologram").getChild("create"));
        assertNotNull(dispatcher.getRoot().getChild("hologram").getChild("remove"));
        assertNotNull(dispatcher.getRoot().getChild("hologram").getChild("move"));
        assertNotNull(dispatcher.getRoot().getChild("hologram").getChild("stats"));
        assertNotNull(dispatcher.getRoot().getChild("hologram").getChild("cleanup").getChild("legacy"));
        assertNotNull(dispatcher.getRoot().getChild("holograms").getChild("cleanup").getChild("legacy"));
    }

    @Test
    void subcommandsRespectGranularPermissions() {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenAnswer(invocation -> switch (invocation.getArgument(1, String.class)) {
            case HologramPermissions.CREATE, HologramPermissions.EDIT, HologramPermissions.RELOAD -> true;
            default -> false;
        });
        PermissionAPI.setExternalAdapter(adapter);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        HologramCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertTrue(dispatcher.getRoot().getChild("hologram").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("create").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("setline").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("reload").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("hologram").getChild("list").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("hologram").getChild("remove").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("hologram").getChild("cleanup").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("hologram").getChild("stats").canUse(source));
    }

    @Test
    void adminPermissionUnlocksEntireCommandTree() {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenAnswer(invocation ->
            HologramPermissions.ADMIN.equals(invocation.getArgument(1, String.class)));
        PermissionAPI.setExternalAdapter(adapter);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        HologramCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertTrue(dispatcher.getRoot().getChild("hologram").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("list").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("inspect").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("create").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("remove").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("cleanup").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("hologram").getChild("stats").canUse(source));
    }
}
