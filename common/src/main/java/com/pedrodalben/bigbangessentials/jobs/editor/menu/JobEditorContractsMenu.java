package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobContractProfile;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorDraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class JobEditorContractsMenu extends AbstractJobsEditorMenu {

    private final JobCatalogDefinition definition;
    private final JobEditorDraft draft;

    public JobEditorContractsMenu(int containerId, Inventory playerInventory,
                                  ServerPlayer player, JobCatalogDefinition definition,
                                  JobEditorDraft draft) {
        super(containerId, playerInventory, player, 6);
        this.definition = definition;
        this.draft = draft;
        render();
    }

    public static void open(ServerPlayer player, JobCatalogDefinition definition, JobEditorDraft draft) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorContractsMenu(id, inv, (ServerPlayer) p, definition, draft),
            Component.literal("§8§lContratos: " + definition.displayName())
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(5, "§8§m          §r §8[§6Contratos: " + definition.displayName() + "§8] §8§m          ");

        JobContractProfile cp = definition.contractProfile();

        setItem(10, createActionItem(10, Items.WRITABLE_BOOK,
            "§6§lStatus dos Contratos",
            "§7Habilitados: " + fmtEnabled(cp.contractsEnabled()),
            "§7Máx ativos: §f" + cp.maxActiveContracts(),
            "§7Duração: §f" + cp.contractDurationHours() + "h",
            "§7Por período: §f" + cp.contractsGeneratedPerPeriod(),
            "§7Peso: §f" + cp.contractWeight(),
            "",
            "§eClique para ativar/desativar"));

        setItem(12, createActionItem(12, Items.CLOCK,
            "§e§lPeríodos Disponíveis",
            "§7DAILY: " + (cp.availablePeriods().stream().anyMatch(p -> p.name().equals("DAILY")) ? "§aSim" : "§cNão"),
            "§7WEEKLY: " + (cp.availablePeriods().stream().anyMatch(p -> p.name().equals("WEEKLY")) ? "§aSim" : "§cNão"),
            "",
            "§eClique para alternar"));

        setItem(14, createActionItem(14, Items.HOPPER,
            "§3§lRerolls",
            "§7Máx por dia: §f" + cp.maxRerollsPerDay(),
            "",
            "§eClique para ajustar"));

        setItem(16, createActionItem(16, Items.COMPASS,
            "§b§lFiltros de Ação",
            "§7Ações permitidas: §f" + cp.allowedActionTypes().size(),
            "§7Targets permitidos: §f" + cp.allowedTargetIds().size(),
            "§7Targets banidos: §f" + cp.bannedTargetIds().size(),
            "",
            "§eClique para gerenciar filtros"));

        renderBottomActions();
    }

    private void renderBottomActions() {
        setItem(49, createActionItem(49, MenuIcons.BACK,
            "§7§lVoltar",
            "§7Retorna ao editor do Job",
            "",
            "§e§lClique para voltar"),
            p -> {
                p.closeContainer();
                JobEditorDetailsMenu.open(p, definition, draft);
            });
    }
}
