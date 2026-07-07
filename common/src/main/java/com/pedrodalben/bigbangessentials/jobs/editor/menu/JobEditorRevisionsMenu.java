package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationAuditService;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationPublisher;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationRevision;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class JobEditorRevisionsMenu extends AbstractJobsEditorMenu {

    private final String jobFilter;

    public JobEditorRevisionsMenu(int containerId, Inventory playerInventory,
                                  ServerPlayer player, String jobFilter) {
        super(containerId, playerInventory, player, 6);
        this.jobFilter = jobFilter;
        render();
    }

    public static void open(ServerPlayer player, String jobFilter) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorRevisionsMenu(id, inv, (ServerPlayer) p, jobFilter),
            Component.literal("§8§lHistórico de Revisões")
        ));
    }

    private void render() {
        clearContainer();
        String title = jobFilter != null
            ? "§8§m        §r §8[§7Revisões: " + jobFilter + "§8] §8§m        "
            : "§8§m        §r §8[§7Histórico de Revisões§8] §8§m        ";
        fillBorder(8, title);

        List<JobConfigurationRevision> revisions = jobFilter != null
            ? JobConfigurationPublisher.getInstance().getRevisions(jobFilter)
            : JobConfigurationPublisher.getInstance().getAllRevisions();

        int slot = 10;
        for (JobConfigurationRevision rev : revisions) {
            if (slot > 34 || slot - 10 >= 21) break;
            renderRevisionSlot(slot++, rev);
        }

        renderAuditSummary();
        renderBottomActions();
    }

    private void renderRevisionSlot(int slot, JobConfigurationRevision revision) {
        String typeColor = switch (revision.type()) {
            case PUBLISH -> "§a";
            case ROLLBACK -> "§c";
            case AUTO_BACKUP -> "§7";
        };

        ItemStack stack = new ItemStack(revision.type() == JobConfigurationRevision.RevisionType.ROLLBACK
            ? Items.NAME_TAG : Items.WRITTEN_BOOK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(
            typeColor + "§l" + revision.type().name() + " — " + revision.jobId()));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
            Component.literal("§7Revisão: §f" + revision.revisionId()),
            Component.literal("§7Autor: §f" + revision.authorUuid().toString().substring(0, 8)),
            Component.literal("§7Data: §f" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(revision.timestamp()))),
            Component.literal("§7Tipo: " + typeColor + revision.type().name()),
            Component.literal(revision.reason() != null ? "§7Motivo: §f" + revision.reason() : "§7N/A"),
            Component.literal(revision.requiresRestart() ? "§c⚠ Requer reinício do servidor" : "§aSem necessidade de reinício"),
            Component.literal(""),
            Component.literal("§e§lClique para reverter"))));

        setItem(slot, stack, p -> rollback(p, revision));
    }

    private void renderAuditSummary() {
        JobConfigurationAuditService audit = JobConfigurationAuditService.getInstance();
        setItem(47, createActionItem(47, Items.BOOK,
            "§7§lAuditoria",
            "§7Total de registros: §f" + audit.getEntryCount(),
            "",
            "§eClique para ver últimos registros"));

        setItem(51, createActionItem(51, Items.COMPARATOR,
            "§5§lCrate Tiers",
            "§7Veja e configure",
            "§7os tiers de crate",
            "",
            "§eClique para abrir"),
            p -> JobEditorCrateTiersMenu.open(p, jobFilter != null ? jobFilter : "global"));
    }

    private void rollback(ServerPlayer player, JobConfigurationRevision revision) {
        var result = JobConfigurationPublisher.getInstance()
            .rollback(player.getUUID(), revision.jobId(), revision.revisionId());

        if (result.success()) {
            player.sendSystemMessage(Component.literal("§a✔ Rollback do Job '" + revision.jobId()
                + "' para revisão " + revision.revisionId() + " concluído."));
            player.closeContainer();
            JobsEditorMainMenu.open(player);
        } else {
            player.sendSystemMessage(Component.literal("§c✘ Falha no rollback:"));
            for (String msg : result.messages()) {
                player.sendSystemMessage(Component.literal("§c  - " + msg));
            }
        }
    }

    private void renderBottomActions() {
        setItem(49, createActionItem(49, MenuIcons.BACK,
            "§7§lVoltar",
            "§7Retorna ao menu principal",
            "",
            "§e§lClique para voltar"),
            p -> {
                p.closeContainer();
                JobsEditorMainMenu.open(p);
            });
    }
}
