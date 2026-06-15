package com.pedrodalben.bigbangessentials.menu;

import com.pedrodalben.bigbangessentials.menu.api.MenuService;
import com.pedrodalben.bigbangessentials.menu.api.MenuPersistenceService;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuRegistry;
import com.pedrodalben.bigbangessentials.menu.session.MenuSessionStore;
import com.pedrodalben.bigbangessentials.menu.neoforge.NeoForgeMenuRenderer;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuServiceImpl;
import com.pedrodalben.bigbangessentials.menu.persistence.yaml.YamlMenuPersistenceService;
import com.pedrodalben.bigbangessentials.menu.event.MenuEventListener;
import com.pedrodalben.bigbangessentials.menu.command.MenuCommand;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuActionRegistryImpl;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuConditionRegistryImpl;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;

public class MenuSystem {
    private static MenuSystem instance;
    private MenuService menuService;
    private MenuPersistenceService persistenceService;

    public static MenuSystem getInstance() {
        if (instance == null) {
            instance = new MenuSystem();
        }
        return instance;
    }

    public void initialize() {
        MenuRegistry registry = new MenuRegistry();
        MenuSessionStore sessionStore = new MenuSessionStore();
        NeoForgeMenuRenderer renderer = new NeoForgeMenuRenderer();
        MenuEventListener listener = new MenuEventListener() {}; // Default listener

        MenuActionRegistryImpl actionRegistry = new MenuActionRegistryImpl();
        MenuConditionRegistryImpl conditionRegistry = new MenuConditionRegistryImpl();
        
        // Register Actions
        actionRegistry.registerActionHandler("open_menu", new com.pedrodalben.bigbangessentials.menu.action.builtin.OpenMenuAction());
        actionRegistry.registerActionHandler("close_menu", new com.pedrodalben.bigbangessentials.menu.action.builtin.CloseMenuAction());
        actionRegistry.registerActionHandler("send_message", new com.pedrodalben.bigbangessentials.menu.action.builtin.SendMessageAction());
        actionRegistry.registerActionHandler("run_player_command", new com.pedrodalben.bigbangessentials.menu.action.builtin.RunPlayerCommandAction());
        actionRegistry.registerActionHandler("run_console_command", new com.pedrodalben.bigbangessentials.menu.action.builtin.RunConsoleCommandAction());
        actionRegistry.registerActionHandler("next_page", new com.pedrodalben.bigbangessentials.menu.action.builtin.NextPageAction());
        actionRegistry.registerActionHandler("previous_page", new com.pedrodalben.bigbangessentials.menu.action.builtin.PreviousPageAction());

        // Register Conditions
        conditionRegistry.registerConditionHandler("permission", new com.pedrodalben.bigbangessentials.menu.condition.builtin.PermissionCondition());
        conditionRegistry.registerConditionHandler("page_index_at_least", new com.pedrodalben.bigbangessentials.menu.condition.builtin.PageIndexAtLeastCondition());

        menuService = new MenuServiceImpl(registry, sessionStore, listener, renderer);
        
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("bigbangessentials").resolve("menus");
        persistenceService = new YamlMenuPersistenceService(configDir, registry);

        // Load all menus
        persistenceService.loadAllMenus();

        // Register for commands
        MenuCommand.setMenuService(menuService);
    }

    public MenuService getMenuService() {
        return menuService;
    }

    public MenuPersistenceService getPersistenceService() {
        return persistenceService;
    }
}
