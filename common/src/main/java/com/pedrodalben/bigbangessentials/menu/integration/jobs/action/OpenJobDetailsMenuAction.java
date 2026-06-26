package com.pedrodalben.bigbangessentials.menu.integration.jobs.action;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.integration.jobs.JobsMenuSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OpenJobDetailsMenuAction implements MenuActionHandler {
    @Override
    public String type() {
        return "open_job_details";
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

        String detailsMenuId = context.param("menu-id", String.class);
        if (detailsMenuId == null || detailsMenuId.isBlank()) {
            detailsMenuId = "job_details_menu";
        }

        Map<String, Object> values = new HashMap<>();
        Map<String, String> overrides = new HashMap<>();
        if (context.context() != null) {
            if (context.context().values() != null) {
                values.putAll(context.context().values());
            }
            if (context.context().placeholderOverrides() != null) {
                overrides.putAll(context.context().placeholderOverrides());
            }
        }

        Map<String, Object> jobPlaceholders = JobsMenuSupport.buildJobPlaceholders(player, job);
        values.putAll(jobPlaceholders);
        for (Map.Entry<String, Object> entry : jobPlaceholders.entrySet()) {
            overrides.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }

        MenuContext detailsContext = new MenuContext(
            player.getUUID(),
            context.context() != null && context.context().locale() != null ? context.context().locale() : "pt_BR",
            values,
            overrides,
            context.context() != null ? context.context().sourceModule() : "jobs",
            context.context() != null ? context.context().sourceCommand() : null,
            UUID.randomUUID()
        );

        return MenuSystem.getInstance().getMenuService()
            .openMenu(player, detailsMenuId, detailsContext)
            .thenApply(result -> {
                if (result.success()) {
                    return ActionExecutionResult.success();
                }
                player.sendSystemMessage(Component.literal("§cNão foi possível abrir os detalhes do trabalho."));
                return ActionExecutionResult.failed(result.error() != null ? result.error() : "Details menu unavailable");
            });
    }
}
