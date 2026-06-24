package com.pedrodalben.bigbangessentials.kits;

import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.kits.command.KitCommand;
import com.pedrodalben.bigbangessentials.kits.command.KitResetCommand;
import com.pedrodalben.bigbangessentials.kits.command.ListKitsCommand;
import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import net.minecraft.commands.Commands;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitCommandTest {

    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @BeforeEach
    void setUp() throws Exception {
        clearKitManagerState();
        PermissionAPI.setExternalAdapter(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        PermissionAPI.setExternalAdapter(null);
        clearKitManagerState();
    }

    @Test
    void kitCommandIsVisibleWithPerKitPermissionOnly() throws Exception {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenAnswer(invocation -> {
            String permission = invocation.getArgument(1, String.class);
            return "bigbangessentials.kits.iniciante".equals(permission);
        });
        PermissionAPI.setExternalAdapter(adapter);

        injectKit(new Kit(
            "iniciante",
            "Iniciante",
            "Kit inicial",
            List.of(ItemStack.EMPTY),
            0L,
            "bigbangessentials.kits.iniciante",
            -1,
            true
        ));

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        KitCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertNotNull(dispatcher.getRoot().getChild("kit"));
        assertNotNull(dispatcher.getRoot().getChild("kits"));
        assertTrue(dispatcher.getRoot().getChild("kit").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("kits").canUse(source));
    }

    @Test
    void kitCommandIsVisibleEvenWithoutPermissions() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        KitCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertTrue(dispatcher.getRoot().getChild("kit").canUse(source));
        assertTrue(dispatcher.getRoot().getChild("kits").canUse(source));
    }

    @Test
    void kitCommandReplacesPreexistingRootRequirement() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("kit").requires(source -> false));

        KitCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertTrue(dispatcher.getRoot().getChild("kit").canUse(source));
        assertNotNull(dispatcher.getRoot().getChild("kit").getChild("kitname"));
    }

    @Test
    void kitAliasesCanBeRegisteredIndependently() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        KitCommand.register(dispatcher, false, true);

        assertNull(dispatcher.getRoot().getChild("kit"));
        assertNotNull(dispatcher.getRoot().getChild("kits"));
        assertNotNull(dispatcher.getRoot().getChild("kits").getChild("kitname"));
    }

    @Test
    void kitReadsCooldownMillisFromJson() {
        var json = JsonParser.parseString("""
            {
              "name": "iniciante",
              "cooldownMillis": 86400000,
              "items": []
            }
            """).getAsJsonObject();

        Kit kit = Kit.fromJson(json);

        assertEquals(86400000L, kit.getCooldownMillis());
        assertEquals(-1, kit.getMaxUses());
    }

    @Test
    void kitReadsDelayAsSecondsAndNegativeDelayAsOneTimeUse() {
        var delayJson = JsonParser.parseString("""
            {
              "name": "delaykit",
              "delay": 3600,
              "items": []
            }
            """).getAsJsonObject();
        var oneTimeJson = JsonParser.parseString("""
            {
              "name": "onetime",
              "cooldown": -1,
              "items": []
            }
            """).getAsJsonObject();

        assertEquals(3_600_000L, Kit.fromJson(delayJson).getCooldownMillis());
        assertEquals(1, Kit.fromJson(oneTimeJson).getMaxUses());
    }

    @Test
    void activeCooldownBlocksKitUseWithoutExactBypass() throws Exception {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenReturn(true);
        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenReturn(false);
        PermissionAPI.setExternalAdapter(adapter);

        injectKit(new Kit(
            "cooldownkit",
            "Cooldown Kit",
            "",
            List.of(ItemStack.EMPTY),
            86_400_000L,
            null,
            -1,
            true
        ));
        setCooldown(playerId, "cooldownkit", System.currentTimeMillis() + 60_000L);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        KitManager.KitUsageResult result = KitManager.getInstance().canUseKit(player, "cooldownkit");

        assertFalse(result.isAllowed());
        assertTrue(result.getMessage().contains("cooldown"));
    }

    @Test
    void kitResetRequiresExplicitResetPermission() throws Exception {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenReturn(true);
        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenReturn(false);
        PermissionAPI.setExternalAdapter(adapter);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        KitResetCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertFalse(dispatcher.getRoot().getChild("kitreset").canUse(source));
    }

    @Test
    void kitResetIsVisibleWithExplicitResetPermission() throws Exception {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenAnswer(invocation ->
            "bigbangessentials.kitreset".equals(invocation.getArgument(1, String.class)));
        PermissionAPI.setExternalAdapter(adapter);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        KitResetCommand.register(dispatcher);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);

        assertTrue(dispatcher.getRoot().getChild("kitreset").canUse(source));
    }

    @Test
    void listKitsCommandKeepsOnlyAdminLiteral() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        ListKitsCommand.register(dispatcher);

        assertNotNull(dispatcher.getRoot().getChild("listkits"));
        assertNull(dispatcher.getRoot().getChild("kits"));
    }

    @SuppressWarnings("unchecked")
    private static void injectKit(Kit kit) throws Exception {
        KitManager manager = KitManager.getInstance();

        Field kitsField = KitManager.class.getDeclaredField("kits");
        kitsField.setAccessible(true);
        Map<String, Kit> kits = (Map<String, Kit>) kitsField.get(manager);
        kits.put(kit.getName(), kit);

        Field initializedField = KitManager.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(manager, true);
    }

    @SuppressWarnings("unchecked")
    private static void setCooldown(UUID playerId, String kitName, long cooldownEnd) throws Exception {
        KitManager manager = KitManager.getInstance();

        Field cooldownsField = KitManager.class.getDeclaredField("playerCooldowns");
        cooldownsField.setAccessible(true);
        Map<UUID, Map<String, Long>> cooldowns = (Map<UUID, Map<String, Long>>) cooldownsField.get(manager);
        cooldowns.computeIfAbsent(playerId, ignored -> new java.util.concurrent.ConcurrentHashMap<>())
            .put(kitName.toLowerCase(), cooldownEnd);
    }

    @SuppressWarnings("unchecked")
    private static void clearKitManagerState() throws Exception {
        KitManager manager = KitManager.getInstance();

        for (String fieldName : List.of("kits", "playerCooldowns", "playerUsages")) {
            Field field = KitManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            ((Map<?, ?>) field.get(manager)).clear();
        }

        Field initializedField = KitManager.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(manager, false);
    }
}
