package com.pedrodalben.bigbangessentials.menu.api;

import com.pedrodalben.bigbangessentials.menu.persistence.*;
import java.nio.file.Path;

public interface MenuPersistenceService {
    LoadReport loadAllMenus();
    LoadReport loadMenu(Path file);
    SaveReport saveMenu(String menuId);
    SaveReport saveAllDirtyMenus();
    ValidationReport validateAll();
    ValidationReport validateMenu(String menuId);
    ReloadReport reloadAll();
    ReloadReport reloadMenu(String menuId);
}
