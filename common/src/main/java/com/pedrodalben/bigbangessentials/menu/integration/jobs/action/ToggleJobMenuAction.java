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
            switch (leaveResult) {
                case SUCCESS:
                    player.sendSystemMessage(Component.literal("§aVocê saiu com sucesso do trabalho: §l" + job.displayName));
                    MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.all");
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                    return CompletableFuture.completedFuture(ActionExecutionResult.success());
                case NOT_FOUND:
                    player.sendSystemMessage(Component.literal("§cTrabalho '" + job.displayName + "' não encontrado."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
                case NOT_ACTIVE:
                    player.sendSystemMessage(Component.literal("§cVocê não está ativo neste trabalho."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
                default:
                    player.sendSystemMessage(Component.literal("§cA saída do trabalho foi impedida por outro sistema."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            }
        } else {
            // Join job
            JobCommandService.JoinResult joinResult = JobCommandService.getInstance().joinJob(player, job.id);
            switch (joinResult) {
                case SUCCESS:
                    player.sendSystemMessage(Component.literal("§aVocê entrou com sucesso no trabalho: §l" + job.displayName));
                    MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.all");
                    MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.common");
                    MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.pokemon");
                    MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.active");
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                    return CompletableFuture.completedFuture(ActionExecutionResult.success());
                case MISSING_PERMISSION:
                    player.sendSystemMessage(Component.literal("§cVocê não possui permissão para entrar neste trabalho."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
                case ALREADY_ACTIVE:
                    player.sendSystemMessage(Component.literal("§cVocê já está ativo neste trabalho."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
                case NO_COMPATIBLE_SLOT:
                    player.sendSystemMessage(Component.literal("§cNenhum slot compatível disponível. Remova um trabalho de um slot primeiro."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
                case SLOT_COOLDOWN:
                    player.sendSystemMessage(Component.literal("§cO slot está em tempo de recarga. Aguarde antes de trocar de profissão."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
                case INTEGRATION_UNAVAILABLE:
                    player.sendSystemMessage(Component.literal("§cA integração necessária para esta profissão não está disponível."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
                case LOCKED_BY_RANK:
                    player.sendSystemMessage(Component.literal("§cVocê ainda não alcançou o rank necessário para esta profissão."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
                case LICENSE_AVAILABLE:
                case LICENSE_IN_PROGRESS:
                case LICENSE_READY_TO_CLAIM:
                    MenuSystem.getInstance().getMenuService().refreshSessionsUsingSource("jobs.all");
                    MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
                    return CompletableFuture.completedFuture(ActionExecutionResult.success());
                default:
                    player.sendSystemMessage(Component.literal("§cNão foi possível entrar neste trabalho."));
                    return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            }
        }
    }
}
