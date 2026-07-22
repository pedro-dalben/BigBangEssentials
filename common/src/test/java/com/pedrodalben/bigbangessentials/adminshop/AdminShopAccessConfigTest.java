package com.pedrodalben.bigbangessentials.adminshop;

import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AdminShopAccessConfigTest {
    @Test
    void usesSeparateNonDefaultCurrencyPermissions() {
        assertEquals("bigbangessentials.adminshop.money", AdminShopTransactionService.currencyPermission("money"));
        assertEquals("bigbangessentials.adminshop.gems", AdminShopTransactionService.currencyPermission("gems"));
        assertFalse(PermissionRegistry.getInstance().getDefaultPermissionValue("bigbangessentials.adminshop.money"));
        assertFalse(PermissionRegistry.getInstance().getDefaultPermissionValue("bigbangessentials.adminshop.gems"));
    }

    @Test
    void enablesAdminShopByDefaultInModuleConfigVersionTwo() {
        try (var input = getClass().getResourceAsStream("/data/config/bigbangessentials/modules.json")) {
            assertNotNull(input);
            var modules = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(2, modules.get("_configVersion").getAsInt());
            assertTrue(modules.get("adminshopEnabled").getAsBoolean());
        } catch (Exception e) {
            fail(e);
        }
    }
}
