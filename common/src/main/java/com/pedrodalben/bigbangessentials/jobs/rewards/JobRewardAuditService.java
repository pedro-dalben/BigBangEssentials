package com.pedrodalben.bigbangessentials.jobs.rewards;

import java.util.List;
import java.util.UUID;

public class JobRewardAuditService {
    private static final JobRewardAuditService INSTANCE = new JobRewardAuditService();

    public static JobRewardAuditService getInstance() {
        return INSTANCE;
    }

    private JobRewardAuditService() {}

    public long inspectFragmentBalance(UUID playerUuid) {
        return JourneyFragmentService.getInstance().getBalance(playerUuid);
    }

    public List<JourneyFragmentLedgerEntry> inspectLedger(UUID playerUuid, int limit) {
        return JourneyFragmentService.getInstance().getLedger(playerUuid, limit);
    }
}
