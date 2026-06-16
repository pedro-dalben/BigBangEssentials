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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PublicPlayerWarpsMenuDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "pwarps.public";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        WarpManager warpManager = WarpManager.getInstance();
        Map<UUID, Map<String, TeleportLocation>> allPwarps = warpManager.getAllPlayerWarps();
        
        List<PwarpEntry> entries = new ArrayList<>();
        if (allPwarps != null) {
            for (Map.Entry<UUID, Map<String, TeleportLocation>> userEntry : allPwarps.entrySet()) {
                UUID ownerUuid = userEntry.getKey();
                String ownerName = "unknown";
                if (player.getServer() != null) {
                    ServerPlayer owner = player.getServer().getPlayerList().getPlayer(ownerUuid);
                    if (owner != null) {
                        ownerName = owner.getName().getString();
                    } else {
                        ownerName = ownerUuid.toString().substring(0, 8);
                    }
                }
                
                for (Map.Entry<String, TeleportLocation> warpEntry : userEntry.getValue().entrySet()) {
                    entries.add(new PwarpEntry(warpEntry.getKey(), ownerUuid, ownerName, warpEntry.getValue()));
                }
            }
        }
        
        entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        int totalItems = entries.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                PwarpEntry entry = entries.get(i);
                TeleportLocation loc = entry.location();
                Map<String, Object> map = new HashMap<>();
                map.put("pwarp_id", entry.name());
                map.put("pwarp_name", entry.name());
                map.put("pwarp_owner_name", entry.ownerName());
                map.put("pwarp_owner_uuid", entry.ownerUuid().toString());
                map.put("pwarp_world", loc.getWorldName() != null ? loc.getWorldName() : "unknown");
                map.put("pwarp_dimension", loc.getWorldName() != null ? loc.getWorldName() : "unknown");
                map.put("pwarp_x", String.format(java.util.Locale.ROOT, "%.1f", loc.getX()));
                map.put("pwarp_y", String.format(java.util.Locale.ROOT, "%.1f", loc.getY()));
                map.put("pwarp_z", String.format(java.util.Locale.ROOT, "%.1f", loc.getZ()));
                map.put("pwarp_icon", "minecraft:player_head");
                map.put("pwarp_public", "true");
                map.put("pwarp_visits", "0");
                map.put("pwarp_created_at", "");
                items.add(map);
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }

    private record PwarpEntry(String name, UUID ownerUuid, String ownerName, TeleportLocation location) {}
}
