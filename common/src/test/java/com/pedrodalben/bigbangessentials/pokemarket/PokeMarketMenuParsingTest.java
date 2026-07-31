package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.menu.persistence.yaml.YamlMenuParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PokeMarketMenuParsingTest {
    @BeforeAll
    static void setup() {
        try { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); } catch (Throwable ignored) { }
    }

    @Test
    void bundledMenusAreValid() throws Exception {
        List<String> menus = List.of("pokemarket_main", "pokemarket_browse", "pokemarket_detail", "pokemarket_buy_confirm",
            "pokemarket_sell_confirm", "pokemarket_claims", "pokemarket_notifications", "pokemarket_species", "pokemarket_records", "pokemarket_admin", "pokemarket_trade_requirements", "pokemarket_trade_accept_confirm", "pokemarket_party", "pokemarket_pc");
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
}
