package com.pedrodalben.bigbangessentials.menu.integration.teleportation.provider;

import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import com.pedrodalben.bigbangessentials.teleportation.TeleportLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OwnPlayerWarpsMenuDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "pwarps.own";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        WarpManager warpManager = WarpManager.getInstance();
        List<String> pwarpNames = warpManager.getPlayerWarpNames(player);
        if (pwarpNames == null) {
            pwarpNames = List.of();
        }

        List<String> sorted = new ArrayList<>(pwarpNames);
        sorted.sort(Comparator
            .comparingInt((String name) -> warpManager.getPlayerWarpVisits(player.getUUID(), name)).reversed()
            .thenComparing(String.CASE_INSENSITIVE_ORDER));

        int totalItems = sorted.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                String warpName = sorted.get(i);
                TeleportLocation loc = warpManager.getPlayerWarp(player, warpName);
                if (loc != null) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("pwarp_id", warpName);
                    map.put("pwarp_name", warpName);
                    map.put("pwarp_owner_name", player.getName().getString());
                    map.put("pwarp_owner_uuid", player.getUUID().toString());
                    map.put("pwarp_world", loc.getWorldName() != null ? loc.getWorldName() : "unknown");
                    map.put("pwarp_dimension", loc.getWorldName() != null ? loc.getWorldName() : "unknown");
                    map.put("pwarp_x", String.format(java.util.Locale.ROOT, "%.1f", loc.getX()));
                    map.put("pwarp_y", String.format(java.util.Locale.ROOT, "%.1f", loc.getY()));
                    map.put("pwarp_z", String.format(java.util.Locale.ROOT, "%.1f", loc.getZ()));
                    map.put("pwarp_icon", "minecraft:player_head");
                    map.put("pwarp_public", "true");
                    map.put("pwarp_visits", String.valueOf(warpManager.getPlayerWarpVisits(player.getUUID(), warpName)));
                    map.put("pwarp_created_at", "");
                    items.add(map);
                }
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }
}
