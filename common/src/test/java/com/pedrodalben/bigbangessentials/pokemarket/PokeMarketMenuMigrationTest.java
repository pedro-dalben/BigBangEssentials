package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.pokemarket.menu.PokeMarketMenuIntegration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PokeMarketMenuMigrationTest {
    private static final List<String> MENUS = List.of(
        "pokemarket_main.yml", "pokemarket_browse.yml", "pokemarket_detail.yml", "pokemarket_buy_confirm.yml",
        "pokemarket_sell_confirm.yml", "pokemarket_claims.yml", "pokemarket_notifications.yml", "pokemarket_species.yml",
        "pokemarket_records.yml", "pokemarket_admin.yml", "pokemarket_trade_requirements.yml",
        "pokemarket_trade_accept_confirm.yml", "pokemarket_party.yml", "pokemarket_pc.yml"
    );

    @Test
    void installsAllNewMenusAsSchemaTwo() throws Exception {
        Path dir = Files.createTempDirectory("pokemarket-menus");
        try {
            PokeMarketMenuIntegration.prepare(dir);
            for (String menu : MENUS) {
                assertTrue(Files.exists(dir.resolve(menu)), menu);
                assertTrue(Files.readString(dir.resolve(menu)).contains("schema-version: 2"), menu);
            }
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }

    @Test
    void keepsCustomizedLegacyMenuInsteadOfOverwritingIt() throws Exception {
        Path dir = Files.createTempDirectory("pokemarket-custom");
        Path custom = dir.resolve("pokemarket_main.yml");
        String content = "id: pokemarket_main\nschema-version: 1\nsize: 27\ntitle: custom\npages: {}\n";
        Files.writeString(custom, content);
        try {
            PokeMarketMenuIntegration.prepare(dir);
            assertEquals(content, Files.readString(custom));
            try (var files = Files.list(dir)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("pokemarket_main.yml.bak-")));
            }
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }
}
