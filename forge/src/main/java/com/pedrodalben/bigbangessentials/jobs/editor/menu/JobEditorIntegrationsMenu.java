package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogRegistry;
import com.pedrodalben.bigbangessentials.jobs.compat.IntegrationStatus;
import com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JobEditorIntegrationsMenu extends AbstractJobsEditorMenu {

    public JobEditorIntegrationsMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(containerId, playerInventory, player, 6);
        render();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorIntegrationsMenu(id, inv, (ServerPlayer) p),
            Component.literal("§8§lIntegrações Cobbleverse")
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(3, "§8§m          §r §8[§bIntegrações Cobbleverse§8] §8§m          ");

        Collection<IntegrationStatus> statuses = PokemonIntegrationRegistry.getInstance().getAllStatuses();

        int slot = 10;
        for (IntegrationStatus status : statuses) {
            if (slot > 34) break;
            renderIntegrationSlot(slot++, status);
        }

        renderSummary(slot);
        renderBottomActions();
    }

    private void renderIntegrationSlot(int slot, IntegrationStatus status) {
        Item icon = status.isOperational() ? Items.LIME_WOOL : Items.RED_WOOL;
        String statusColor = status.isOperational() ? "§a" : "§c";

        List<String> lore = new ArrayList<>();
        lore.add("§7Estado: " + statusColor + status.state().name());
        lore.add("§7Mod: §f" + status.detectedModId());
        lore.add("§7Versão: §f" + status.detectedVersion());
        lore.add("§7Compatibilidade: §f" + status.compatibilityVersion());
        lore.add("§7Detalhes: §f" + status.details());
        lore.add("");

        if (status.supportedActions() != null && !status.supportedActions().isEmpty()) {
            lore.add("§aAções suportadas:");
            for (String action : status.supportedActions()) {
                lore.add("§a  + " + action);
            }
        }
        if (status.unavailableActions() != null && !status.unavailableActions().isEmpty()) {
            lore.add("§cAções indisponíveis:");
            for (String action : status.unavailableActions()) {
                lore.add("§c  - " + action);
            }
        }

        List<JobCatalogDefinition> jobs = JobCatalogRegistry.getInstance()
            .getJobsByIntegration(status.integrationId());
        if (!jobs.isEmpty()) {
            lore.add("");
            lore.add("§eJobs dependentes:");
            for (JobCatalogDefinition job : jobs) {
                lore.add("§e  • " + job.displayName()
                    + (job.availability().isOperational() ? " §a[OK]" : " §c[Indisponível]"));
            }
        }

        String name = statusColor + "§l" + status.integrationId().toUpperCase();

        ItemStack stack = new ItemStack(icon);
        stack.setHoverName(Component.literal(translateColorCodes(name)));
        ItemLoreHelper.setLore(stack,
            lore.stream().map(s -> (Component) Component.literal(translateColorCodes(s))).toList());

        setItem(slot, stack);
    }

    private void renderSummary(int startSlot) {
        long active = PokemonIntegrationRegistry.getInstance().getAllStatuses().stream()
            .filter(IntegrationStatus::isOperational).count();
        long total = PokemonIntegrationRegistry.getInstance().getAllStatuses().size();
        long degraded = PokemonIntegrationRegistry.getInstance().getAllStatuses().stream()
            .filter(s -> s.state().name().equals("DEGRADED")).count();

        setItem(startSlot, createActionItem(startSlot, Items.COMPARATOR,
            "§b§lResumo",
            "§7Total: §f" + total,
            "§7Ativas: §a" + active,
            "§7Degradadas: §e" + degraded,
            "§7Inativas: §c" + (total - active),
            ""));
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
