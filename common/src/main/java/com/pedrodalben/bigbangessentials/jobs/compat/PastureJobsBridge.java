package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PastureJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(PastureJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";
    private static final String PASTURE_ID = "cobblemon_pasture";
    private IntegrationStatus status;

    @Override
    public String integrationId() {
        return "cobblemon_pasture";
    }

    @Override
    public IntegrationStatus initialize() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean pastureLoaded = Platform.isModLoaded(PASTURE_ID) || Platform.isModLoaded("pastures") || Platform.isModLoaded("cobblemon_pastures");

        if (!baseLoaded) {
            status = new IntegrationStatus(integrationId(), IntegrationState.DISABLED_NOT_INSTALLED, "none", "N/A", "1.5+", "Cobblemon ausente", List.of(), List.of("PASTURE_TASK_COMPLETED"));
            return status;
        }

        status = new IntegrationStatus(
                integrationId(),
                pastureLoaded ? IntegrationState.ACTIVE : IntegrationState.DEGRADED,
                pastureLoaded ? PASTURE_ID : COBBLEMON_ID,
                "1.5+",
                "1.5+",
                pastureLoaded ? "Mod Pastures detectado. Modo manual validado e contratos ativos (farm passivo desativado)." : "Mod Pastures ausente. Progresso disponível apenas via contratos de entrega no menu.",
                List.of("PASTURE_TASK_COMPLETED"),
                List.of()
        );
        return status;
    }

    @Override
    public void registerListeners() {
        if (status == null || !status.isOperational()) return;
        LOGGER.info("Pasture bridge active. Ready to process manual collections and contract deliveries.");
    }

    @Override
    public void shutdown() {}

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : initialize();
    }
}
