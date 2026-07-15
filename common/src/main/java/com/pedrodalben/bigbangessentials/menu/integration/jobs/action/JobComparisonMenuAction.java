package com.pedrodalben.bigbangessentials.menu.integration.jobs.action;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityService;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.menu.JobMenuViewModelFactory;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class JobComparisonMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "job_comparison";
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player unavailable"));
        }

        String rawJobIds = context.param("job-ids", String.class);
        if (rawJobIds == null || rawJobIds.isBlank()) {
            rawJobIds = context.param("jobs", String.class);
        }

        String resolved = PlaceholderService.resolve(rawJobIds, player, context.context());
        if (resolved == null || resolved.isBlank()) {
            player.sendSystemMessage(Component.literal("§cNenhum trabalho informado para comparação."));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Job ids missing"));
        }

        String[] ids = resolved.split(",");
        if (ids.length < 2) {
            player.sendSystemMessage(Component.literal("§cInforme ao menos dois trabalhos para comparar."));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Need at least 2 jobs"));
        }

        player.sendSystemMessage(Component.literal("§6§lComparação de Trabalhos:"));
        for (String id : ids) {
            id = id.trim();
            JobDefinition job = JobsManager.getInstance().getConfig() != null
                ? JobsManager.getInstance().getConfig().getJob(id) : null;
            if (job == null) continue;
            player.sendSystemMessage(Component.literal("§e" + job.displayName
                + " §7(Nível Máx: " + job.maxLevel
                + " | Bônus: $" + String.format("%.1f", job.moneyBonusPerLevel) + "/nível)"));
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
