package com.pedrodalben.bigbangessentials.crates.placeholder;

import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderMode;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderRequest;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderResolver;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderValue;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CratePlaceholderResolver implements PlaceholderResolver {

    public CratePlaceholderResolver() {
    }

    @Override
    public String id() {
        return "crates";
    }

    @Override
    public CompletionStage<PlaceholderValue> resolve(ServerPlayer player, MenuContext context, PlaceholderRequest request) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        String raw = request.rawInput();
        String params = request.params();
        UUID playerId = player.getUUID();
        String resolved = null;

        if (params != null) {
            if (raw.startsWith("key_")) {
                int balance = CrateKeyService.getInstance().getVirtualKeyBalance(playerId, params);
                resolved = String.valueOf(balance);
            } else if (raw.startsWith("opened_")) {
                resolved = "0";
            } else if (raw.startsWith("cooldown_")) {
                resolved = "§aDispon\u00edvel";
            }
        }

        if (raw.equals("total_opened")) {
            resolved = "0";
        }

        if (resolved != null) {
            return CompletableFuture.completedFuture(new PlaceholderValue(resolved));
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public PlaceholderMode mode() {
        return PlaceholderMode.SYNC;
    }
}
