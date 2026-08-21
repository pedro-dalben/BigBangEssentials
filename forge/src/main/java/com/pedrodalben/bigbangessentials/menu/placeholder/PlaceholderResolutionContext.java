package com.pedrodalben.bigbangessentials.menu.placeholder;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;

public record PlaceholderResolutionContext(
    ServerPlayer player,
    MenuContext context,
    MenuSession session,
    MenuDefinition menu
) {}
