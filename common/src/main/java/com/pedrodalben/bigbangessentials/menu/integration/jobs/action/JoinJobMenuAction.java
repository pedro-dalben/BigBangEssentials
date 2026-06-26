package com.pedrodalben.bigbangessentials.menu.integration.jobs.action;

import com.pedrodalben.bigbangessentials.jobs.JobCommandService;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class JoinJobMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "join_job";
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

        JobCommandService.JoinResult result = JobCommandService.getInstance().joinJob(player, jobName);
        switch (result) {
            case SUCCESS:
                JobsConfig cfg = JobsManager.getInstance().getConfig();
                JobDefinition job = cfg != null ? cfg.getJob(jobName) : null;
                String displayName = job != null ? job.displayName : jobName;
                player.sendSystemMessage(Component.literal("§aVocê entrou com sucesso no trabalho: §l" + displayName));
                
                // Refresh open sessions
                MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.all");
                MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                return CompletableFuture.completedFuture(ActionExecutionResult.success());
            case NOT_FOUND:
                player.sendSystemMessage(Component.literal("§cTrabalho '" + jobName + "' não encontrado ou desabilitado."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case NO_PERMISSION:
                player.sendSystemMessage(Component.literal("§cVocê não possui permissão para entrar neste trabalho."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case ALREADY_ACTIVE:
                player.sendSystemMessage(Component.literal("§cVocê já está ativo neste trabalho."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case LIMIT_REACHED:
                int maxJobs = JobsManager.getInstance().getMaxActiveJobsForPlayer(player);
                player.sendSystemMessage(Component.literal("§cLimite de trabalhos ativos atingido (" + maxJobs + "). Saia de um para poder entrar em outro."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case CANCELLED:
            default:
                player.sendSystemMessage(Component.literal("§cA entrada no trabalho foi impedida por outro sistema."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
        }
    }
}
