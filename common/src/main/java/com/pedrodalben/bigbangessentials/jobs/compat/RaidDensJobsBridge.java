package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RaidDensJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaidDensJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";
    private static final String RAIDS_ID = "raiddens";
    private IntegrationStatus status;

    @Override
    public String integrationId() {
        return "cobblemon_raids";
    }

    @Override
    public IntegrationStatus initialize() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean raidsLoaded = Platform.isModLoaded(RAIDS_ID) || Platform.isModLoaded("cobblemon_raids") || Platform.isModLoaded("cobbleradiant") || Platform.isModLoaded("cobblemondens");

        if (!baseLoaded) {
            status = new IntegrationStatus(integrationId(), IntegrationState.DISABLED_NOT_INSTALLED, "none", "N/A", "1.0+", "Cobblemon ausente", List.of(), List.of("RAID_CLEARED"));
            return status;
        }

        if (!raidsLoaded) {
            status = new IntegrationStatus(
                    integrationId(),
                    IntegrationState.DISABLED_NOT_INSTALLED,
                    "none",
                    "N/A",
                    "1.0+",
                    "Mod de Raid Dens/Radiant não instalado no ambiente runtime. Job Especialista em Raids aguardando mod ou eventos.",
                    List.of(),
                    List.of("RAID_CLEARED")
            );
            return status;
        }

        status = new IntegrationStatus(
                integrationId(),
                IntegrationState.ACTIVE,
                RAIDS_ID,
                "1.0+",
                "1.0+",
                "Mod Raid Dens detectado. Suporte a conclusão em cooperação e chaves de especialista habilitado.",
                List.of("RAID_CLEARED"),
                List.of()
        );
        return status;
    }

    @Override
    public void registerListeners() {
        if (status == null || !status.isOperational()) return;
        LOGGER.info("Raid Dens bridge active. Ready to process raid clearances.");
    }

    @Override
    public void shutdown() {}

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : initialize();
    }
}
