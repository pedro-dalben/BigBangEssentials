package com.pedrodalben.bigbangessentials.inventory;

import com.mojang.brigadier.CommandDispatcher;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.inventory.ClickType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class InventoryViewCommandsTest {
    @Test
    void enderChestCommandsOpenOwnChestWithoutAnArgument() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        InventoryViewCommands.register(dispatcher);

        assertNotNull(dispatcher.getRoot().getChild("enderchest").getCommand());
        assertNotNull(dispatcher.getRoot().getChild("ec").getCommand());
        assertNotNull(dispatcher.getRoot().getChild("enderchest").getChild("target").getCommand());
        assertNotNull(dispatcher.getRoot().getChild("ec").getChild("target").getCommand());
        assertNull(dispatcher.getRoot().getChild("enderchestedit").getCommand());
        assertNull(dispatcher.getRoot().getChild("ecedit").getCommand());
    }

    @Test
    void readOnlyEnderChestMenuIgnoresEveryInventoryClick() {
        InventoryViewCommands.ReadOnlyEnderChestMenu menu =
            mock(InventoryViewCommands.ReadOnlyEnderChestMenu.class, Mockito.CALLS_REAL_METHODS);

        menu.clicked(0, 0, ClickType.PICKUP, null);
        menu.clicked(0, 0, ClickType.SWAP, null);
        menu.clicked(27, 0, ClickType.QUICK_MOVE, null);
        menu.clicked(0, 0, ClickType.QUICK_CRAFT, null);
        menu.clicked(0, 0, ClickType.PICKUP_ALL, null);
    }

    @Test
    void enderChestCommandsRequireTheirExactCanonicalPermissions() {
        UUID playerId = UUID.randomUUID();

        try (MockedStatic<PermissionAPI> permissions = Mockito.mockStatic(PermissionAPI.class)) {
            permissions.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.enderchest")).thenReturn(true);
            permissions.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.enderchest.edit")).thenReturn(true);

            assertFalse(InventoryViewCommands.hasEnderChestPermission(playerId, false));
            assertFalse(InventoryViewCommands.hasEnderChestPermission(playerId, true));

            permissions.when(() -> PermissionAPI.hasExactPermission(playerId, "bigbangessentials.enderchest")).thenReturn(true);
            assertTrue(InventoryViewCommands.hasEnderChestPermission(playerId, false));
            assertFalse(InventoryViewCommands.hasEnderChestPermission(playerId, true));

            permissions.when(() -> PermissionAPI.hasExactPermission(playerId, "bigbangessentials.enderchest.edit")).thenReturn(true);
            assertTrue(InventoryViewCommands.hasEnderChestPermission(playerId, true));
        }
    }
}
