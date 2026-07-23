package com.pedrodalben.bigbangessentials.shop.model;

import com.pedrodalben.bigbangessentials.shop.ShopTransaction;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopDataTest {
    @Test
    void classifiesExplicitAdminPlayerAndLegacyShops() {
        ShopData admin = new ShopData();
        admin.ownerName = ShopData.ADMIN_SHOP_NAME;

        ShopData player = new ShopData();
        player.ownerUUID = UUID.randomUUID();
        player.ownerName = "Player";

        ShopData legacy = new ShopData();
        legacy.ownerName = "OldPlayer";

        assertTrue(admin.isAdminShop());
        assertFalse(admin.isLegacyUnownedShop());
        assertFalse(player.isAdminShop());
        assertFalse(player.isLegacyUnownedShop());
        assertFalse(legacy.isAdminShop());
        assertTrue(legacy.isLegacyUnownedShop());
        assertEquals(ShopTransaction.ResultType.LEGACY_UNOWNED,
                ShopTransaction.executeBuy(null, legacy, null).type);
        assertEquals(ShopTransaction.ResultType.LEGACY_UNOWNED,
                ShopTransaction.executeSell(null, legacy, null).type);
    }
}
