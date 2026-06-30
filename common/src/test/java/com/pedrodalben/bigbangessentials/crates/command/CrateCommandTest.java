package com.pedrodalben.bigbangessentials.crates.command;

import com.mojang.brigadier.CommandDispatcher;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.crates.command.config.CratePermissions;
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

class CrateCommandTest {

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
    void registersCreateSubcommandsOnBothRootAliases() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        CrateCommand.register(dispatcher);

        assertNotNull(dispatcher.getRoot().getChild("crate"));
        assertNotNull(dispatcher.getRoot().getChild("crates"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("create"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("edit"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("setname"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("setdesc"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("setopening"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("setlocation"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("massopen"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("claim"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setitems"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("additem"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("clearitems"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setcommands"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("addcommand"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("clearcommands"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("settype"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setlore"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setperm"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setvisible"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setmilestoneonly"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setbroadcast"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setbroadcastmsg"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setplayermsg"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setdisplayorder"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setgloballimit"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setplayerlimit"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setblockingperms"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("remove"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("duplicate"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity").getChild("setname"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity").getChild("setcolor"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity").getChild("setweight"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity").getChild("seticon"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity").getChild("setlore"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity").getChild("toggle"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity").getChild("setpriority"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("rarity").getChild("setdisplayorder"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("create"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("editor"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("setname"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("settype"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("setlore"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("setperm"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("setgivesound"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("settakesound"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("setgivecommands"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("addgivecommand"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("cleargivecommands"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("setcrates"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("key").getChild("removecrate"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone").getChild("setname"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone").getChild("setdescription"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone").getChild("setreward"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone").getChild("setopenings"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone").getChild("toggle"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone").getChild("setrepeatable"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone").getChild("setdisplayorder"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("milestone").getChild("remove"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("location").getChild("list"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("location").getChild("remove"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("location").getChild("settemplate"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("location").getChild("setoffsety"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("location").getChild("togglehologram"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("location").getChild("toggleparticle"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("location").getChild("toggle"));
        assertNotNull(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("create"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("create"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("edit"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("massopen"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("claim"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setitems"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("additem"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("clearitems"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setcommands"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("addcommand"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("clearcommands"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("settype"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setlore"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setperm"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setvisible"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setmilestoneonly"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setbroadcast"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setbroadcastmsg"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setplayermsg"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setdisplayorder"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setgloballimit"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setplayerlimit"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("setblockingperms"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("remove"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("duplicate"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity").getChild("setname"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity").getChild("setcolor"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity").getChild("setweight"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity").getChild("seticon"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity").getChild("setlore"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity").getChild("toggle"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity").getChild("setpriority"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("rarity").getChild("setdisplayorder"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("editor"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("reward").getChild("create"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("create"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("setlore"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("setperm"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("setgivesound"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("settakesound"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("setgivecommands"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("addgivecommand"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("cleargivecommands"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("setcrates"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("key").getChild("removecrate"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone").getChild("setname"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone").getChild("setdescription"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone").getChild("setreward"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone").getChild("setopenings"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone").getChild("toggle"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone").getChild("setrepeatable"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone").getChild("setdisplayorder"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("milestone").getChild("remove"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("location").getChild("list"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("location").getChild("remove"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("location").getChild("settemplate"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("location").getChild("setoffsety"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("location").getChild("togglehologram"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("location").getChild("toggleparticle"));
        assertNotNull(dispatcher.getRoot().getChild("crates").getChild("location").getChild("toggle"));
    }

    @Test
    void createSubcommandsRespectPermissionChecks() {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenAnswer(invocation -> {
            String permission = invocation.getArgument(1, String.class);
            return CratePermissions.MANAGE.equals(permission) || CratePermissions.EDITOR.equals(permission);
        });
        PermissionAPI.setExternalAdapter(adapter);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CrateCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertTrue(dispatcher.getRoot().getChild("crate").getChild("create").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("edit").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("key").getChild("create").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("key").getChild("editor").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("create").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setitems").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("settype").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("remove").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("rarity").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crates").getChild("rarity").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("key").getChild("setlore").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crates").getChild("key").getChild("removecrate").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("milestone").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crates").getChild("milestone").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("location").getChild("settemplate").canUse(source));
    }

    @Test
    void createSubcommandsStayHiddenWithoutMatchingPermissions() {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenReturn(false);
        PermissionAPI.setExternalAdapter(adapter);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CrateCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertFalse(dispatcher.getRoot().getChild("crate").getChild("create").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("edit").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("key").getChild("create").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("key").getChild("editor").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("create").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("setitems").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("settype").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("reward").getChild("remove").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("rarity").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crates").getChild("rarity").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("key").getChild("setlore").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crates").getChild("key").getChild("removecrate").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("milestone").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crates").getChild("milestone").canUse(source));
        assertFalse(dispatcher.getRoot().getChild("crate").getChild("location").getChild("settemplate").canUse(source));
    }
}
