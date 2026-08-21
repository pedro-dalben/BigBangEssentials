package com.pedrodalben.bigbangessentials.jobs.rewards;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class JourneyFragmentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JourneyFragmentService.class);
    private static final JourneyFragmentService INSTANCE = new JourneyFragmentService();

    public static JourneyFragmentService getInstance() {
        return INSTANCE;
    }

    private JourneyFragmentService() {}

    public long getBalance(UUID playerId) {
        return JourneyFragmentRepository.getInstance().getBalance(playerId, RewardType.JOURNEY_FRAGMENT.name());
    }

    public long addFragments(UUID playerId, long amount, String sourceType, String sourceRefId, String actionId, String contractId, String rankMilestoneId, String metadata) {
        if (playerId == null || amount <= 0) return getBalance(playerId);
        long newBalance = JourneyFragmentRepository.getInstance().modifyBalance(
            playerId, RewardType.JOURNEY_FRAGMENT.name(), amount, sourceType, sourceRefId, actionId, contractId, rankMilestoneId, metadata
        );
        if (newBalance >= 0) {
            LOGGER.debug("Player {} gained {} Journey Fragments (source: {}, ref: {}). New balance: {}", playerId, amount, sourceType, sourceRefId, newBalance);
            JobRewardNotificationService.getInstance().notifyFragmentsGained(playerId, amount, newBalance);
        }
        return newBalance;
    }

    public boolean removeFragments(UUID playerId, long amount, String sourceType, String sourceRefId, String metadata) {
        if (playerId == null || amount <= 0) return false;
        long current = getBalance(playerId);
        if (current < amount) return false;

        long newBalance = JourneyFragmentRepository.getInstance().modifyBalance(
            playerId, RewardType.JOURNEY_FRAGMENT.name(), -amount, sourceType, sourceRefId, null, null, null, metadata
        );
        if (newBalance >= 0) {
            LOGGER.debug("Player {} spent {} Journey Fragments (source: {}, ref: {}). New balance: {}", playerId, amount, sourceType, sourceRefId, newBalance);
            return true;
        }
        return false;
    }

    public List<JourneyFragmentLedgerEntry> getLedger(UUID playerId, int limit) {
        return JourneyFragmentRepository.getInstance().getLedger(playerId, limit);
    }
}
