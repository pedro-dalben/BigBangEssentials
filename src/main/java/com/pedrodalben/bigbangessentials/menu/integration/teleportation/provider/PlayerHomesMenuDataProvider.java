package com.pedrodalben.bigbangessentials.menu.integration.teleportation.provider;

import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.teleportation.HomeManager;
import com.pedrodalben.bigbangessentials.teleportation.TeleportLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PlayerHomesMenuDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "homes.player";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        HomeManager homeManager = HomeManager.getInstance();
        Map<String, TeleportLocation> homes = homeManager.getPlayerHomes(player);
        if (homes == null) {
            homes = Map.of();
        }

        List<String> homeNames = new ArrayList<>(homes.keySet());
        homeNames.sort(String.CASE_INSENSITIVE_ORDER);

        int totalItems = homeNames.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                String homeName = homeNames.get(i);
                TeleportLocation loc = homes.get(homeName);
                if (loc != null) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("home_name", homeName);
                    map.put("home_world", loc.getWorldName() != null ? loc.getWorldName() : "unknown");
                    map.put("home_dimension", loc.getWorldName() != null ? loc.getWorldName() : "unknown");
                    map.put("home_x", String.format(java.util.Locale.ROOT, "%.1f", loc.getX()));
                    map.put("home_y", String.format(java.util.Locale.ROOT, "%.1f", loc.getY()));
                    map.put("home_z", String.format(java.util.Locale.ROOT, "%.1f", loc.getZ()));
                    map.put("home_icon", "minecraft:red_bed");
                    map.put("home_created_at", "");
                    items.add(map);
                }
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }
}
