package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminShopRuntimeLoadTest {
    @Test
    void runtimeCatalogContainsTheMoneyStore() {
        AdminShopConfig config = AdminShopConfig.load();

        assertTrue(config.stores.containsKey("money"),
                () -> "stores loaded: " + config.stores.keySet());
        assertTrue(config.categoriesByStore("money").size() > 0);
        assertTrue(config.products("money").size() > 0);
    }

    @Test
    void resolvesStoreIdAndCurrencyWithoutCaseSensitivity() {
        AdminShopConfig config = AdminShopConfig.load();

        assertEquals("money", config.findStoreId("MONEY"));
        assertEquals("money", config.findStoreId("MoNeY"));
    }

    @Test
    void generatesOneYamlFilePerCategory() {
        AdminShopConfig.load();

        assertTrue(Files.exists(ResourceUtil.getConfigPath("shops/money/blocks.yml")));
    }
}
