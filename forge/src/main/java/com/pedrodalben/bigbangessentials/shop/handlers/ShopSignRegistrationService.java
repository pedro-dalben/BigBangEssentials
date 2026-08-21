package com.pedrodalben.bigbangessentials.shop.handlers;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.shop.ShopManager;
import com.pedrodalben.bigbangessentials.shop.ShopParser;
import com.pedrodalben.bigbangessentials.shop.model.ShopData;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared sign registration flow used by both Fabric and NeoForge. */
public final class ShopSignRegistrationService {
    private static final long TIMEOUT_MS = 30_000L;
    private static final int CHECK_INTERVAL_TICKS = 5;
    private static final Map<String, PendingSign> pending = new ConcurrentHashMap<>();
    private static int tickCounter;

    private ShopSignRegistrationService() {}

    private record PendingSign(UUID playerUUID, String dimension, BlockPos pos, long placedAt) {}

    public static void trackPlacement(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SignBlock)) return;

        String dimension = level.dimension().location().toString();
        pending.put(key(dimension, pos), new PendingSign(player.getUUID(), dimension, pos, System.currentTimeMillis()));
    }

    public static void tick() {
        if (++tickCounter % CHECK_INTERVAL_TICKS != 0) return;

        var server = Platform.getCurrentServer();
        if (server == null) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingSign>> entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, PendingSign> entry = entries.next();
            PendingSign pendingSign = entry.getValue();
            if (now - pendingSign.placedAt() > TIMEOUT_MS) {
                entries.remove();
                continue;
            }

            ServerLevel level = null;
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.dimension().location().toString().equals(pendingSign.dimension())) {
                    level = candidate;
                    break;
                }
            }
            if (level == null) {
                entries.remove();
                continue;
            }

            BlockPos pos = pendingSign.pos();

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                entries.remove();
                continue;
            }

            String[] lines = ShopSignText.read(sign);
            if (!hasCompleteShopText(lines)) continue;

            entries.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(pendingSign.playerUUID());
            if (player != null) tryRegisterShop(player, lines, pos, pendingSign.dimension(), level);
        }
    }

    public static void tryRegisterShop(ServerPlayer player, String[] lines,
                                       BlockPos pos, String dimension, ServerLevel level) {
        String ownerLine = lines[ShopData.NAME_LINE].replaceAll("§[0-9a-fA-FkKlLmMnNoOrRiI]", "").trim();
        if (ownerLine.isEmpty()) {
            ownerLine = player.getName().getString();
            lines[ShopData.NAME_LINE] = ownerLine;
        }

        boolean wantsAdmin = ShopData.ADMIN_SHOP_NAME.equals(ownerLine);
        if (wantsAdmin && !PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.shop.create.admin")) {
            player.sendSystemMessage(Component.literal("§cYou don't have permission to create admin shops."));
            return;
        }
        if (!wantsAdmin && !PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.shop.create")) {
            player.sendSystemMessage(Component.literal("§cYou don't have permission to create shops."));
            return;
        }

        if (ShopManager.getInstance().getShopBySign(dimension, pos) != null) {
            player.sendSystemMessage(Component.literal("§cA shop already exists at this sign."));
            return;
        }

        Optional<ShopData> parsed = ShopParser.parse(
            lines, pos, dimension, level, player.getUUID(), player.getName().getString());
        if (parsed.isEmpty()) {
            if (!wantsAdmin && ShopParser.findAdjacentChest(pos, level) == null) {
                player.sendSystemMessage(Component.literal("§cNo chest found next to this sign. Place a chest first."));
            } else {
                player.sendSystemMessage(Component.literal(
                    "§cInvalid shop sign format.  Lines: [name or blank] / [qty] / [B x:S y] / [item or ?]"));
            }
            return;
        }

        ShopData shop = parsed.get();
        ShopManager.getInstance().registerShop(shop);
        ShopSignText.write(level, pos, ShopParser.formatSignLines(shop));

        if (shop.itemPending) {
            player.sendSystemMessage(Component.literal(
                "§aShop frame created! §eRight-click this sign while holding the item you want to sell/buy."));
        } else {
            player.sendSystemMessage(Component.literal("§aShop created successfully!"));
            String currency = com.pedrodalben.bigbangessentials.economy.managers.EconomyManager.getInstance().getCurrencySymbol();
            if (!shop.isAdminShop()) {
                if (shop.buyPrice != null) player.sendSystemMessage(Component.literal(
                    "§eBuy price:  §f" + currency + shop.buyPrice.toPlainString()));
                if (shop.sellPrice != null) player.sendSystemMessage(Component.literal(
                    "§eSell price: §f" + currency + shop.sellPrice.toPlainString()));
            } else {
                player.sendSystemMessage(Component.literal("§2[Admin Shop] Unlimited stock."));
            }
        }
    }

    static boolean hasCompleteShopText(String[] lines) {
        if (lines.length <= ShopData.PRICE_LINE || lines.length <= ShopData.QUANTITY_LINE) return false;
        String price = lines[ShopData.PRICE_LINE].toUpperCase().trim();
        return (price.contains("B") || price.contains("S")) && !lines[ShopData.QUANTITY_LINE].isBlank();
    }

    private static String key(String dimension, BlockPos pos) {
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
