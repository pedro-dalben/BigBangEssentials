package com.pedrodalben.bigbangessentials.jobs.progression;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.api.rankup.RankupAPI;
import com.pedrodalben.bigbangessentials.api.rankup.RankDefinition;
import com.pedrodalben.bigbangessentials.api.rankup.RankTransitionCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing player rank milestone unlocks and progression standing.
 */
public class JobRankMilestoneService implements JobRankProgressionProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobRankMilestoneService.class);
    private static final JobRankMilestoneService INSTANCE = new JobRankMilestoneService();

    private final JobRankMilestoneRepository repository = new JobRankMilestoneRepository();
    private final Map<UUID, Set<String>> milestoneCache = new ConcurrentHashMap<>();

    public static JobRankMilestoneService getInstance() {
        return INSTANCE;
    }

    private JobRankMilestoneService() {}

    public void initListener() {
        try {
            RankupAPI.get().addRankTransitionListener(this::onRankTransition);
            LOGGER.info("Registered RankUp transition listener for Jobs milestones.");
        } catch (IllegalStateException e) {
            LOGGER.warn("RankupAPI provider not found, cannot register transition listener.");
        }
    }

    private void onRankTransition(RankTransitionCompletedEvent event) {
        LOGGER.debug("Rank changed for player {}, synchronizing milestones...", event.playerId());
        synchronizeMilestones(event.playerId()).thenAccept(milestones -> {
            LOGGER.debug("Synchronized milestones for player {}: {}", event.playerId(), milestones);
        });
    }

    public void loadPlayer(UUID playerId) {
        synchronizeMilestones(playerId).exceptionally(e -> {
            LOGGER.error("Failed to load/synchronize milestones for {}", playerId, e);
            return Collections.emptySet();
        });
    }

    public void unloadPlayer(UUID playerId) {
        milestoneCache.remove(playerId);
    }

    public void shutdown() {
        milestoneCache.clear();
    }

    @Override
    public CompletableFuture<RankProgressionSnapshot> getProgression(UUID playerId) {
        return synchronizeMilestones(playerId).thenApply(unlocked -> {
            Optional<RankDefinition> rankOpt;
            try {
                rankOpt = RankupAPI.get().getCurrentRank(playerId);
            } catch (IllegalStateException e) {
                rankOpt = Optional.empty();
            }
            String rankId = rankOpt.map(RankDefinition::id).orElse("starter");
            String rankName = rankOpt.map(RankDefinition::displayName).orElse("Starter");
            int rankOrder = rankOpt.map(RankDefinition::order).orElse(0);
            return new RankProgressionSnapshot(playerId, rankId, rankName, rankOrder, unlocked);
        });
    }

    @Override
    public CompletableFuture<Set<String>> synchronizeMilestones(UUID playerId) {
        return repository.loadPlayerMilestones(playerId).thenCompose(dbMilestones -> {
            Set<String> unlocked = new HashSet<>(dbMilestones);
            Optional<RankDefinition> rankOpt;
            try {
                rankOpt = RankupAPI.get().getCurrentRank(playerId);
            } catch (IllegalStateException e) {
                rankOpt = Optional.empty();
            }
            int currentOrder = rankOpt.map(RankDefinition::order).orElse(0);
            String currentRankId = rankOpt.map(RankDefinition::id).orElse("");

            JobsConfig config = JobsManager.getInstance().getConfig();
            if (config != null) {
                List<CompletableFuture<Void>> saveFutures = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (RankMilestoneDefinition def : config.getRankMilestones().values()) {
                    boolean eligible = currentOrder >= def.requiredRankOrder() || currentRankId.equalsIgnoreCase(def.requiredRankId());
                    if (eligible && !unlocked.contains(def.id().toLowerCase())) {
                        unlocked.add(def.id().toLowerCase());
                        saveFutures.add(repository.savePlayerMilestone(playerId, def.id().toLowerCase(), currentRankId, now));
                    }
                }
                if (!saveFutures.isEmpty()) {
                    return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> {
                                milestoneCache.put(playerId, unlocked);
                                return unlocked;
                            });
                }
            }
            milestoneCache.put(playerId, unlocked);
            return CompletableFuture.completedFuture(unlocked);
        });
    }

    @Override
    public boolean hasReachedMilestone(UUID playerId, String milestoneId) {
        if (milestoneId == null || milestoneId.isBlank()) return true;
        Set<String> unlocked = milestoneCache.get(playerId);
        if (unlocked != null && unlocked.contains(milestoneId.toLowerCase())) {
            return true;
        }
        Optional<RankDefinition> rankOpt;
        try {
            rankOpt = RankupAPI.get().getCurrentRank(playerId);
        } catch (IllegalStateException e) {
            rankOpt = Optional.empty();
        }
        int currentOrder = rankOpt.map(RankDefinition::order).orElse(0);
        String currentRankId = rankOpt.map(RankDefinition::id).orElse("");
        Optional<RankMilestoneDefinition> defOpt = getMilestoneDefinition(milestoneId);
        return defOpt.map(def -> currentOrder >= def.requiredRankOrder() || currentRankId.equalsIgnoreCase(def.requiredRankId())).orElse(false);
    }

    @Override
    public Optional<RankMilestoneDefinition> getMilestoneDefinition(String milestoneId) {
        if (milestoneId == null) return Optional.empty();
        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config == null) return Optional.empty();
        return Optional.ofNullable(config.getRankMilestones().get(milestoneId.toLowerCase()));
    }

    public boolean isAtOrAboveRank(UUID playerId, String requiredRankId) {
        if (requiredRankId == null || requiredRankId.isBlank()) return true;
        Optional<RankDefinition> rankOpt;
        try {
            rankOpt = RankupAPI.get().getCurrentRank(playerId);
        } catch (IllegalStateException e) {
            return false;
        }
        if (rankOpt.isEmpty()) return false;
        int currentOrder = rankOpt.get().order();
        String currentRankId = rankOpt.get().id();

        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config != null) {
            Optional<RankMilestoneDefinition> defOpt = getMilestoneDefinition(requiredRankId);
            if (defOpt.isPresent()) {
                RankMilestoneDefinition def = defOpt.get();
                return currentOrder >= def.requiredRankOrder() || currentRankId.equalsIgnoreCase(def.requiredRankId());
            }
        }
        return currentRankId.equalsIgnoreCase(requiredRankId);
    }
}
