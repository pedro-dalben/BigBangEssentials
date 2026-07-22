package com.pedrodalben.bigbangessentials.shop.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSignRegistrationServiceTest {
    @Test
    void onlyRegistersAfterQuantityAndPriceAreWritten() {
        assertTrue(ShopSignRegistrationService.hasCompleteShopText(new String[] {"", "16", "B 10:S 5", ""}));
        assertFalse(ShopSignRegistrationService.hasCompleteShopText(new String[] {"", "", "B 10", ""}));
        assertFalse(ShopSignRegistrationService.hasCompleteShopText(new String[] {"", "16", "", ""}));
    }
}
