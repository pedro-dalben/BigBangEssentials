package com.pedrodalben.bigbangessentials.menu.integration.jobs.action;

import com.pedrodalben.bigbangessentials.jobs.JobCommandService;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ToggleJobMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "toggle_job";
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
        if (rawJobName == null || rawJobName.isBlank()) {
            rawJobName = context.param("name", String.class);
        }

        String jobName = PlaceholderService.resolve(rawJobName, player, context.context());
        if (jobName == null || jobName.isBlank()) {
            player.sendSystemMessage(Component.literal("§cNome do trabalho não informado."));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Job name missing"));
        }

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        JobDefinition job = cfg != null ? cfg.getJob(jobName) : null;
        if (job == null) {
            player.sendSystemMessage(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Job not found"));
        }

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobProgress prog = data != null ? data.getProgress(job.id) : null;
        boolean isActive = prog != null && prog.isActive();

        if (isActive) {
            // Leave job
            JobCommandService.LeaveResult leaveResult = JobCommandService.getInstance().leaveJob(player, job.id);
            if (leaveResult == JobCommandService.LeaveResult.SUCCESS) {
                player.sendSystemMessage(Component.literal("§aVocê saiu com sucesso do trabalho: §l" + job.displayName));
                MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.all");
                MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                return CompletableFuture.completedFuture(ActionExecutionResult.success());
            } else {
                player.sendSystemMessage(Component.literal("§cNão foi possível sair do trabalho: " + leaveResult.name()));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            }
        } else {
            // Join job
            JobCommandService.JoinResult joinResult = JobCommandService.getInstance().joinJob(player, job.id);
            if (joinResult == JobCommandService.JoinResult.SUCCESS) {
                player.sendSystemMessage(Component.literal("§aVocê entrou com sucesso no trabalho: §l" + job.displayName));
                MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.all");
                MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                return CompletableFuture.completedFuture(ActionExecutionResult.success());
            } else if (joinResult == JobCommandService.JoinResult.LIMIT_REACHED) {
                int maxJobs = JobsManager.getInstance().getMaxActiveJobsForPlayer(player);
                player.sendSystemMessage(Component.literal("§cLimite de trabalhos ativos atingido (" + maxJobs + "). Saia de um para poder entrar em outro."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            } else {
                player.sendSystemMessage(Component.literal("§cNão foi possível entrar no trabalho: " + joinResult.name()));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            }
        }
    }
}
