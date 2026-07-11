package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import com.pedrodalben.bigbangessentials.permissions.LuckPermsAdapter;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RankupLuckPermsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupLuckPermsService.class);
    private static final long USER_LOAD_TIMEOUT = 5;

    public RankupRank resolveCurrentRank(UUID uuid, RankupConfig config) {
        if (config == null) return null;
        RankupRankResolutionResult result = resolveRankResolution(uuid, config);
        return result.rank();
    }

    public RankupRankResolutionResult resolveRankResolution(UUID uuid, RankupConfig config) {
        if (config == null) {
            return RankupRankResolutionResult.configurationError("No active RankUp configuration");
        }

        ExternalPermissionAdapter adapter = getExternalPermissionAdapter();
        if (adapter == null || !adapter.isAvailable()) {
            return RankupRankResolutionResult.integrationUnavailable(
                    config.getInitialRank(),
                    "LuckPerms is not loaded or available; defaulting to initial rank"
            );
        }

        String primaryGroup = adapter.getPrimaryGroup(uuid);
        Set<String> allGroups = new HashSet<>(adapter.getInheritedGroups(uuid));
        if (primaryGroup != null && !primaryGroup.isBlank()) {
            allGroups.add(primaryGroup.toLowerCase());
        }

        if (allGroups.isEmpty()) {
            return RankupRankResolutionResult.uninitialized(
                    config.getInitialRank(),
                    "Player has no groups assigned in LuckPerms; defaulting to initial rank"
            );
        }

        // Find the highest rank order on the ladder among all groups the player has
        RankupRank highestRank = null;
        for (RankupRank rank : config.getOrderedRanks()) {
            String rankGroup = rank.luckPerms().group().toLowerCase();
            if (allGroups.contains(rankGroup)) {
                if (highestRank == null || rank.order() > highestRank.order()) {
                    highestRank = rank;
                }
            }
        }

        if (highestRank != null) {
            return RankupRankResolutionResult.resolved(highestRank, highestRank.luckPerms().group());
        }

        // Player's groups do not match any rank on our ladder
        // Check if player only has "default"
        if (allGroups.size() == 1 && allGroups.contains("default")) {
            return RankupRankResolutionResult.uninitialized(
                    config.getInitialRank(),
                    "Player only has default group; assigning initial rank"
            );
        }

        return RankupRankResolutionResult.externalGroup(
                config.getInitialRank(),
                primaryGroup != null ? primaryGroup : allGroups.iterator().next(),
                "Player group does not belong to RankUp ladder; fallback to initial rank"
        );
    }

    public String resolveCurrentGroup(UUID uuid) {
        ExternalPermissionAdapter adapter = getExternalPermissionAdapter();
        if (adapter == null) return null;
        return adapter.getPrimaryGroup(uuid);
    }

    public CompletableFuture<RankupGroupMutationResult> applyRankChange(UUID uuid, RankupRank fromRank, RankupRank toRank,
                                                                        RankupConfig config) {
        ExternalPermissionAdapter extAdapter = getExternalPermissionAdapter();
        if (!(extAdapter instanceof LuckPermsAdapter lpAdapter)) {
            return CompletableFuture.completedFuture(RankupGroupMutationResult.failure("LuckPerms adapter not available"));
        }
        LuckPerms api = lpAdapter.getApi();
        if (api == null) {
            return CompletableFuture.completedFuture(RankupGroupMutationResult.failure("LuckPerms API not available"));
        }

        return loadUser(api, uuid).thenCompose(user -> {
            if (user == null) {
                return CompletableFuture.completedFuture(RankupGroupMutationResult.failure("Could not load LuckPerms user"));
            }

            RankupPromotionMode mode = toRank.luckPerms().mode() != null ? toRank.luckPerms().mode() : config.getLadder().luckPermsMode();
            Set<String> ladderGroups = new HashSet<>();
            for (RankupRank rank : config.getRanks().values()) {
                ladderGroups.add(rank.luckPerms().group().toLowerCase());
            }

            try {
                // Remove ladder groups
                user.data().clear(node -> {
                    if (!(node instanceof InheritanceNode inheritance)) return false;
                    String groupName = inheritance.getGroupName();
                    return ladderGroups.contains(groupName.toLowerCase());
                });

                // Add destination group
                Group targetGroup = api.getGroupManager().getGroup(toRank.luckPerms().group());
                if (targetGroup == null) {
                    LOGGER.warn("LuckPerms group '{}' does not exist; creating it to complete RankUp promotion", toRank.luckPerms().group());
                    try {
                        api.getGroupManager().createAndLoadGroup(toRank.luckPerms().group()).get(USER_LOAD_TIMEOUT, TimeUnit.SECONDS);
                        targetGroup = api.getGroupManager().getGroup(toRank.luckPerms().group());
                    } catch (Exception e) {
                        return CompletableFuture.completedFuture(RankupGroupMutationResult.failure("Target LuckPerms group does not exist and could not be created: " + e.getMessage()));
                    }
                }
                user.data().add(InheritanceNode.builder(targetGroup).build());

                if (toRank.luckPerms().setAsPrimaryGroup() || mode == RankupPromotionMode.SET_PRIMARY_GROUP) {
                    user.setPrimaryGroup(toRank.luckPerms().group());
                }

                return api.getUserManager().saveUser(user)
                        .thenApply(v -> RankupGroupMutationResult.ok())
                        .exceptionally(e -> {
                            LOGGER.error("Failed to save LuckPerms user {}", uuid, e);
                            return RankupGroupMutationResult.failure("LuckPerms save failed: " + e.getMessage());
                        });
            } catch (Exception e) {
                LOGGER.error("Error mutating LuckPerms groups for {}", uuid, e);
                return CompletableFuture.completedFuture(RankupGroupMutationResult.failure(e.getMessage()));
            }
        });
    }

    private CompletableFuture<User> loadUser(LuckPerms api, UUID uuid) {
        User cached = api.getUserManager().getUser(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return api.getUserManager().loadUser(uuid)
                .orTimeout(USER_LOAD_TIMEOUT, TimeUnit.SECONDS)
                .exceptionally(e -> {
                    LOGGER.error("Failed to load LuckPerms user {}", uuid, e);
                    return null;
                });
    }

    private ExternalPermissionAdapter getExternalPermissionAdapter() {
        ExternalPermissionAdapter adapter = PermissionAPI.getExternalAdapter();
        if (adapter != null) {
            return adapter;
        }
        if (Platform.isModLoaded("luckperms")) {
            return new LuckPermsAdapter();
        }
        return null;
    }
}
