package com.pedrodalben.bigbangessentials.economy.gems;

import com.mojang.brigadier.CommandDispatcher;
import com.pedrodalben.bigbangessentials.adminshop.AdminShopCommand;
import com.pedrodalben.bigbangessentials.economy.gems.command.GemsCommand;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GemCommandAuthorizationTest {

    @BeforeEach
    void setUp() {
        GemsManager.getInstance().reload();
    }

    @Test
    void testCommandRegistrationDoesNotThrow() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        // Try registering
        GemsCommand.register(dispatcher);

        // Verify root node is present
        assertNotNull(dispatcher.getRoot().getChild("gems"));
        assertNotNull(dispatcher.getRoot().getChild("gemas"));

        // Verify some subcommands
        assertNotNull(dispatcher.getRoot().getChild("gems").getChild("balance"));
        assertNotNull(dispatcher.getRoot().getChild("gems").getChild("history"));
        assertNotNull(dispatcher.getRoot().getChild("gems").getChild("admin"));
    }

    @Test
    void gemasExecutesBalanceAfterAdminShopRegistersItsShopSubcommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        GemsCommand.register(dispatcher);
        AdminShopCommand.register(dispatcher);

        var parsed = dispatcher.parse("gemas", mock(CommandSourceStack.class));

        assertTrue(parsed.getExceptions().isEmpty());
        assertNotNull(parsed.getContext().getCommand());
        assertNotNull(dispatcher.getRoot().getChild("gemas").getChild("shop"));
    }
}
