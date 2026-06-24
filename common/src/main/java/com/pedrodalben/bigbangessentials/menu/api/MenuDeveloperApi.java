package com.pedrodalben.bigbangessentials.menu.api;

import com.pedrodalben.bigbangessentials.menu.persistence.LoadReport;
import com.pedrodalben.bigbangessentials.menu.persistence.SaveReport;
import java.util.function.Consumer;

public interface MenuDeveloperApi {
    MenuCreateResult createMenu(String menuId, Consumer<MenuBuilder> builder);
    MenuUpdateResult updateItem(String menuId, String pageId, String itemId, Consumer<MenuItemBuilder> builder);
    MenuUpdateResult upsertPage(String menuId, String pageId, Consumer<PageBuilder> builder);
    MenuUpdateResult setLocalizedTitle(String menuId, String locale, String title);
    MenuUpdateResult putContextDefault(String menuId, String key, Object value);
    SaveReport save(String menuId);
    LoadReport reload(String menuId);
}
