package com.pedrodalben.bigbangessentials.menu.integration.jobs;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class JobsMenuSupport {
    private JobsMenuSupport() {}

    public static List<JobDefinition> getSortedJobs() {
        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config == null) return Collections.emptyList();
        List<JobDefinition> list = new ArrayList<>(config.getProfessions().values());
        list.sort(Comparator.comparing(j -> j.displayName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public static String getProgressBarString(double current, double max, int width) {
        double percentage = max > 0 ? Math.max(0, Math.min(1, current / max)) : 0;
        int filled = (int) (percentage * width);
        int empty = width - filled;
        
        StringBuilder bar = new StringBuilder();
        if (filled > 0) {
            bar.append("<green>").append("█".repeat(filled));
        }
        if (empty > 0) {
            bar.append("<gray>").append("█".repeat(empty));
        }
        bar.append(" <white>").append(String.format(Locale.ROOT, "%.1f%%", percentage * 100));
        return bar.toString();
    }

    public static String getJobIcon(String jobId) {
        switch (jobId.toLowerCase(Locale.ROOT)) {
            case "miner": return "minecraft:diamond_pickaxe";
            case "woodcutter": return "minecraft:diamond_axe";
            case "farmer": return "minecraft:diamond_hoe";
            case "ranger": return "minecraft:bow";
            case "builder": return "minecraft:bricks";
            case "blacksmith": return "minecraft:anvil";
            case "explorer": return "minecraft:compass";
            case "crafter": return "minecraft:crafting_table";
            case "culinarian": return "minecraft:cake";
            case "magician": return "minecraft:enchanted_book";
            default: return "minecraft:book";
        }
    }

    public static Map<String, Object> buildJobPlaceholders(ServerPlayer player, JobDefinition job) {
        Map<String, Object> values = new LinkedHashMap<>();
        PlayerJobsData data = player != null ? JobsManager.getInstance().getPlayerData(player.getUUID()) : null;
        
        int level = 1;
        double xp = 0.0;
        boolean isActive = false;
        double earnings = 0.0;
        
        if (data != null) {
            JobProgress prog = data.getProgress(job.id);
            if (prog != null) {
                level = prog.getLevel();
                xp = prog.getXp();
                isActive = prog.isActive();
            }
            earnings = data.getDailyEarnings(job.id);
        }
        
        double reqXp = job.getRequiredXp(level);
        double maxDaily = job.maxDailyEarnings >= 0 ? job.maxDailyEarnings : (JobsManager.getInstance().getConfig() != null ? JobsManager.getInstance().getConfig().getDailyLimitGlobal() : 50000.0);
        if (player != null) {
            maxDaily *= JobsManager.getInstance().getDailyLimitPermissionMultiplier(player);
        }

        boolean hasPerm = player == null || PermissionAPI.hasPermission(player.getUUID(), job.permission);
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus licStatus =
                player != null ? com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().getLicenseStatus(player.getUUID(), job.id) : com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.LICENSED;

        String statusLabel;
        String statusColor;
        String statusKey;
        if (isActive) {
            statusLabel = "Ativo no Slot";
            statusColor = "<green>";
            statusKey = "active";
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.LICENSED) {
            statusLabel = "Licenciado";
            statusColor = "<aqua>";
            statusKey = "licensed";
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.READY_TO_CLAIM) {
            statusLabel = "Licença Pronta!";
            statusColor = "<gold>";
            statusKey = "ready_to_claim";
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.IN_PROGRESS) {
            statusLabel = "Em Andamento";
            statusColor = "<yellow>";
            statusKey = "in_progress";
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.ELIGIBLE && hasPerm) {
            statusLabel = "Licença Disponível";
            statusColor = "<yellow>";
            statusKey = "eligible";
        } else {
            statusLabel = "Bloqueado por Rank";
            statusColor = "<red>";
            statusKey = "locked";
        }

        String licLabel;
        if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.LICENSED) {
            licLabel = "<aqua>Licenciado";
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.READY_TO_CLAIM) {
            licLabel = "<gold>Pronta para Resgatar";
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.IN_PROGRESS) {
            licLabel = "<yellow>Em Andamento";
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.ELIGIBLE && hasPerm) {
            licLabel = "<yellow>Disponível";
        } else {
            licLabel = "<red>Bloqueado por Rank";
        }

        String assignedSlot = "Nenhum";
        if (player != null) {
            Map<String, com.pedrodalben.bigbangessentials.jobs.slot.JobSlot> slots = com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().getSlots(player.getUUID());
            for (com.pedrodalben.bigbangessentials.jobs.slot.JobSlot slot : slots.values()) {
                if (slot.activeJobId().isPresent() && slot.activeJobId().get().equalsIgnoreCase(job.id)) {
                    assignedSlot = slot.slotType();
                    break;
                }
            }
        }
        String categoryLabel = job.category != null ? (job.category.contains("POKEMON") ? "Especialização Pokémon" : "Profissão Comum") : "Profissão Comum";

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
        values.put("job_icon", job.icon != null ? job.icon : getJobIcon(job.id));
        values.put("job_active", String.valueOf(isActive));
        values.put("job_has_permission", String.valueOf(hasPerm));
        values.put("job_max_level", String.valueOf(job.maxLevel));
        values.put("job_category", job.category != null ? job.category : "COMMON");

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

        // License objectives summary
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
}
