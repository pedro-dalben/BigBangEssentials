package com.pedrodalben.bigbangessentials.api.permissions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestShopPermissionRegistryTest {
    @Test
    void shopUseIsGrantedByDefault() {
        assertTrue(PermissionRegistry.getInstance()
                .getDefaultPermissionValue("bigbangessentials.shop.use"));
    }

    @Test
    void commerceNodesAreExplicitlyRegistered() {
        var registry = PermissionRegistry.getInstance();
        assertTrue(registry.isRegistered("bigbangessentials.shop.create"));
        assertTrue(registry.isRegistered("bigbangessentials.shop.admin.remove"));
        assertTrue(registry.isRegistered("bigbangessentials.adminshop.admin"));
        assertTrue(registry.isRegistered("bigbangessentials.pokemarket.admin.retry"));
    }
}
