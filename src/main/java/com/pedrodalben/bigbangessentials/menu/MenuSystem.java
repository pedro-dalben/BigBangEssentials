package com.pedrodalben.bigbangessentials.menu;

import com.pedrodalben.bigbangessentials.menu.api.MenuService;
import com.pedrodalben.bigbangessentials.menu.api.MenuPersistenceService;
import com.pedrodalben.bigbangessentials.menu.api.MenuActionRegistry;
import com.pedrodalben.bigbangessentials.menu.api.MenuConditionRegistry;
import com.pedrodalben.bigbangessentials.menu.api.MenuPlaceholderRegistry;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuRegistry;
import com.pedrodalben.bigbangessentials.menu.session.MenuSessionStore;
import com.pedrodalben.bigbangessentials.menu.neoforge.NeoForgeMenuRenderer;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuServiceImpl;
import com.pedrodalben.bigbangessentials.menu.persistence.yaml.YamlMenuPersistenceService;
import com.pedrodalben.bigbangessentials.menu.event.MenuEventListener;
import com.pedrodalben.bigbangessentials.menu.command.MenuCommand;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuActionRegistryImpl;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuConditionRegistryImpl;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuPlaceholderRegistryImpl;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;

public class MenuSystem {
    private static MenuSystem instance;
    private MenuService menuService;
    private MenuPersistenceService persistenceService;
    private MenuActionRegistryImpl actionRegistry;
    private MenuConditionRegistryImpl conditionRegistry;
    private MenuPlaceholderRegistryImpl placeholderRegistry;
    private MenuRegistry registry;

    public static MenuSystem getInstance() {
        if (instance == null) {
            instance = new MenuSystem();
        }
        return instance;
    }

    public void initialize() {
        registry = new MenuRegistry();
        MenuSessionStore sessionStore = new MenuSessionStore();
        NeoForgeMenuRenderer renderer = new NeoForgeMenuRenderer();
        MenuEventListener listener = new MenuEventListener() {}; // Default listener

        actionRegistry = new MenuActionRegistryImpl();
        conditionRegistry = new MenuConditionRegistryImpl();
        placeholderRegistry = new MenuPlaceholderRegistryImpl();
        
        // Register Actions
        actionRegistry.registerActionHandler("open_menu", new com.pedrodalben.bigbangessentials.menu.action.builtin.OpenMenuAction());
        actionRegistry.registerActionHandler("close_menu", new com.pedrodalben.bigbangessentials.menu.action.builtin.CloseMenuAction());
        actionRegistry.registerActionHandler("back_menu", new com.pedrodalben.bigbangessentials.menu.action.builtin.BackMenuAction());
        actionRegistry.registerActionHandler("go_to_page", new com.pedrodalben.bigbangessentials.menu.action.builtin.GoToPageAction());
        actionRegistry.registerActionHandler("send_message", new com.pedrodalben.bigbangessentials.menu.action.builtin.SendMessageAction());
        actionRegistry.registerActionHandler("run_player_command", new com.pedrodalben.bigbangessentials.menu.action.builtin.RunPlayerCommandAction());
        actionRegistry.registerActionHandler("run_console_command", new com.pedrodalben.bigbangessentials.menu.action.builtin.RunConsoleCommandAction());
        actionRegistry.registerActionHandler("next_page", new com.pedrodalben.bigbangessentials.menu.action.builtin.NextPageAction());
        actionRegistry.registerActionHandler("previous_page", new com.pedrodalben.bigbangessentials.menu.action.builtin.PreviousPageAction());
        actionRegistry.registerActionHandler("refresh_menu", new com.pedrodalben.bigbangessentials.menu.action.builtin.RefreshMenuAction());
        actionRegistry.registerActionHandler("refresh_page", new com.pedrodalben.bigbangessentials.menu.action.builtin.RefreshPageAction());
        actionRegistry.registerActionHandler("refresh_item", new com.pedrodalben.bigbangessentials.menu.action.builtin.RefreshItemAction());
        actionRegistry.registerActionHandler("set_context_value", new com.pedrodalben.bigbangessentials.menu.action.builtin.SetContextValueAction());
        actionRegistry.registerActionHandler("remove_context_value", new com.pedrodalben.bigbangessentials.menu.action.builtin.RemoveContextValueAction());

        // Register Conditions
        conditionRegistry.registerConditionHandler("permission", new com.pedrodalben.bigbangessentials.menu.condition.builtin.PermissionCondition());
        conditionRegistry.registerConditionHandler("has_all_permissions", new com.pedrodalben.bigbangessentials.menu.condition.builtin.HasAllPermissionsCondition());
        conditionRegistry.registerConditionHandler("has_any_permission", new com.pedrodalben.bigbangessentials.menu.condition.builtin.HasAnyPermissionCondition());
        conditionRegistry.registerConditionHandler("lacks_permission", new com.pedrodalben.bigbangessentials.menu.condition.builtin.LacksPermissionCondition());
        conditionRegistry.registerConditionHandler("context_present", new com.pedrodalben.bigbangessentials.menu.condition.builtin.ContextPresentCondition());
        conditionRegistry.registerConditionHandler("context_equals", new com.pedrodalben.bigbangessentials.menu.condition.builtin.ContextEqualsCondition());
        conditionRegistry.registerConditionHandler("context_not_equals", new com.pedrodalben.bigbangessentials.menu.condition.builtin.ContextNotEqualsCondition());
        conditionRegistry.registerConditionHandler("page_index_at_least", new com.pedrodalben.bigbangessentials.menu.condition.builtin.PageIndexAtLeastCondition());
        conditionRegistry.registerConditionHandler("page_index_at_most", new com.pedrodalben.bigbangessentials.menu.condition.builtin.PageIndexAtMostCondition());
        conditionRegistry.registerConditionHandler("current_page_is", new com.pedrodalben.bigbangessentials.menu.condition.builtin.CurrentPageIsCondition());

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

    public MenuActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    public MenuConditionRegistry getConditionRegistry() {
        return conditionRegistry;
    }

    public MenuPlaceholderRegistry getPlaceholderRegistry() {
        return placeholderRegistry;
    }

    public MenuRegistry getRegistry() {
        return registry;
    }
}
