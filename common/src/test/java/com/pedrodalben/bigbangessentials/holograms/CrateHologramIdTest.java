package com.pedrodalben.bigbangessentials.holograms;

import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrateHologramIdTest {
    @Test
    void usesStableNamespacedLocationId() {
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        CrateLocation location = new CrateLocation(id, "crate", Level.OVERWORLD, new BlockPos(1, 64, 1));

        assertEquals(
            "bigbangessentials:crate/01234567-89ab-cdef-0123-456789abcdef",
            CrateHologramManager.hologramId(location)
        );
    }
}
