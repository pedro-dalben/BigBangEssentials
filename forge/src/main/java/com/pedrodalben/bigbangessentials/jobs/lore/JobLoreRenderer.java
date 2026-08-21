package com.pedrodalben.bigbangessentials.jobs.lore;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityResult;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityStatus;
import com.pedrodalben.bigbangessentials.jobs.availability.JobRequirementResult;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.progressbar.ProgressBarComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JobLoreRenderer {
    private static final JobLoreRenderer INSTANCE = new JobLoreRenderer();
    private static final int MAX_LORE_LINES = 30;

    private JobLoreRenderer() {}

    public static JobLoreRenderer getInstance() { return INSTANCE; }

    public List<Component> render(ServerPlayer player, JobDefinition job, JobAvailabilityResult avail, JobProgress prog) {
        List<Component> lore = new ArrayList<>();

        if (job.description != null && !job.description.isBlank()) {
            lore.add(Component.literal("§7" + job.description));
            lore.add(Component.literal(""));
        }

        if (prog != null) {
            int level = prog.getLevel();
            double xp = prog.getXp();
            double reqXp = job.getRequiredXp(level);
            lore.add(Component.literal(String.format("§7Nível atual: §f%d", level)));
            lore.add(Component.literal(String.format("§7Experiência: §f%s/§f%s",
                formatNumber(xp), formatNumber(reqXp))));
            lore.add(ProgressBarComponent.getInstance().render(xp, reqXp));
            lore.add(Component.literal(""));
        }

        if (avail.status() == JobAvailabilityStatus.ACTIVE) {
            lore.add(Component.literal("§a§l\u2714 Todos os requisitos cumpridos"));
            lore.add(Component.literal(""));
            lore.add(Component.literal("§eClique para ver detalhes."));
        } else if (avail.status() == JobAvailabilityStatus.AVAILABLE) {
            lore.add(Component.literal("§a§l\u2714 Todos os requisitos cumpridos"));
            lore.add(Component.literal(""));
            lore.add(Component.literal("§eClique para entrar."));
        } else {
            lore.add(Component.literal("§c§l\u2716 Profissão indisponível"));
            lore.add(Component.literal(""));

            List<JobRequirementResult> pending = avail.getPendingRequirements();
            if (!pending.isEmpty()) {
                lore.add(Component.literal("§7Requisitos:"));
                for (JobRequirementResult req : avail.requirements()) {
                    if (req.completed()) {
                        lore.add(Component.literal("  §a\u2714 " + req.title()));
                    } else {
                        lore.add(Component.literal("  §c\u2716 " + req.title()));
                    }
                }
                lore.add(Component.literal(""));
            }

            if (avail.status() == JobAvailabilityStatus.COOLDOWN && avail.cooldownRemaining() != null
                && !avail.cooldownRemaining().isZero()) {
                lore.add(Component.literal("§7Tempo restante: §e" + formatDuration(avail.cooldownRemaining())));
                lore.add(Component.literal(""));
            }

            if (avail.canStartLicense()) {
                lore.add(Component.literal("§eClique para iniciar a licença."));
            } else if (avail.status() == JobAvailabilityStatus.NO_AVAILABLE_SLOT) {
                lore.add(Component.literal("§eClique para gerenciar slots."));
            } else {
                lore.add(Component.literal("§eClique para ver como desbloquear."));
            }
        }

        if (lore.size() > MAX_LORE_LINES) {
            lore = new ArrayList<>(lore.subList(0, MAX_LORE_LINES));
        }
        return lore;
    }

    private String formatNumber(double value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fK", value / 1_000);
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private String formatDuration(Duration d) {
        long seconds = d.getSeconds();
        if (seconds <= 0) return "0s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        if (minutes > 0) return String.format("%dm %ds", minutes, secs);
        return String.format("%ds", secs);
    }
}
