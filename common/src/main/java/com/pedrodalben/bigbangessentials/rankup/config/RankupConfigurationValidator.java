package com.pedrodalben.bigbangessentials.rankup.config;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.objectives.ObjectiveTargetMatcher;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RankupConfigurationValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupConfigurationValidator.class);

    public static RankupValidationResult validate(RankupConfig config) {
        RankupValidationResult result = new RankupValidationResult();
        if (config == null) {
            result.addError("Configuration is null");
            return result;
        }

        validateLadder(config.getLadder(), config, result);

        Set<String> rankIds = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        Set<String> groups = new HashSet<>();
        boolean hasInitial = false;

        List<RankupRank> ordered = config.getOrderedRanks();
        for (RankupRank rank : ordered) {
            if (rank.id() == null || rank.id().isBlank()) {
                result.addError("Rank ID is empty");
                continue;
            }
            if (!rankIds.add(rank.id().toLowerCase())) {
                result.addError("Duplicate rank ID: " + rank.id());
            }
            if (!orders.add(rank.order())) {
                result.addError("Duplicate rank order: " + rank.order() + " (rank " + rank.id() + ")");
            }
            if (rank.luckPerms() == null || rank.luckPerms().group() == null || rank.luckPerms().group().isBlank()) {
                result.addError("Rank '" + rank.id() + "' is missing LuckPerms group");
            } else {
                String group = rank.luckPerms().group().toLowerCase();
                if (!groups.add(group)) {
                    result.addError("Duplicate LuckPerms group in ladder: " + rank.luckPerms().group());
                }
            }
            if (rank.requirements().money().compareTo(java.math.BigDecimal.ZERO) < 0) {
                result.addError("Rank '" + rank.id() + "' has negative money requirement");
            }
            if (rank.requirements().gems() < 0) {
                result.addError("Rank '" + rank.id() + "' has negative gems requirement");
            }
            if (config.getLadder().initialRankId().equalsIgnoreCase(rank.id())) {
                hasInitial = true;
            }
            validateTasks(rank, result);
        }

        if (!hasInitial && !config.getRanks().isEmpty()) {
            result.addError("Initial rank '" + config.getLadder().initialRankId() + "' not found");
        }

        // Validate ordering continuity: orders should start at 0 and be contiguous
        int expected = 0;
        for (RankupRank rank : ordered) {
            if (rank.order() != expected) {
                result.addWarning("Rank order is not contiguous at expected index " + expected + " for rank " + rank.id());
            }
            expected++;
        }

        return result;
    }

    private static void validateLadder(RankupLadder ladder, RankupConfig config, RankupValidationResult result) {
        if (ladder == null) {
            result.addError("Ladder is missing");
            return;
        }
        if (ladder.id() == null || ladder.id().isBlank()) {
            result.addError("Ladder ID is empty");
        }
        if (ladder.initialRankId() == null || ladder.initialRankId().isBlank()) {
            result.addError("Ladder initial-rank-id is empty");
        }
    }

    private static void validateTasks(RankupRank rank, RankupValidationResult result) {
        Set<String> taskIds = new HashSet<>();
        for (RankupTask task : rank.requirements().tasks()) {
            if (task.id() == null || task.id().isBlank()) {
                result.addError("Rank '" + rank.id() + "' has a task with empty ID");
                continue;
            }
            if (!taskIds.add(task.id().toLowerCase())) {
                result.addError("Rank '" + rank.id() + "' has duplicate task ID: " + task.id());
            }
            if (task.type() == ObjectiveActionType.UNKNOWN) {
                result.addError("Rank '" + rank.id() + "' task '" + task.id() + "' has unknown type");
            }
            if (task.target() < 0) {
                result.addError("Rank '" + rank.id() + "' task '" + task.id() + "' has invalid target amount");
            }

            if (task.type() == ObjectiveActionType.COBBLEMON_DEFEAT) {
                result.addError("Rank '" + rank.id() + "' task '" + task.id() + "' uses COBBLEMON_DEFEAT which is currently unsupported due to insecure player win attribution.");
            }

            if (isCobblemonType(task.type())) {
                boolean cobblemonLoaded = Platform.isModLoaded("cobblemon");
                if (!cobblemonLoaded) {
                    result.addWarning("Rank '" + rank.id() + "' task '" + task.id() + "' uses Cobblemon type " +
                            task.type().configName() + " but Cobblemon is not loaded");
                }
            }

            validateFilters(rank, task, result);
        }
    }

    private static void validateFilters(RankupRank rank, RankupTask task, RankupValidationResult result) {
        RankupTaskFilter f = task.filters();
        switch (task.type()) {
            case BREAK_BLOCK -> validateRegistryList(rank, task, f.blocks(), "block", result);
            case PLACE_BLOCK -> validateRegistryList(rank, task, f.blocks(), "block", result);
            case KILL_ENTITY -> validateRegistryList(rank, task, f.entities(), "entity", result);
            case FISH -> validateRegistryList(rank, task, f.items(), "item", result);
            case CRAFT_ITEM, SMELT_ITEM -> validateRegistryList(rank, task, f.items(), "item", result);
            case VISIT_BIOME -> validateRegistryList(rank, task, f.biomes(), "biome", result);
            case ADVANCEMENT -> {
                if (f.advancements() == null || f.advancements().isEmpty()) {
                    result.addWarning("Rank '" + rank.id() + "' task '" + task.id() + "' has no advancement filter");
                }
            }
            case COBBLEMON_CAPTURE, COBBLEMON_DEFEAT -> {
                if ((f.species() == null || f.species().isEmpty()) && (f.types() == null || f.types().isEmpty())
                        && f.legendary() == null && f.shiny() == null) {
                    result.addWarning("Rank '" + rank.id() + "' task '" + task.id() + "' has no Cobblemon filters");
                }
            }
            default -> {}
        }
    }

    private static void validateRegistryList(RankupRank rank, RankupTask task, List<String> list, String kind, RankupValidationResult result) {
        if (list == null || list.isEmpty()) {
            result.addWarning("Rank '" + rank.id() + "' task '" + task.id() + "' has no " + kind + " filters");
            return;
        }
        for (String entry : list) {
            if (entry.startsWith("#")) {
                if (!ObjectiveTargetMatcher.isValidTagSyntax(entry)) {
                    result.addError("Rank '" + rank.id() + "' task '" + task.id() + "' has invalid tag syntax: " + entry);
                }
            } else {
                if (ObjectiveTargetMatcher.parse(entry) == null) {
                    result.addError("Rank '" + rank.id() + "' task '" + task.id() + "' has invalid registry ID: " + entry);
                } else {
                    boolean found = switch (kind) {
                        case "block" -> BuiltInRegistries.BLOCK != null && BuiltInRegistries.BLOCK.containsKey(ObjectiveTargetMatcher.parse(entry));
                        case "entity" -> BuiltInRegistries.ENTITY_TYPE != null && BuiltInRegistries.ENTITY_TYPE.containsKey(ObjectiveTargetMatcher.parse(entry));
                        case "item" -> BuiltInRegistries.ITEM != null && BuiltInRegistries.ITEM.containsKey(ObjectiveTargetMatcher.parse(entry));
                        case "biome" -> true; // Biomes are data-driven; cannot reliably check at this stage
                        default -> true;
                    };
                    if (!found && BuiltInRegistries.BLOCK != null && !BuiltInRegistries.BLOCK.keySet().isEmpty()) {
                        result.addWarning("Rank '" + rank.id() + "' task '" + task.id() + "' unknown " + kind + " registry ID: " + entry);
                    }
                }
            }
        }
    }

    private static boolean isCobblemonType(ObjectiveActionType type) {
        return type == ObjectiveActionType.COBBLEMON_CAPTURE
                || type == ObjectiveActionType.COBBLEMON_BATTLE_WIN
                || type == ObjectiveActionType.COBBLEMON_DEFEAT
                || type == ObjectiveActionType.COBBLEMON_HATCH_EGG;
    }
}
