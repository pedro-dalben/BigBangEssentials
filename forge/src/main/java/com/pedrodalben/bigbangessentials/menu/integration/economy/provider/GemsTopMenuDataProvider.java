package com.pedrodalben.bigbangessentials.menu.integration.economy.provider;

import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GemsTopMenuDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "economy.top.gems";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Long> balances = GemsManager.getInstance().getAllBalances();
            
            // Sort by balance descending
            List<Map.Entry<UUID, Long>> sorted = balances.entrySet().stream()
                    .filter(e -> e.getValue() > 0) // only positive balances
                    .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                    .toList();

            int totalItems = sorted.size();
            int fromIndex = (request.page() - 1) * request.itemsPerPage();
            int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

            List<Map<String, Object>> items = new ArrayList<>();
            if (fromIndex >= 0 && fromIndex < totalItems) {
                for (int i = fromIndex; i < toIndex; i++) {
                    Map.Entry<UUID, Long> entry = sorted.get(i);
                    UUID uuid = entry.getKey();
                    Long balance = entry.getValue();
                    int rank = i + 1;

                    String name = "Unknown";
                    var profile = player.server.getProfileCache().get(uuid).orElse(null);
                    if (profile != null && profile.getName() != null) {
                        name = profile.getName();
                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("gems_top_uuid", uuid.toString());
                    map.put("gems_top_name", name);
                    map.put("gems_top_rank", String.valueOf(rank));
                    
                    String formatted = GemsManager.getInstance().format(balance);
                    map.put("gems_top_balance", formatted);
                    items.add(map);
                }
            }

            return new MenuDataResult(items, totalItems);
        });
    }
}
