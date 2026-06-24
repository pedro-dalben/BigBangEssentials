package com.pedrodalben.bigbangessentials.menu.integration.teleportation.provider;

import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import com.pedrodalben.bigbangessentials.teleportation.TeleportLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GlobalWarpsMenuDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "warps.global";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        WarpManager warpManager = WarpManager.getInstance();
        List<String> warpNames = warpManager.getWarpNames();
        if (warpNames == null) {
            warpNames = List.of();
        }

        List<String> sorted = new ArrayList<>(warpNames);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);

        int totalItems = sorted.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                String warpName = sorted.get(i);
                TeleportLocation loc = warpManager.getWarp(warpName);
                if (loc != null) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("warp_id", warpName);
                    map.put("warp_name", warpName);
                    map.put("warp_world", loc.getWorldName() != null ? loc.getWorldName() : "unknown");
                    map.put("warp_dimension", loc.getWorldName() != null ? loc.getWorldName() : "unknown");
                    map.put("warp_x", String.format(java.util.Locale.ROOT, "%.1f", loc.getX()));
                    map.put("warp_y", String.format(java.util.Locale.ROOT, "%.1f", loc.getY()));
                    map.put("warp_z", String.format(java.util.Locale.ROOT, "%.1f", loc.getZ()));
                    map.put("warp_icon", getIconForWorld(loc.getWorldName()));
                    map.put("warp_created_at", "");
                    items.add(map);
                }
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }

    private String getIconForWorld(String world) {
        if (world == null) return "minecraft:emerald";
        if (world.contains("nether")) return "minecraft:nether_star";
        if (world.contains("end")) return "minecraft:ender_eye";
        return "minecraft:grass_block";
    }
}
