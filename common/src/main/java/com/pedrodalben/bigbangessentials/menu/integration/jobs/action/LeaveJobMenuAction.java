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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class LeaveJobMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "leave_job";
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

        JobCommandService.LeaveResult result = JobCommandService.getInstance().leaveJob(player, jobName);
        switch (result) {
            case SUCCESS:
                JobsConfig cfg = JobsManager.getInstance().getConfig();
                JobDefinition job = cfg != null ? cfg.getJob(jobName) : null;
                String displayName = job != null ? job.displayName : jobName;
                player.sendSystemMessage(Component.literal("§aVocê saiu com sucesso do trabalho: §l" + displayName));
                
                // Refresh open sessions
                MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.all");
                MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                return CompletableFuture.completedFuture(ActionExecutionResult.success());
            case NOT_FOUND:
                player.sendSystemMessage(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case NOT_ACTIVE:
                player.sendSystemMessage(Component.literal("§cVocê não está ativo neste trabalho."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case CANCELLED:
            default:
                player.sendSystemMessage(Component.literal("§cA saída do trabalho foi impedida por outro sistema."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
        }
    }
}
