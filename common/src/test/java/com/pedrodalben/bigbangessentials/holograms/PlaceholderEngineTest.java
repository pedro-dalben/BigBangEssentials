package com.pedrodalben.bigbangessentials.holograms;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPlaceholderResolver;
import com.pedrodalben.bigbangessentials.holograms.placeholder.PlaceholderEngine;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderEngineTest {
    @Test
    void keepsUnknownPlaceholderVisibleAndMergesMultilineContent() {
        PlaceholderEngine engine = new PlaceholderEngine();
        engine.register(new HologramPlaceholderResolver() {
            @Override
            public boolean supports(String placeholder) {
                return "crate_name".equals(placeholder);
            }

            @Override
            public boolean isPlayerScoped() {
                return false;
            }

            @Override
            public String resolve(String placeholder, HologramDefinition definition, net.minecraft.server.level.ServerPlayer viewer) {
                return "Lendaria";
            }
        });

        HologramDefinition definition = HologramDefinition.builder("bigbangessentials:test/placeholders")
            .location(new HologramLocation(Level.OVERWORLD, 0.0D, 80.0D, 0.0D))
            .lines(List.of("&6{crate_name}", "&7{missing_token}"))
            .build();

        PlaceholderEngine.ResolvedContent resolved = engine.resolve(definition, 0, null);
        String plain = resolved.component().getString();
        assertTrue(plain.contains("Lendaria"));
        assertTrue(plain.contains("{missing_token}"));
        assertEquals(2, plain.split("\n").length);
    }
}
