package com.pedrodalben.bigbangessentials.api.permissions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestShopPermissionRegistryTest {
    @Test
    void shopUseIsGrantedByDefault() {
        assertTrue(PermissionRegistry.getInstance()
                .getDefaultPermissionValue("bigbangessentials.shop.use"));
    }
}
