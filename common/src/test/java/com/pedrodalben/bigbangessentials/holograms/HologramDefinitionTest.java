package com.pedrodalben.bigbangessentials.holograms;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HologramDefinitionTest {
    @Test
    void normalizesNamespacedIdsToLowercase() {
        HologramDefinition definition = HologramDefinition.builder("BigBangEssentials:Spawn/Rules")
            .location(new HologramLocation(Level.OVERWORLD, 0.0D, 70.0D, 0.0D))
            .lines(List.of("Linha 1"))
            .build();

        assertEquals("bigbangessentials:spawn/rules", definition.id());
    }

    @Test
    void rejectsIdsWithoutNamespace() {
        assertThrows(IllegalArgumentException.class, () -> HologramDefinition.builder("spawn-rules")
            .location(new HologramLocation(Level.OVERWORLD, 0.0D, 70.0D, 0.0D))
            .lines(List.of("Linha 1"))
            .build());
    }

    @Test
    void requiresAtLeastOnePage() {
        assertThrows(IllegalArgumentException.class, () -> HologramDefinition.builder("bigbangessentials:test/empty")
            .location(new HologramLocation(Level.OVERWORLD, 0.0D, 70.0D, 0.0D))
            .build());
    }
}
