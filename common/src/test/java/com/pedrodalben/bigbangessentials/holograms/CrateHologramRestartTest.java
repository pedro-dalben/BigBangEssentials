package com.pedrodalben.bigbangessentials.holograms;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import com.pedrodalben.bigbangessentials.crates.repository.CrateLocationRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateRepository;
import com.pedrodalben.bigbangessentials.crates.repository.KeyRepository;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.holograms.api.BigBangHolograms;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrateHologramRestartTest {
    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        BigBangHologramsManager.getInstance().shutdown();
        clearCrateHologramCache();
        setCrateServiceInstance(null);
    }

    @Test
    void reconcileAllKeepsSingleCrateHologramAcrossThreeRestartCycles() throws Exception {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "legendary", "Legendary");
        CrateLocation location = new CrateLocation(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            crate.getKey(),
            Level.OVERWORLD,
            new BlockPos(10, 64, 10)
        );

        CrateRepository crateRepository = mock(CrateRepository.class);
        when(crateRepository.findByKey(crate.getKey())).thenReturn(Optional.of(crate));

        CrateLocationRepository locationRepository = mock(CrateLocationRepository.class);
        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(locationRepository.findByCrateId(crate.getKey())).thenReturn(List.of(location));

        KeyRepository keyRepository = mock(KeyRepository.class);
        setCrateServiceInstance(new CrateService(crateRepository, locationRepository, keyRepository));

        String hologramId = CrateHologramManager.hologramId(location);

        for (int cycle = 1; cycle <= 3; cycle++) {
            BigBangHologramsManager.getInstance().shutdown();
            clearCrateHologramCache();

            CrateHologramManager.getInstance().reconcileAll();

            List<String> definitions = BigBangHolograms.getApi().getDefinitions().stream()
                .map(definition -> definition.id())
                .toList();

            assertEquals(List.of(hologramId), definitions, "unexpected hologram set after restart cycle " + cycle);
            assertEquals(Map.of(location.getId(), hologramId), CrateHologramManager.getInstance().getActiveHolograms());
            assertTrue(BigBangHolograms.getApi().exists(hologramId));
        }
    }

    private static void clearCrateHologramCache() throws Exception {
        Field field = CrateHologramManager.class.getDeclaredField("activeHolograms");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, String> activeHolograms = (Map<UUID, String>) field.get(CrateHologramManager.getInstance());
        activeHolograms.clear();
    }

    private static void setCrateServiceInstance(CrateService value) throws Exception {
        Field field = CrateService.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, value);
    }
}
