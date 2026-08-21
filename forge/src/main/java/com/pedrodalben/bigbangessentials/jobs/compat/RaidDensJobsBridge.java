package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RaidDensJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaidDensJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";

    private IntegrationStatus status;

    @Override
    public String integrationId() { return "cobblemon_raids"; }

    @Override
    public String requiredModId() { return "cobblemon_raids"; }

    @Override
    public String[] supportedActionTypes() {
        return new String[]{"RAID_CLEARED"};
    }

    @Override
    public IntegrationStatus probeApi() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean raidsLoaded = Platform.isModLoaded("raiddens")
                || Platform.isModLoaded("cobblemon_raids")
                || Platform.isModLoaded("cobbleradiant")
                || Platform.isModLoaded("cobblemondens");

        if (!baseLoaded) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, "none",
                    "Cobblemon base mod not found",
                    List.of(), List.of("RAID_CLEARED"));
            return status;
        }

        if (!raidsLoaded) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, "none",
                    "No Raid Dens/Radiant/Dens addon detected. Raid integration unavailable. Raider job has no live events.",
                    List.of(), List.of("RAID_CLEARED"));
        } else {
            status = new IntegrationStatus(
                    integrationId(), IntegrationState.API_FOUND, "raiddens",
                    "unknown", "1.0+",
                    "Raid mod detected but no known subscription API found. Pending implementation.",
                    List.of("RAID_CLEARED"), List.of(),
                    "N/A", "N/A",
                    "NOT_SUBSCRIBED", "NONE",
                    0L, 0L, 0L, 0L, 0L, null, 0L,
                    "No known event bus for raid completion events.", false
            );
        }
        return status;
    }

    @Override
    public SubscriptionResult subscribeEvents() {
        if (status == null) probeApi();

        if (status.state() == IntegrationState.API_FOUND) {
            LOGGER.info("[Jobs Compat] Raid Dens bridge: no event subscription available. Progress via contract/"
                    + "RaidDeduplicationService only.");
        }

        return new SubscriptionResult(false, "N/A", "N/A", false, "NONE",
                "No event subscription implemented for Raids. Raid clearance processing occurs via service calls from external triggers (if any exist).",
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
