package com.pedrodalben.bigbangessentials.menu.integration.kits;

import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderMode;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderRequest;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderResolver;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderValue;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class KitPlaceholderResolver implements PlaceholderResolver {
    @Override
    public String id() {
        return "kits";
    }

    @Override
    public PlaceholderMode mode() {
        return PlaceholderMode.SYNC;
    }

    @Override
    public CompletionStage<PlaceholderValue> resolve(ServerPlayer player, MenuContext context, PlaceholderRequest request) {
        String params = request.params();
        if (params == null || params.isBlank()) {
            return CompletableFuture.completedFuture(PlaceholderValue.of(""));
        }

        Map<String, Object> values = KitMenuSupport.buildSummaryPlaceholders(player);
        String key = params.toLowerCase(java.util.Locale.ROOT);
        Object value = values.get(key);
        if (value == null && !key.startsWith("kits_")) {
            value = values.get("kits_" + key);
        }
        if (value == null && key.startsWith("kits_")) {
            value = values.get(key.substring("kits_".length()));
        }
        return CompletableFuture.completedFuture(PlaceholderValue.of(value != null ? value.toString() : ""));
    }
}
