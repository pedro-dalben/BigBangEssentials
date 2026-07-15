package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PastureJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(PastureJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";

    private IntegrationStatus status;

    @Override
    public String integrationId() { return "cobblemon_pasture"; }

    @Override
    public String requiredModId() { return "cobblemon_pasture"; }

    @Override
    public String[] supportedActionTypes() {
        return new String[]{"PASTURE_TASK_COMPLETED"};
    }

    @Override
    public IntegrationStatus probeApi() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean pastureLoaded = Platform.isModLoaded("cobblemon_pasture")
                || Platform.isModLoaded("pastures")
                || Platform.isModLoaded("cobblemon_pastures");

        if (!baseLoaded) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, "none",
                    "Cobblemon base mod not found",
                    List.of(), List.of("PASTURE_TASK_COMPLETED"));
            return status;
        }

        if (!pastureLoaded) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, "none",
                    "No Pasture addon detected. Pasture Keeper job available only via contract-based progress (menu delivery). No real-time event listeners are registered.",
                    List.of(), List.of("PASTURE_TASK_COMPLETED"));
            LOGGER.info("[Jobs Compat] Pasture mod not installed. Pasture job relies on contract delivery system only.");
        } else {
            status = new IntegrationStatus(
                    integrationId(), IntegrationState.API_FOUND, "cobblemon_pasture",
                    "unknown", "1.5+",
                    "Pasture mod detected but no known subscription API found. Pending implementation.",
                    List.of("PASTURE_TASK_COMPLETED"), List.of(),
                    "N/A", "N/A",
                    "NOT_SUBSCRIBED", "NONE",
                    0L, 0L, 0L, 0L, 0L, null, 0L,
                    "No known event bus for pasture events.", false
            );
        }
        return status;
    }

    @Override
    public SubscriptionResult subscribeEvents() {
        if (status == null) probeApi();

        if (status.state() == IntegrationState.API_FOUND) {
            LOGGER.info("[Jobs Compat] Pasture bridge: no event subscription available. Progress via contracts only.");
        }

        return new SubscriptionResult(false, "N/A", "N/A", false, "NONE",
                "No event subscription implemented for Pasture. Paste collection occurs via contract delivery (PastureCollectionService).",
                null, false, null);
    }

    @Override
    public void shutdown() {
        status = (status != null) ? status.withState(IntegrationState.SHUTDOWN) : null;
    }

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : probeApi();
    }
}
