package com.pedrodalben.bigbangessentials.menu.integration.jobs.action;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityResult;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityService;
import com.pedrodalben.bigbangessentials.jobs.availability.JobRequirementResult;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class JobRequirementsMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "job_requirements";
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player unavailable"));
        }

        String rawJobName = context.param("job-id", String.class);
        if (rawJobName == null || rawJobName.isBlank()) {
            rawJobName = context.param("job", String.class);
        }

        String jobName = PlaceholderService.resolve(rawJobName, player, context.context());
        if (jobName == null || jobName.isBlank()) {
            player.sendSystemMessage(Component.literal("§cNome do trabalho não informado."));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Job name missing"));
        }

        JobDefinition job = JobsManager.getInstance().getConfig() != null
            ? JobsManager.getInstance().getConfig().getJob(jobName) : null;
        if (job == null) {
            player.sendSystemMessage(Component.literal("§cTrabalho não encontrado: " + jobName));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Job not found"));
        }

        JobAvailabilityResult result = JobAvailabilityService.getInstance().evaluate(player, job);
        List<JobRequirementResult> pending = result.getPendingRequirements();

        player.sendSystemMessage(Component.literal("§6§lRequisitos: " + job.displayName));
        if (pending.isEmpty()) {
            player.sendSystemMessage(Component.literal("§aTodos os requisitos foram atendidos!"));
        } else {
            for (JobRequirementResult req : pending) {
                String color = req.completed() ? "§a" : "§c";
                player.sendSystemMessage(Component.literal(color + "✗ " + req.title() + ": " + req.description()));
            }
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
