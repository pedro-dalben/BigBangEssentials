package com.pedrodalben.bigbangessentials.adminshop;

import org.junit.jupiter.api.Test;
import com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogLoader;
import com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogV2;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;

public class AdminShopLoadTest {
    @Test
    public void testLoad() throws Exception {
        String content = Files.readString(Path.of("src/main/resources/default-config/bigbangessentials/adminshop.yml"));
        Yaml yaml = new Yaml();
        AdminShopCatalogV2 catalog = yaml.loadAs(content, AdminShopCatalogV2.class);
        System.out.println("Stores: " + catalog.stores.size());
        System.out.println("Products: " + catalog.products.size());
    }
}
