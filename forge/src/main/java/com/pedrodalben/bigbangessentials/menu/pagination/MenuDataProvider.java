package com.pedrodalben.bigbangessentials.menu.pagination;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import java.util.concurrent.CompletionStage;

public interface MenuDataProvider {
    String id();
    CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request);
}
