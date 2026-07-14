package com.pedrodalben.bigbangessentials.menu.integration.jobs;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityResult;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityService;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityStatus;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.favorite.JobFavoriteService;
import com.pedrodalben.bigbangessentials.jobs.menu.JobMenuViewModel;
import com.pedrodalben.bigbangessentials.jobs.menu.JobMenuViewModelFactory;
import com.pedrodalben.bigbangessentials.jobs.progressbar.ProgressBarComponent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public final class JobsMenuSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsMenuSupport.class);

    private JobsMenuSupport() {}

    public static List<JobDefinition> getSortedJobs() {
        return getSortedJobs("name", true);
    }

    public static List<JobDefinition> getSortedJobs(String sortField, boolean ascending) {
        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config == null) {
            LOGGER.warn("getSortedJobs() config is null, returning empty list");
            return Collections.emptyList();
        }
        List<JobDefinition> list = new ArrayList<>(config.getProfessions().values());
        LOGGER.debug("getSortedJobs() config profession count = {}", list.size());
        Comparator<JobDefinition> comparator = switch (sortField.toLowerCase(Locale.ROOT)) {
            case "name", "displayname" -> Comparator.comparing(j -> j.displayName, String.CASE_INSENSITIVE_ORDER);
            case "id" -> Comparator.comparing(j -> j.id, String.CASE_INSENSITIVE_ORDER);
            case "sortorder" -> Comparator.comparingInt(j -> j.sortOrder);
            case "category" -> Comparator.comparing(j -> j.category, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(j -> j.displayName, String.CASE_INSENSITIVE_ORDER);
        };
        if (!ascending) comparator = comparator.reversed();
        list.sort(comparator);
        return list;
    }

    public static String getProgressBarString(double current, double max, int width) {
        return ProgressBarComponent.getInstance().render(current, max, width).getString();
    }

    public static Map<String, Object> buildJobPlaceholders(ServerPlayer player, JobDefinition job) {
        JobAvailabilityResult avail;
        try {
            avail = JobAvailabilityService.getInstance().evaluate(player, job);
        } catch (Exception e) {
            avail = JobAvailabilityResult.builder(job.id)
                .status(JobAvailabilityStatus.CONFIGURATION_ERROR)
                .visible(true).canOpenDetails(false)
                .primaryReason("Error: " + e.getMessage())
                .build();
        }
        LOGGER.debug("buildJobPlaceholders() jobId={} availStatus={} availVisible={} availPrimaryReason={}",
                job.id, avail.status(), avail.visible(), avail.primaryReason());
        JobMenuViewModel viewModel;
        try {
            viewModel = JobMenuViewModelFactory.getInstance().create(player, job);
        } catch (Exception e) {
            viewModel = new JobMenuViewModel(
                job.id, null, null, JobAvailabilityStatus.CONFIGURATION_ERROR, null,
                1, 0, 100, 0.0, null, null,
                false, false, false, false, false, List.of(), null
            );
        }

        Map<String, Object> values = new LinkedHashMap<>();
        PlayerJobsData data = player != null ? JobsManager.getInstance().getPlayerData(player.getUUID()) : null;

        int level = viewModel.level();
        double xp = viewModel.currentXp();
        double reqXp = viewModel.requiredXp();
        boolean isActive = viewModel.active();
        double earnings = viewModel.earningsToday() != null ? viewModel.earningsToday().doubleValue() : 0.0;
        double maxDaily = viewModel.dailyLimit() != null ? viewModel.dailyLimit().doubleValue() : 50000.0;
        boolean favorite = viewModel.favorite();

        String statusColor = switch (avail.status()) {
            case ACTIVE -> "<green>";
            case AVAILABLE -> "<dark_green>";
            case LOCKED, PERMISSION_REQUIRED, RANK_REQUIRED, NO_AVAILABLE_SLOT -> "<red>";
            case LICENSE_REQUIRED -> "<yellow>";
            case COOLDOWN -> "<gray>";
            case INTEGRATION_UNAVAILABLE, CONFIGURATION_ERROR, ADMIN_DISABLED -> "<dark_gray>";
        };
        String statusKey = avail.status().name().toLowerCase(Locale.ROOT);
        String statusLabel = viewModel.statusText() != null ? viewModel.statusText().getString() : statusKey;

        String licLabel;
        if (isActive) {
            licLabel = "<green>Ativo";
        } else if (avail.canJoin()) {
            licLabel = "<aqua>Disponível para entrar";
        } else if (avail.canStartLicense()) {
            licLabel = "<gold>Licença disponível";
        } else if (avail.status() == JobAvailabilityStatus.RANK_REQUIRED) {
            licLabel = "<red>Bloqueado por Rank";
        } else if (avail.status() == JobAvailabilityStatus.PERMISSION_REQUIRED) {
            licLabel = "<red>Sem Permissão";
        } else {
            licLabel = "<red>Bloqueado";
        }

        String assignedSlot = "Nenhum";
        if (player != null) {
            Map<String, com.pedrodalben.bigbangessentials.jobs.slot.JobSlot> slots =
                com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().getSlots(player.getUUID());
            for (com.pedrodalben.bigbangessentials.jobs.slot.JobSlot slot : slots.values()) {
                if (slot.activeJobId().isPresent() && slot.activeJobId().get().equalsIgnoreCase(job.id)) {
                    assignedSlot = slot.slotType();
                    break;
                }
            }
        }
        String categoryLabel = job.category != null
            ? (job.category.contains("POKEMON") ? "Especialização Pokémon" : "Profissão Comum")
            : "Profissão Comum";

        values.put("job_id", job.id);
        values.put("job_name", job.id);
        values.put("job_display_name", job.displayName);
        values.put("job_description", job.description != null ? job.description : "");
        values.put("job_short_description", job.shortDescription != null ? job.shortDescription : "");
        values.put("job_level", String.valueOf(level));
        values.put("job_xp", String.format(Locale.ROOT, "%.1f", xp));
        values.put("job_xp_required", String.format(Locale.ROOT, "%.1f", reqXp));
        values.put("job_xp_progress_bar", getProgressBarString(xp, reqXp, 20));
        values.put("job_earnings", String.format(Locale.ROOT, "%.2f", earnings));
        values.put("job_limit", String.format(Locale.ROOT, "%.2f", maxDaily));
        values.put("job_status", statusLabel);
        values.put("job_status_color", statusColor);
        values.put("job_status_key", statusKey);
        values.put("job_license_label", licLabel);
        values.put("job_slot_assigned", assignedSlot);
        values.put("job_category_label", categoryLabel);
        values.put("job_icon", job.icon != null && !job.icon.isBlank() ? job.icon : "minecraft:book");
        values.put("job_active", String.valueOf(isActive));
        values.put("job_max_level", String.valueOf(job.maxLevel));
        values.put("job_category", job.category != null ? job.category : "COMMON");

        // New availability placeholders
        values.put("job_availability_status", avail.status().name());
        values.put("job_primary_reason", avail.primaryReason());
        values.put("job_can_join", String.valueOf(avail.canJoin()));
        values.put("job_can_leave", String.valueOf(avail.canLeave()));
        values.put("job_favorite", String.valueOf(favorite));

        // How to earn
        if (job.howToEarn != null) {
            values.put("job_earn_money_header", job.howToEarn.moneyHeader);
            values.put("job_earn_xp_header", job.howToEarn.xpHeader);
            if (!job.howToEarn.moneyLines.isEmpty()) {
                values.put("job_earn_money_lines", String.join("\n", job.howToEarn.moneyLines));
                for (int i = 0; i < job.howToEarn.moneyLines.size(); i++) {
                    values.put("job_earn_money_line_" + (i + 1), job.howToEarn.moneyLines.get(i));
                }
            }
            if (!job.howToEarn.xpLines.isEmpty()) {
                values.put("job_earn_xp_lines", String.join("\n", job.howToEarn.xpLines));
                for (int i = 0; i < job.howToEarn.xpLines.size(); i++) {
                    values.put("job_earn_xp_line_" + (i + 1), job.howToEarn.xpLines.get(i));
                }
            }
        }

        // License objectives
        if (job.licenseRequired && !job.licenseObjectives.isEmpty()) {
            values.put("job_license_objectives_count", String.valueOf(job.licenseObjectives.size()));
        }

        // Required integration
        if (job.requiredIntegration != null && !job.requiredIntegration.isBlank()) {
            values.put("job_required_integration", job.requiredIntegration);
        }

        return values;
    }

    public static Map<String, Object> buildSummaryPlaceholders(ServerPlayer player) {
        Map<String, Object> values = new LinkedHashMap<>();
        PlayerJobsData data = player != null ? JobsManager.getInstance().getPlayerData(player.getUUID()) : null;
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        int activeCount = 0;
        int maxActive = cfg != null ? cfg.getMaxActiveJobs() : 2;
        double totalEarnings = 0.0;
        double globalLimit = cfg != null ? cfg.getDailyLimitGlobal() : 50000.0;
        double vipBonus = 0.0;

        if (player != null) {
            maxActive = JobsManager.getInstance().getMaxActiveJobsForPlayer(player);
            globalLimit *= JobsManager.getInstance().getDailyLimitPermissionMultiplier(player);
            vipBonus = (JobsManager.getInstance().getGanhosPermissionMultiplier(player) - 1.0) * 100.0;
        }

        if (data != null) {
            activeCount = data.getActiveJobsCount();
            totalEarnings = data.getTotalDailyEarnings();
        }

        values.put("jobs_active_count", String.valueOf(activeCount));
        values.put("jobs_max_active", String.valueOf(maxActive));
        values.put("jobs_total_earnings", String.format(Locale.ROOT, "%.2f", totalEarnings));
        values.put("jobs_global_limit", String.format(Locale.ROOT, "%.2f", globalLimit));
        values.put("jobs_vip_bonus", String.format(Locale.ROOT, "+%.0f%%", vipBonus));
        values.put("active_count", String.valueOf(activeCount));
        values.put("max_active", String.valueOf(maxActive));
        values.put("total_earnings", String.format(Locale.ROOT, "%.2f", totalEarnings));
        values.put("global_limit", String.format(Locale.ROOT, "%.2f", globalLimit));
        values.put("vip_bonus", String.format(Locale.ROOT, "+%.0f%%", vipBonus));

        return values;
    }

    public static List<JobDefinition> filterByFavorite(ServerPlayer player, List<JobDefinition> jobs) {
        if (player == null) return Collections.emptyList();
        Set<String> favs = JobFavoriteService.getInstance().getFavorites(player.getUUID());
        return jobs.stream()
            .filter(j -> favs.contains(j.id.toLowerCase()))
            .collect(Collectors.toList());
    }

    public static List<JobDefinition> filterByAvailability(ServerPlayer player, List<JobDefinition> jobs, JobAvailabilityStatus status) {
        return jobs.stream()
            .filter(j -> JobAvailabilityService.getInstance().evaluate(player, j).status() == status)
            .collect(Collectors.toList());
    }

    public static List<JobDefinition> filterByCategory(List<JobDefinition> jobs, String category) {
        return jobs.stream()
            .filter(j -> category == null || category.equalsIgnoreCase(j.category))
            .collect(Collectors.toList());
    }

    public static List<JobDefinition> sortByFavorites(ServerPlayer player, List<JobDefinition> jobs) {
        if (player == null) return jobs;
        Set<String> favs = JobFavoriteService.getInstance().getFavorites(player.getUUID());
        List<JobDefinition> sorted = new ArrayList<>(jobs);
        sorted.sort((a, b) -> {
            boolean aFav = favs.contains(a.id.toLowerCase());
            boolean bFav = favs.contains(b.id.toLowerCase());
            if (aFav == bFav) return 0;
            return aFav ? -1 : 1;
        });
        return sorted;
    }
}
