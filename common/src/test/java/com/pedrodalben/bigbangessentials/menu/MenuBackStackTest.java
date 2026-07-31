package com.pedrodalben.bigbangessentials.menu;

import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.builtin.BackMenuAction;
import com.pedrodalben.bigbangessentials.menu.api.MenuOpenResult;
import com.pedrodalben.bigbangessentials.menu.api.MenuService;
import com.pedrodalben.bigbangessentials.menu.event.MenuEventListener;
import com.pedrodalben.bigbangessentials.menu.model.MenuClickType;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuFlags;
import com.pedrodalben.bigbangessentials.menu.model.MenuPageDefinition;
import com.pedrodalben.bigbangessentials.menu.neoforge.NeoForgeMenuRenderer;
import com.pedrodalben.bigbangessentials.menu.session.MenuBackStackEntry;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.session.MenuSessionStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuBackStackTest {
    @BeforeAll
    static void bootstrap() {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
            // Other Minecraft-focused test classes may have initialized Bootstrap already.
        }
    }

    @Test
    void backPassesSavedContextAndPaginationPageToService() throws Exception {
        ServerPlayer player = mock(ServerPlayer.class);
        MenuSession session = new MenuSession();
        session.setBackStack(new ArrayDeque<>());
        MenuContext saved = new MenuContext(UUID.randomUUID(), "pt_BR", Map.of("species", "Pikachu"), null, "pokemarket", "browse", UUID.randomUUID());
        MenuBackStackEntry entry = new MenuBackStackEntry("browse", "main", 4, saved);
        session.getBackStack().push(entry);
        MenuService service = mock(MenuService.class);
        when(service.openMenuFromBack(eq(player), eq("browse"), eq("main"), eq(4), eq(entry.context()), any()))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(new MenuOpenResult(true, null)));

        java.lang.reflect.Field field = MenuSystem.class.getDeclaredField("menuService");
        field.setAccessible(true);
        Object previous = field.get(MenuSystem.getInstance());
        field.set(MenuSystem.getInstance(), service);
        try {
            ActionContext context = new ActionContext(player, session, null, null, null, MenuClickType.LEFT, new MenuContext(UUID.randomUUID(), "pt_BR", Map.of("listing_id", "current"), null, "test", "current", UUID.randomUUID()), Map.of());
            new BackMenuAction().execute(context).toCompletableFuture().join();
            verify(service).openMenuFromBack(eq(player), eq("browse"), eq("main"), eq(4), eq(entry.context()), any());
        } finally {
            field.set(MenuSystem.getInstance(), previous);
        }
    }

    @Test
    void backWithoutHistoryClosesInventory() {
        ServerPlayer player = mock(ServerPlayer.class);
        MenuSession session = new MenuSession();
        session.setBackStack(new ArrayDeque<>());
        ActionContext context = new ActionContext(player, session, null, null, null, MenuClickType.LEFT, null, Map.of());

        new BackMenuAction().execute(context).toCompletableFuture().join();

        verify(player).closeContainer();
    }

    @Test
    void stackEntryCopiesContextMaps() {
        Map<String, Object> values = new HashMap<>();
        values.put("species", "Eevee");
        MenuContext context = new MenuContext(UUID.randomUUID(), "pt_BR", values, null, "test", "test", UUID.randomUUID());
        MenuBackStackEntry entry = new MenuBackStackEntry("menu", "main", 2, context);

        values.put("species", "Mew");
        assertEquals("Eevee", entry.context().values().get("species"));
        assertThrows(UnsupportedOperationException.class, () -> entry.context().values().put("x", "y"));
    }
}
