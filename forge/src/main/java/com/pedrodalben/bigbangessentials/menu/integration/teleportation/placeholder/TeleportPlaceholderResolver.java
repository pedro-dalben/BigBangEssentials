package com.pedrodalben.bigbangessentials.menu.integration.teleportation.placeholder;

import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderResolver;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderMode;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderRequest;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderValue;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.teleportation.HomeManager;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TeleportPlaceholderResolver implements PlaceholderResolver {
    @Override
    public String id() {
        return "teleport";
    }

    @Override
    public PlaceholderMode mode() {
        return PlaceholderMode.SYNC;
    }

    @Override
    public CompletionStage<PlaceholderValue> resolve(ServerPlayer player, MenuContext context, PlaceholderRequest request) {
        String params = request.params();
        if (params == null) {
            return CompletableFuture.completedFuture(PlaceholderValue.of(""));
        }

        switch (params.toLowerCase()) {
            case "warp_count":
                return CompletableFuture.completedFuture(PlaceholderValue.of(String.valueOf(WarpManager.getInstance().getWarpCount())));
            case "home_count":
                int homeCount = player != null ? HomeManager.getInstance().getHomeNames(player).size() : 0;
                return CompletableFuture.completedFuture(PlaceholderValue.of(String.valueOf(homeCount)));
            case "pwarp_count":
                int pwarpCount = player != null ? WarpManager.getInstance().getPlayerWarpNames(player).size() : 0;
                return CompletableFuture.completedFuture(PlaceholderValue.of(String.valueOf(pwarpCount)));
            default:
                return CompletableFuture.completedFuture(PlaceholderValue.of(""));
        }
    }
}
