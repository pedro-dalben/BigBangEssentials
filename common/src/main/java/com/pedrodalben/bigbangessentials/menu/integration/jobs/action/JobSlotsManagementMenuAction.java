package com.pedrodalben.bigbangessentials.menu.integration.jobs.action;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlot;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class JobSlotsManagementMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "job_slots_management";
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player unavailable"));
        }

        Map<String, JobSlot> slots = JobSlotService.getInstance().getSlots(player.getUUID());
        int maxSlots = JobsManager.getInstance().getMaxActiveJobsForPlayer(player);
        int usedSlots = (int) slots.values().stream().filter(s -> !s.isEmpty()).count();

        player.sendSystemMessage(Component.literal("§6§lGerenciamento de Slots:"));
        player.sendSystemMessage(Component.literal("§7Slots usados: §e" + usedSlots + " §7/ §e" + maxSlots));
        for (JobSlot slot : slots.values()) {
            String status = slot.isEmpty() ? "§aVazio" : "§e" + slot.activeJobId().orElse("Desconhecido");
            player.sendSystemMessage(Component.literal(" §8- §7" + slot.slotType() + ": " + status));
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
