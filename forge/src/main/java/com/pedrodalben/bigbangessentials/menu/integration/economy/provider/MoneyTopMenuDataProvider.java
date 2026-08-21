package com.pedrodalben.bigbangessentials.menu.integration.economy.provider;

import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class MoneyTopMenuDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "economy.top.money";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, BigDecimal> balances = EconomyManager.getInstance().getAllBalances();
            
            // Sort by balance descending
            List<Map.Entry<UUID, BigDecimal>> sorted = balances.entrySet().stream()
                    .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0) // only positive balances
                    .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                    .toList();

            int totalItems = sorted.size();
            int fromIndex = (request.page() - 1) * request.itemsPerPage();
            int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

            List<Map<String, Object>> items = new ArrayList<>();
            if (fromIndex >= 0 && fromIndex < totalItems) {
                for (int i = fromIndex; i < toIndex; i++) {
                    Map.Entry<UUID, BigDecimal> entry = sorted.get(i);
                    UUID uuid = entry.getKey();
                    BigDecimal balance = entry.getValue();
                    int rank = i + 1;

                    String name = "Unknown";
                    var profile = player.server.getProfileCache().get(uuid).orElse(null);
                    if (profile != null && profile.getName() != null) {
                        name = profile.getName();
                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("money_top_uuid", uuid.toString());
                    map.put("money_top_name", name);
                    map.put("money_top_rank", String.valueOf(rank));
                    
                    // Format balance
                    String formatted = String.format(java.util.Locale.US, "%,.2f", balance).replace(".00", "");
                    map.put("money_top_balance", formatted);
                    items.add(map);
                }
            }

            return new MenuDataResult(items, totalItems);
        });
    }
}
