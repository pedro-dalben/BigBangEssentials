package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.jobs.catalog.*;
import com.pedrodalben.bigbangessentials.jobs.compat.IntegrationStatus;
import com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationPublisher;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorSession;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class JobsEditorMainMenu extends AbstractJobsEditorMenu {

    private static final int JOBS_PER_PAGE = 21;
    private int currentPage = 0;
    private final List<JobCatalogDefinition> allJobs;
    private final JobCatalogRegistry registry;

    public JobsEditorMainMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(containerId, playerInventory, player, 6);
        this.registry = JobCatalogRegistry.getInstance();
        this.allJobs = new ArrayList<>(registry.getAllJobs().values());
        render();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobsEditorMainMenu(id, inv, (ServerPlayer) p),
            Component.literal("§8§lJobs Editor — BigBangCraft")
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(15, "§8§m                §r §8[§6Jobs Editor§8] §8§m                ");

        renderDashboardStats();
        renderJobEntries();
        renderQuickActions();
    }

    private void renderDashboardStats() {
        long activeCommon = allJobs.stream()
            .filter(j -> j.enabled() && j.category() == JobCategory.COMMON).count();
        long activePokemon = allJobs.stream()
            .filter(j -> j.enabled() && j.category() == JobCategory.POKEMON_SPECIALIZATION
                && j.availability().isOperational()).count();
        long needsConfig = allJobs.stream()
            .filter(j -> j.availability() == JobAvailability.CONFIGURATION_REQUIRED).count();
        long integrationsActive = PokemonIntegrationRegistry.getInstance().getAllStatuses().stream()
            .filter(IntegrationStatus::isOperational).count();

        setItem(1, createActionItem(1, MenuIcons.JOB,
            "§6§lJobs Comuns",
            "§7Ativos: §a" + activeCommon,
            ""));

        setItem(2, createActionItem(2, MenuIcons.POKEMON,
            "§d§lJobs Pokémon",
            "§7Ativos: §a" + activePokemon,
            "§7Precisam de config: §e" + needsConfig,
            ""));

        setItem(3, createActionItem(3, MenuIcons.INTEGRATION,
            "§b§lIntegrações",
            "§7Ativas: §a" + integrationsActive + "/6",
            "§7Clique para diagnóstico",
            ""));

        setItem(4, createActionItem(4, MenuIcons.HISTORY,
            "§7§lRevisões",
            "§7Publicações: §f" + JobConfigurationPublisher.getInstance().getAllRevisions().size(),
            "§7Clique para histórico",
            ""));
    }

    private void renderJobEntries() {
        int totalJobs = allJobs.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalJobs / JOBS_PER_PAGE));
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        int start = currentPage * JOBS_PER_PAGE;
        int end = Math.min(start + JOBS_PER_PAGE, totalJobs);

        int[] jobSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

        for (int i = start; i < end; i++) {
            JobCatalogDefinition job = allJobs.get(i);
            int slot = jobSlots[i - start];
            renderJobSlot(slot, job);
        }

        if (currentPage > 0) {
            setItem(45, createActionItem(45, Items.ARROW,
                "§e§lPágina Anterior", "§7Página " + currentPage + "/" + totalPages, ""),
                p -> { currentPage--; render(); });
        }
        if (currentPage < totalPages - 1) {
            setItem(53, createActionItem(53, Items.ARROW,
                "§e§lPróxima Página", "§7Página " + (currentPage + 2) + "/" + totalPages, ""),
                p -> { currentPage++; render(); });
        }

        setItem(49, createActionItem(49, Items.PAPER,
            "§e§lPágina " + (currentPage + 1) + " / " + totalPages,
            "§7Total de Jobs: §f" + totalJobs,
            ""));
    }

    private void renderJobSlot(int slot, JobCatalogDefinition job) {
        Item icon = job.category() == JobCategory.POKEMON_SPECIALIZATION ? MenuIcons.POKEMON : MenuIcons.JOB;
        String color = job.category() == JobCategory.POKEMON_SPECIALIZATION ? "§d" : "§e";
        String statusColor = job.enabled() && job.availability().isOperational() ? "§a" : "§c";
        String statusText = job.enabled() && job.availability().isOperational() ? "Ativo" : "Inativo";

        List<String> lore = new ArrayList<>();
        lore.add("§7ID: §f" + job.jobId());
        lore.add("§7Categoria: §f" + job.category().name());
        lore.add("§7Status: " + statusColor + statusText);
        lore.add("§7Integração: " + (job.requiredIntegration() != null ? "§b" + job.requiredIntegration() : "§7Nenhuma"));
        lore.add("§7Rank: §f" + job.requirements().requiredRankOrder());
        lore.add("§7Slot: §f" + job.requirements().slotType());
        if (job.availability() == JobAvailability.INTEGRATION_MISSING
            || job.availability() == JobAvailability.BRIDGE_ERROR) {
            lore.add("§cIndisponível: " + (job.unavailabilityReason() != null ? job.unavailabilityReason() : "Integração ausente"));
        }
        if (job.availability() == JobAvailability.CONFIGURATION_REQUIRED) {
            lore.add("§6Configuração pendente");
        }
        lore.add("");
        lore.add("§e§lClique para editar");

        ItemStack stack = new ItemStack(icon);
        stack.setHoverName(Component.literal(
            statusColor + "§l" + job.displayName()));
        ItemLoreHelper.setLore(stack,
            lore.stream().map(s -> (Component) Component.literal(translateColorCodes(s))).toList());

        setItem(slot, stack, p -> JobEditorDetailsMenu.open(p, job, null));
    }

    private void renderQuickActions() {
        setItem(46, createActionItem(46, MenuIcons.CRATE,
            "§5§lTiers de Crate",
            "§7Configure as caixas",
            "§7Iniciante / Intermediária / Avançada",
            "",
            "§e§lClique para configurar"),
            p -> JobEditorCrateTiersMenu.open(p, "global"));

        setItem(47, createActionItem(47, MenuIcons.SIMULATE,
            "§a§lSimular",
            "§7Teste recompensas",
            "§7antes de publicar",
            "",
            "§e§lClique para simular"),
            p -> JobEditorSimulationMenu.open(p, null, null));

        setItem(48, createActionItem(48, MenuIcons.VALIDATE,
            "§9§lValidar Tudo",
            "§7Verifica todos os Jobs",
            "§7e configurações",
            "",
            "§e§lClique para validar"),
            p -> JobEditorValidateAllMenu.open(p));

        setItem(50, createActionItem(50, MenuIcons.INTEGRATION,
            "§b§lIntegrações Cobbleverse",
            "§7Status das bridges",
            "§7e diagnóstico",
            "",
            "§e§lClique para abrir"),
            p -> JobEditorIntegrationsMenu.open(p));

        setItem(51, createActionItem(51, MenuIcons.HISTORY,
            "§7§lHistórico",
            "§7Revisões e rollback",
            "",
            "§e§lClique para abrir"),
            p -> JobEditorRevisionsMenu.open(p, null));

        setItem(52, createActionItem(52, MenuIcons.RELOAD,
            "§c§lRecarregar",
            "§7Recarrega toda",
            "§7configuração de Jobs",
            "",
            "§e§lClique para recarregar"),
            p -> {
                com.pedrodalben.bigbangessentials.jobs.JobsManager.getInstance().reload();
                p.sendSystemMessage(Component.literal("§aConfiguração de Jobs recarregada."));
                p.closeContainer();
            });
    }
}
