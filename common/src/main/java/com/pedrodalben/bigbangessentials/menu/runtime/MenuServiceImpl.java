package com.pedrodalben.bigbangessentials.menu.runtime;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.api.*;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuCloseReason;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.session.MenuSessionStore;
import com.pedrodalben.bigbangessentials.menu.event.MenuEventListener;
import com.pedrodalben.bigbangessentials.menu.event.MenuOpenDecision;
import com.pedrodalben.bigbangessentials.menu.neoforge.NeoForgeMenuRenderer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;
import java.util.function.Supplier;
import java.util.ArrayDeque;
import java.time.Instant;

public class MenuServiceImpl implements MenuService {

    private final MenuRegistry menuRegistry;
    private final MenuSessionStore sessionStore;
    private final MenuEventListener eventListener;
    private final NeoForgeMenuRenderer renderer;

    public MenuServiceImpl(MenuRegistry menuRegistry, MenuSessionStore sessionStore, MenuEventListener eventListener, NeoForgeMenuRenderer renderer) {
        this.menuRegistry = menuRegistry;
        this.sessionStore = sessionStore;
        this.eventListener = eventListener;
        this.renderer = renderer;
    }

    @Override
    public MenuCreateResult createMenu(MenuDefinition definition) {
        menuRegistry.registerMenu(definition);
        return new MenuCreateResult(true, null);
    }

    @Override
    public Optional<MenuDefinition> getMenu(String menuId) {
        return menuRegistry.getMenu(menuId);
    }

    @Override
    public Collection<MenuDefinition> listMenus() {
        return menuRegistry.getMenus();
    }

    @Override
    public MenuDeleteResult deleteMenu(String menuId) {
        menuRegistry.unregisterMenu(menuId);
        return new MenuDeleteResult(true, null);
    }

    @Override
    public MenuUpdateResult updateMenu(String menuId, UnaryOperator<MenuDefinition> updater) {
        Optional<MenuDefinition> menuOpt = menuRegistry.getMenu(menuId);
        if (menuOpt.isPresent()) {
            MenuDefinition newDef = updater.apply(menuOpt.get());
            menuRegistry.registerMenu(newDef);
            return new MenuUpdateResult(true, null);
        }
        return new MenuUpdateResult(false, "Menu not found");
    }

    @Override
    public CompletionStage<MenuOpenResult> openMenu(ServerPlayer player, String menuId, MenuContext context) {
        return openMenu(player, menuId, null, context);
    }

    @Override
    public CompletionStage<MenuOpenResult> openMenu(ServerPlayer player, String menuId, String pageId, MenuContext context) {
        return runOnServerThread(player, () -> openMenuSync(player, menuId, pageId, context));
    }

    @Override
    public MenuCloseResult closeMenu(ServerPlayer player, String menuId, MenuCloseReason reason) {
        return runBlockingOnServerThread(player, () -> closeMenuSync(player, menuId, reason));
    }

    @Override
    public MenuRefreshResult refreshMenu(ServerPlayer player, String menuId) {
        return refreshCurrentPage(player);
    }

    @Override
    public MenuRefreshResult refreshCurrentPage(ServerPlayer player) {
        return runBlockingOnServerThread(player, () -> refreshCurrentPageSync(player));
    }

    @Override
    public MenuRefreshResult refreshItem(ServerPlayer player, String menuId, String pageId, String itemId) {
        return refreshCurrentPage(player); // Simplification for now
    }

    @Override
    public PageChangeResult goToPage(ServerPlayer player, String menuId, String pageId) {
        return runBlockingOnServerThread(player, () -> goToPageSync(player, pageId));
    }

    @Override
    public PageChangeResult nextPage(ServerPlayer player, String menuId) {
        Optional<MenuSession> sessionOpt = sessionStore.getByPlayerId(player.getUUID());
        if (sessionOpt.isPresent()) {
            MenuSession session = sessionOpt.get();
            session.setCurrentPageIndex(session.getCurrentPageIndex() + 1);
            return refreshCurrentPage(player).success() ? new PageChangeResult(true, null) : PageChangeResult.NOT_FOUND;
        }
        return PageChangeResult.NOT_FOUND;
    }

    @Override
    public PageChangeResult previousPage(ServerPlayer player, String menuId) {
         Optional<MenuSession> sessionOpt = sessionStore.getByPlayerId(player.getUUID());
        if (sessionOpt.isPresent()) {
            MenuSession session = sessionOpt.get();
            if (session.getCurrentPageIndex() > 1) {
                session.setCurrentPageIndex(session.getCurrentPageIndex() - 1);
                return refreshCurrentPage(player).success() ? new PageChangeResult(true, null) : PageChangeResult.NOT_FOUND;
            }
        }
        return PageChangeResult.NOT_FOUND;
    }

    @Override
    public Optional<MenuSession> getCurrentSession(UUID playerId) {
        return sessionStore.getByPlayerId(playerId);
    }

    @Override
    public Optional<MenuSession> getSession(UUID sessionId) {
        return sessionStore.getById(sessionId);
    }

    @Override
    public void refreshSessionsUsingSource(String sourceId) {
        net.minecraft.server.MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
        if (server == null) return;
        
        server.submit(() -> {
            for (MenuSession session : sessionStore.getAllSessions()) {
                if (session.isClosed()) continue;
                Optional<MenuDefinition> menuOpt = menuRegistry.getMenu(session.getMenuId());
                if (menuOpt.isPresent()) {
                    MenuDefinition menu = menuOpt.get();
                    if (menu.pagination() != null && menu.pagination().enabled() && sourceId.equals(menu.pagination().source())) {
                        ServerPlayer player = server.getPlayerList().getPlayer(session.getPlayerId());
                        if (player != null) {
                            session.setRevision(session.getRevision() + 1);
                            renderer.renderPage(player, session, menu, session.getContext() != null ? session.getContext() : defaultContext(player));
                        }
                    }
                }
            }
        });
    }

    private MenuOpenResult openMenuSync(ServerPlayer player, String menuId, String pageId, MenuContext context) {
        Optional<MenuDefinition> menuOpt = menuRegistry.getMenu(menuId);
        if (menuOpt.isEmpty()) {
            return MenuOpenResult.NOT_FOUND;
        }

        MenuDefinition menu = menuOpt.get();
        MenuOpenDecision decision = eventListener.onMenuOpen(player, menuId, context, menu);
        if (!decision.allowed()) {
            if (decision.redirectMenuId() != null) {
                return openMenuSync(player, decision.redirectMenuId(), null, context);
            }
            return new MenuOpenResult(false, decision.denyReason() != null ? decision.denyReason() : "Abertura negada.");
        }

        sessionStore.getByPlayerId(player.getUUID()).ifPresent(sess ->
            closeMenuSync(player, sess.getMenuId(), MenuCloseReason.REDIRECT)
        );

        String initialPageId = pageId;
        if (initialPageId == null) {
            initialPageId = menu.pages().values().stream()
                .filter(p -> p.defaultPage())
                .map(p -> p.id())
                .findFirst()
                .orElse(menu.pages().keySet().stream().findFirst().orElse("main"));
        }

        MenuSession session = new MenuSession();
        session.setSessionId(UUID.randomUUID());
        session.setPlayerId(player.getUUID());
        session.setMenuId(menuId);
        session.setCurrentPageId(initialPageId);
        session.setCurrentPageIndex(1);
        session.setOpenedAt(Instant.now());
        session.setRevision(1);
        session.setBackStack(new ArrayDeque<>());
        session.setClosed(false);
        session.setContext(context);

        renderer.openMenu(player, session, menu, context, this);
        sessionStore.save(session);
        renderer.renderPage(player, session, menu, context);
        eventListener.onMenuOpened(player, menuId, session);
        return new MenuOpenResult(true, null);
    }

    private MenuCloseResult closeMenuSync(ServerPlayer player, String menuId, MenuCloseReason reason) {
        Optional<MenuSession> sessionOpt = sessionStore.getByPlayerId(player.getUUID());
        if (sessionOpt.isEmpty() || !sessionOpt.get().getMenuId().equals(menuId)) {
            return new MenuCloseResult(false, "No active session for this menu");
        }

        MenuSession session = sessionOpt.get();
        session.setClosed(true);
        sessionStore.remove(session.getSessionId());
        eventListener.onMenuClose(player, menuId, session, reason);

        if (reason != MenuCloseReason.PLAYER_CLOSE && reason != MenuCloseReason.REDIRECT) {
            player.closeContainer();
        }

        return new MenuCloseResult(true, null);
    }

    private MenuRefreshResult refreshCurrentPageSync(ServerPlayer player) {
        Optional<MenuSession> sessionOpt = sessionStore.getByPlayerId(player.getUUID());
        if (sessionOpt.isPresent()) {
            MenuSession session = sessionOpt.get();
            session.setRevision(session.getRevision() + 1);
            Optional<MenuDefinition> menuOpt = menuRegistry.getMenu(session.getMenuId());
            if (menuOpt.isPresent()) {
                renderer.renderPage(player, session, menuOpt.get(), session.getContext() != null ? session.getContext() : defaultContext(player));
                return new MenuRefreshResult(true, null);
            }
        }
        return new MenuRefreshResult(false, "No session");
    }

    private PageChangeResult goToPageSync(ServerPlayer player, String pageId) {
        Optional<MenuSession> sessionOpt = sessionStore.getByPlayerId(player.getUUID());
        if (sessionOpt.isPresent()) {
            MenuSession session = sessionOpt.get();
            session.setCurrentPageId(pageId);
            session.setRevision(session.getRevision() + 1);
            Optional<MenuDefinition> menuOpt = menuRegistry.getMenu(session.getMenuId());
            if (menuOpt.isPresent()) {
                renderer.renderPage(player, session, menuOpt.get(), session.getContext() != null ? session.getContext() : defaultContext(player));
                return new PageChangeResult(true, null);
            }
        }
        return PageChangeResult.NOT_FOUND;
    }

    private <T> CompletionStage<T> runOnServerThread(ServerPlayer player, Supplier<T> action) {
        if (player == null || player.getServer() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Server unavailable"));
        }
        if (player.getServer().isSameThread()) {
            return CompletableFuture.completedFuture(action.get());
        }
        return player.getServer().submit(action::get);
    }

    private <T> T runBlockingOnServerThread(ServerPlayer player, Supplier<T> action) {
        if (player == null || player.getServer() == null) {
            throw new IllegalStateException("Server unavailable");
        }
        if (player.getServer().isSameThread()) {
            return action.get();
        }
        return player.getServer().submit(action::get).join();
    }

    private MenuContext defaultContext(ServerPlayer player) {
        return new MenuContext(player.getUUID(), "pt_BR", null, null, null, null, null);
    }
}
