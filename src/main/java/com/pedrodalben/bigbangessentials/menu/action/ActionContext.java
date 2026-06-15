package com.pedrodalben.bigbangessentials.menu.action;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuPageDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuItemDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuClickType;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import java.util.Map;

public record ActionContext(
    ServerPlayer player,
    MenuSession session,
    MenuDefinition menu,
    MenuPageDefinition page,
    MenuItemDefinition item,
    MenuClickType clickType,
    MenuContext context,
    Map<String, Object> resolvedParams
) {
    @SuppressWarnings("unchecked")
    public <T> T param(String key, Class<T> type) {
        Object val = resolvedParams != null ? resolvedParams.get(key) : null;
        return type.isInstance(val) ? (T) val : null;
    }
}
