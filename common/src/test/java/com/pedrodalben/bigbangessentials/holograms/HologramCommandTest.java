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

        assertNotNull(dispatcher.getRoot().getChild("bbholo"));
        assertNotNull(dispatcher.getRoot().getChild("hologram"));
        assertNotNull(dispatcher.getRoot().getChild("holograms"));
        assertNotNull(dispatcher.getRoot().getChild("holo"));
        assertNotNull(dispatcher.getRoot().getChild("bbholo").getChild("list"));
        assertNotNull(dispatcher.getRoot().getChild("bbholo").getChild("info"));
        assertNotNull(dispatcher.getRoot().getChild("bbholo").getChild("create"));
        assertNotNull(dispatcher.getRoot().getChild("bbholo").getChild("delete"));
        assertNotNull(dispatcher.getRoot().getChild("bbholo").getChild("movehere"));
        assertNotNull(dispatcher.getRoot().getChild("bbholo").getChild("stats"));
        assertNotNull(dispatcher.getRoot().getChild("bbholo").getChild("reconcile"));
    }

    @Test
    void subcommandsRespectGranularPermissions() {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenAnswer(invocation -> switch (invocation.getArgument(1, String.class)) {
            case HologramPermissions.CREATE, HologramPermissions.LINES, HologramPermissions.RELOAD -> true;
            default -> false;
        });
        PermissionAPI.setExternalAdapter(adapter);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        HologramCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertTrue(dispatcher.getRoot().getChild("bbholo").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("create").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("line").getChild("add").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("reload").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("bbholo").getChild("list").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("bbholo").getChild("delete").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("bbholo").getChild("reconcile").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("bbholo").getChild("stats").canUse(source));
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

        assertTrue(dispatcher.getRoot().getChild("bbholo").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("list").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("info").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("create").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("delete").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("reconcile").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("bbholo").getChild("stats").canUse(source));
    }
}
