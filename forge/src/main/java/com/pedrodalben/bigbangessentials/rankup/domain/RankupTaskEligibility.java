package com.pedrodalben.bigbangessentials.rankup.domain;

/**
 * Detailed eligibility evaluation for an individual rank requirement task.
 */
public record RankupTaskEligibility(
        RankupTask task,
        int progress,
        int target,
        int effectiveProgress,
        double percentage,
        boolean completed,
        String filterSummary
) {
    public static RankupTaskEligibility evaluate(RankupTask task, int rawProgress) {
        int target = Math.max(1, task.target());
        int progress = Math.max(0, rawProgress);
        int effectiveProgress = Math.min(progress, target);
        double percentage = Math.min(100.0, (effectiveProgress * 100.0) / target);
        boolean completed = progress >= target;
        String filterSummary = buildFilterSummary(task);

        return new RankupTaskEligibility(
                task,
                progress,
                target,
                effectiveProgress,
                percentage,
                completed,
                filterSummary
        );
    }

    private static String buildFilterSummary(RankupTask task) {
        if (task.filters() == null) return "Any";
        RankupTaskFilter f = task.filters();
        switch (task.type()) {
            case BREAK_BLOCK, PLACE_BLOCK -> {
                if (f.blocks() != null && !f.blocks().isEmpty()) {
                    return String.join(", ", f.blocks());
                }
            }
            case KILL_ENTITY -> {
                if (f.entities() != null && !f.entities().isEmpty()) {
                    return String.join(", ", f.entities());
                }
            }
            case FISH, CRAFT_ITEM, SMELT_ITEM -> {
                if (f.items() != null && !f.items().isEmpty()) {
                    return String.join(", ", f.items());
                }
            }
            case VISIT_BIOME -> {
                if (f.biomes() != null && !f.biomes().isEmpty()) {
                    return String.join(", ", f.biomes());
                }
            }
            case ADVANCEMENT -> {
                if (f.advancements() != null && !f.advancements().isEmpty()) {
                    return String.join(", ", f.advancements());
                }
            }
            case COBBLEMON_CAPTURE, COBBLEMON_DEFEAT, COBBLEMON_BATTLE_WIN, COBBLEMON_HATCH_EGG -> {
                if (f.species() != null && !f.species().isEmpty()) {
                    return "Species: " + String.join(", ", f.species());
                } else if (f.types() != null && !f.types().isEmpty()) {
                    return "Types: " + String.join(", ", f.types());
                } else if (Boolean.TRUE.equals(f.legendary())) {
                    return "Legendary Cobblemon";
                } else if (Boolean.TRUE.equals(f.shiny())) {
                    return "Shiny Cobblemon";
                }
            }
            default -> {}
        }
        return "Any";
    }
}
