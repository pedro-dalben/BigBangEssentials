package com.pedrodalben.bigbangessentials.menu.integration.kits;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.kits.Kit;
import com.pedrodalben.bigbangessentials.kits.KitManager;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderRequest;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderValue;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
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

class KitMenuIntegrationTest {

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
    void kitMenuProviderExposesStatusesAndSummaryCounts() throws Exception {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenReturn(true);
        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenReturn(false);
        PermissionAPI.setExternalAdapter(adapter);

        injectKit(new Kit("readykit", "Ready Kit", "Kit pronto", List.of(ItemStack.EMPTY), 0L, null, -1, true));
        injectKit(new Kit("cooldownkit", "Cooldown Kit", "Kit em espera", List.of(ItemStack.EMPTY), 3_600_000L, null, -1, true));
        setCooldown(playerId, "cooldownkit", System.currentTimeMillis() + 3_600_000L);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);
        when(player.getName()).thenReturn(Component.literal("Player"));

        KitMenuDataProvider provider = new KitMenuDataProvider();
        MenuContext context = new MenuContext(playerId, "pt_BR", Map.of(), null, "test", "test", UUID.randomUUID());
        MenuDataResult result = provider.provide(player, context, new PaginationRequest(1, 10)).toCompletableFuture().join();

        assertEquals(2, result.totalItems());
        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().anyMatch(item -> "ready".equals(item.get("kit_status_key"))));
        assertTrue(result.items().stream().anyMatch(item -> "cooldown".equals(item.get("kit_status_key"))));

        Map<String, Object> summary = KitMenuSupport.buildSummaryPlaceholders(player);
        assertEquals("2", summary.get("kits_total"));
        assertEquals("1", summary.get("kits_available"));
        assertEquals("1", summary.get("kits_cooldown"));

        KitPlaceholderResolver resolver = new KitPlaceholderResolver();
        PlaceholderValue value = resolver.resolve(player, context, new PlaceholderRequest("kits:available", "available"))
            .toCompletableFuture().join();
        assertEquals("1", value.value());
    }

    @Test
    void summaryPlaceholdersSurvivePermissionAdapterFailures() throws Exception {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenThrow(new RuntimeException("permission backend unavailable"));
        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenReturn(false);
        PermissionAPI.setExternalAdapter(adapter);

        injectKit(new Kit("faulty", "Faulty Kit", "Kit with permission failure", List.of(ItemStack.EMPTY), 0L, "bigbangessentials.kits.faulty", -1, true));

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);
        when(player.getName()).thenReturn(Component.literal("Player"));

        Map<String, Object> summary = KitMenuSupport.buildSummaryPlaceholders(player);

        assertEquals("1", summary.get("kits_total"));
        assertEquals("0", summary.get("kits_available"));
        assertEquals("1", summary.get("kits_locked"));
        assertEquals("0", summary.get("kits_cooldown"));
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
