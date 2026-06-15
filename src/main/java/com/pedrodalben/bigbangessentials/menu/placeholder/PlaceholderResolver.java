package com.pedrodalben.bigbangessentials.menu.placeholder;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import java.util.concurrent.CompletionStage;

public interface PlaceholderResolver {
    String id();
    PlaceholderMode mode();
    CompletionStage<PlaceholderValue> resolve(ServerPlayer player, MenuContext context, PlaceholderRequest request);
}
