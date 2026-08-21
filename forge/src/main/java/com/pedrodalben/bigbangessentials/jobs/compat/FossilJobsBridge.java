package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FossilJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(FossilJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";

    private IntegrationStatus status;

    @Override
    public String integrationId() { return "cobblemon_fossils"; }

    @Override
    public String requiredModId() { return "cobblemon_fossils"; }

    @Override
    public String[] supportedActionTypes() {
        return new String[]{"FOSSIL_REVIVED"};
    }

    @Override
    public IntegrationStatus probeApi() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean fossilsLoaded = Platform.isModLoaded("cobblemon_fossils")
                || Platform.isModLoaded("fossils")
                || Platform.isModLoaded("cobblemon_archaeology");

        if (!baseLoaded) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, "none",
                    "Cobblemon base mod not found",
                    List.of(), List.of("FOSSIL_REVIVED"));
            return status;
        }

        if (!fossilsLoaded) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, "none",
                    "No Fossil addon detected. Fossil revival integration unavailable. No real-time event listeners registered.",
                    List.of(), List.of("FOSSIL_REVIVED"));
        } else {
            status = new IntegrationStatus(
                    integrationId(), IntegrationState.API_FOUND, "cobblemon_fossils",
                    "unknown", "1.5+",
                    "Fossil mod detected but no known subscription API found. Pending implementation.",
                    List.of("FOSSIL_REVIVED"), List.of(),
                    "N/A", "N/A",
                    "NOT_SUBSCRIBED", "NONE",
                    0L, 0L, 0L, 0L, 0L, null, 0L,
                    "No known event bus for fossil machine events.", false
            );
        }
        return status;
    }

    @Override
    public SubscriptionResult subscribeEvents() {
        if (status == null) probeApi();

        if (status.state() == IntegrationState.API_FOUND) {
            LOGGER.info("[Jobs Compat] Fossil bridge: no event subscription available. Progress via contract/"
                    + "FossilProcessDeduplicationService only.");
        }

        return new SubscriptionResult(false, "N/A", "N/A", false, "NONE",
                "No event subscription implemented for Fossils. Fossil revival processing occurs via service calls from external triggers (if any exist).",
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
