package com.pedrodalben.bigbangessentials.menu.integration.jobs.action;

import com.pedrodalben.bigbangessentials.jobs.JobCommandService;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlot;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService;
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
                JobsConfig cfg2 = JobsManager.getInstance().getConfig();
                JobDefinition jobDef = cfg2 != null ? cfg2.getJob(jobName) : null;
                String category = jobDef != null ? jobDef.category : "COMMON";
                String display = jobDef != null ? jobDef.displayName : jobName;
                player.sendSystemMessage(buildSlotOccupiedMessage(player, category, display));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case SLOT_COOLDOWN:
                player.sendSystemMessage(Component.literal("§cSlot em cooldown. Aguarde antes de trocar."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case INTEGRATION_UNAVAILABLE:
                player.sendSystemMessage(Component.literal("§cIntegração necessária indisponível."));
                return CompletableFuture.completedFuture(ActionExecutionResult.denied());
            case LOCKED_BY_RANK:
                player.sendSystemMessage(Component.literal("§cRank insuficiente para esta profissão."));
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

    private static Component buildSlotOccupiedMessage(ServerPlayer player, String category, String targetJobName) {
        java.util.Map<String, JobSlot> slots = JobSlotService.getInstance().getSlots(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        for (JobSlot slot : slots.values()) {
            if (slot.category() != null && slot.category().equalsIgnoreCase(category) && slot.activeJobId().isPresent()) {
                String jobId = slot.activeJobId().get();
                JobDefinition occupyingJob = cfg != null ? cfg.getJob(jobId) : null;
                String occDisplay = occupyingJob != null ? occupyingJob.displayName : jobId;
                String slotDisplay = slotDisplayName(cfg, slot);
                return Component.literal("§cVocê já está como §e" + occDisplay + "§c no slot §e" + slotDisplay + "§c. Saia pelo menu ou use §f/jobs leave " + jobId + "§c.");
            }
        }

        return Component.literal("§cNenhum slot compatível disponível.");
    }

    private static String slotDisplayName(JobsConfig cfg, JobSlot slot) {
        if (cfg != null) {
            var slotDef = cfg.getSlots().get(slot.slotType());
            if (slotDef != null && slotDef.displayName() != null && !slotDef.displayName().isBlank()) {
                return slotDef.displayName();
            }
        }
        return slot.slotType();
    }
}
