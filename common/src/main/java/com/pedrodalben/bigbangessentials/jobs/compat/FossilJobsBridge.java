package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FossilJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(FossilJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";
    private static final String FOSSILS_ID = "cobblemon_fossils";
    private IntegrationStatus status;

    @Override
    public String integrationId() {
        return "cobblemon_fossils";
    }

    @Override
    public IntegrationStatus initialize() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean fossilsLoaded = Platform.isModLoaded(FOSSILS_ID) || Platform.isModLoaded("fossils") || Platform.isModLoaded("cobblemon_archaeology");

        if (!baseLoaded) {
            status = new IntegrationStatus(integrationId(), IntegrationState.DISABLED_NOT_INSTALLED, "none", "N/A", "1.5+", "Cobblemon ausente", List.of(), List.of("FOSSIL_REVIVED"));
            return status;
        }

        status = new IntegrationStatus(
                integrationId(),
                fossilsLoaded ? IntegrationState.ACTIVE : IntegrationState.DEGRADED,
                fossilsLoaded ? FOSSILS_ID : COBBLEMON_ID,
                "1.5+",
                "1.5+",
                fossilsLoaded ? "Mod de Fósseis detectado: revivificação em máquina e extração habilitadas" : "Mod de Fósseis ausente. Revivificação operacional via eventos de estação base.",
                List.of("FOSSIL_REVIVED"),
                List.of()
        );
        return status;
    }

    @Override
    public void registerListeners() {
        if (status == null || !status.isOperational()) return;
        LOGGER.info("Fossil bridge active. Ready to process fossil revivals from machines.");
    }

    @Override
    public void shutdown() {}

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : initialize();
    }
}
