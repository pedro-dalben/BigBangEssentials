package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.menu.persistence.yaml.YamlMenuParser;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PokeMarketMenuParsingTest {
    @BeforeAll
    static void setup() {
        try { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); } catch (Throwable ignored) { }
    }

    @Test
    void bundledMenusAreValid() throws Exception {
        List<String> menus = List.of("pokemarket_main", "pokemarket_browse", "pokemarket_detail", "pokemarket_buy_confirm",
            "pokemarket_sell_confirm", "pokemarket_claims", "pokemarket_notifications", "pokemarket_species", "pokemarket_records", "pokemarket_admin", "pokemarket_trade_requirements", "pokemarket_trade_accept_confirm", "pokemarket_party", "pokemarket_pc", "pokemarket_cancel_confirm", "pokemarket_sell_source", "pokemarket_account");
        YamlMenuParser parser = new YamlMenuParser();
        for (String id : menus) {
            try (InputStream input = getClass().getResourceAsStream("/default-config/bigbangessentials/menus/" + id + ".yml")) {
                Path file = Files.createTempFile(id, ".yml");
                Files.copy(input, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                assertDoesNotThrow(() -> parser.parse(file), id);
                Files.deleteIfExists(file);
            }
        }
    }

    @Test
    void mainMenuRoutesPrimaryFlows() throws Exception {
        YamlMenuParser parser = new YamlMenuParser();
        Path file = Files.createTempFile("pokemarket-main", ".yml");
        try (InputStream input = getClass().getResourceAsStream("/default-config/bigbangessentials/menus/pokemarket_main.yml")) {
            Files.copy(input, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            MenuDefinition menu = parser.parse(file);
            var items = menu.pages().get("main").items();
            assertEquals("&aAnunciar venda", items.get("sell").item().displayName());
            assertEquals("pokemarket_sell_source", items.get("sell").actions().get(0).params().get("menu-id"));
            assertEquals("trade", items.get("trade").actions().get(0).params().get("mode"));
            assertEquals("party", items.get("trade").actions().get(0).params().get("source"));
            assertEquals("pokemarket_account", items.get("account").actions().get(0).params().get("menu-id"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void ownListingIsVisibleButPurchaseActionsAreHidden() throws Exception {
        YamlMenuParser parser = new YamlMenuParser();
        Path file = Files.createTempFile("pokemarket-detail", ".yml");
        try (InputStream input = getClass().getResourceAsStream("/default-config/bigbangessentials/menus/pokemarket_detail.yml")) {
            Files.copy(input, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            MenuDefinition menu = parser.parse(file);
            var items = menu.pages().get("main").items();
            assertEquals("context_not_equals", items.get("buy").renderConditions().get(1).type());
            assertEquals("seller_uuid", items.get("buy").renderConditions().get(1).params().get("key"));
            assertEquals("{player_uuid}", items.get("buy").renderConditions().get(1).params().get("value"));
            assertEquals("context_equals", items.get("own_listing").renderConditions().get(0).type());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
