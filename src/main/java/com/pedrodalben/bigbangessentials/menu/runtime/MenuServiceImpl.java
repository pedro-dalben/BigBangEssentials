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
import com.pedrodalben.bigbangessentials.menu.neoforge.NeoForgeMenuContainer;
import com.pedrodalben.bigbangessentials.menu.neoforge.NeoForgeMenuRenderer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;
import java.util.ArrayDeque;
import java.time.Instant;

import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;

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
        return CompletableFuture.supplyAsync(() -> {
            // Must run sync logic on main thread later, but for now we do standard open flow
            // Actually, we should post to main thread.
            return player.getServer().submit(() -> {
                Optional<MenuDefinition> menuOpt = menuRegistry.getMenu(menuId);
                if (menuOpt.isEmpty()) {
                    return MenuOpenResult.NOT_FOUND;
                }

                MenuDefinition menu = menuOpt.get();
                MenuOpenDecision decision = eventListener.onMenuOpen(player, menuId, context, menu);
                if (!decision.allowed()) {
                    if (decision.redirectMenuId() != null) {
                        return openMenu(player, decision.redirectMenuId(), context).toCompletableFuture().join();
                    }
                    return new MenuOpenResult(false, decision.denyReason() != null ? decision.denyReason() : "Abertura negada.");
                }

                // Close existing session
                sessionStore.getByPlayerId(player.getUUID()).ifPresent(sess -> {
                    closeMenu(player, sess.getMenuId(), MenuCloseReason.REDIRECT);
                });

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

                renderer.openMenu(player, session, menu, context, this);
                
                // container is set in the provider, so we can render
                // sessionStore save is done before rendering
                sessionStore.save(session);
                
                renderer.renderPage(player, session, menu, context);
                
                eventListener.onMenuOpened(player, menuId, session);

                return new MenuOpenResult(true, null);
            }).join();
        });
    }

    @Override
    public MenuCloseResult closeMenu(ServerPlayer player, String menuId, MenuCloseReason reason) {
        return player.getServer().submit(() -> {
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
        }).join();
    }

    @Override
    public MenuRefreshResult refreshMenu(ServerPlayer player, String menuId) {
        return refreshCurrentPage(player);
    }

    @Override
    public MenuRefreshResult refreshCurrentPage(ServerPlayer player) {
        return player.getServer().submit(() -> {
            Optional<MenuSession> sessionOpt = sessionStore.getByPlayerId(player.getUUID());
            if (sessionOpt.isPresent()) {
                MenuSession session = sessionOpt.get();
                session.setRevision(session.getRevision() + 1);
                Optional<MenuDefinition> menuOpt = menuRegistry.getMenu(session.getMenuId());
                if (menuOpt.isPresent()) {
                    renderer.renderPage(player, session, menuOpt.get(), new MenuContext(player.getUUID(), "pt_BR", null, null, null, null, null));
                    return new MenuRefreshResult(true, null);
                }
            }
            return new MenuRefreshResult(false, "No session");
        }).join();
    }

    @Override
    public MenuRefreshResult refreshItem(ServerPlayer player, String menuId, String pageId, String itemId) {
        return refreshCurrentPage(player); // Simplification for now
    }

    @Override
    public PageChangeResult goToPage(ServerPlayer player, String menuId, String pageId) {
        return player.getServer().submit(() -> {
            Optional<MenuSession> sessionOpt = sessionStore.getByPlayerId(player.getUUID());
            if (sessionOpt.isPresent()) {
                MenuSession session = sessionOpt.get();
                session.setCurrentPageId(pageId);
                session.setRevision(session.getRevision() + 1);
                Optional<MenuDefinition> menuOpt = menuRegistry.getMenu(session.getMenuId());
                if (menuOpt.isPresent()) {
                    renderer.renderPage(player, session, menuOpt.get(), new MenuContext(player.getUUID(), "pt_BR", null, null, null, null, null));
                    return new PageChangeResult(true, null);
                }
            }
            return PageChangeResult.NOT_FOUND;
        }).join();
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
}
