package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogRegistry;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationAuditService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class JobEditorSimulationMenu extends AbstractJobsEditorMenu {

    private final String jobId;
    private final String targetId;
    private String selectedAction;

    public JobEditorSimulationMenu(int containerId, Inventory playerInventory,
                                   ServerPlayer player, String jobId, String targetId) {
        super(containerId, playerInventory, player, 6);
        this.jobId = jobId;
        this.targetId = targetId;
        this.selectedAction = jobId != null ? null : null;
        render();
    }

    public static void open(ServerPlayer player, String jobId, String targetId) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorSimulationMenu(id, inv, (ServerPlayer) p, jobId, targetId),
            Component.literal("§8§lSimulação de Recompensa")
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(5, "§8§m          §r §8[§aSimular Recompensa§8] §8§m          ");

        renderJobSelector();
        renderActionSelector();
        renderSimulationResult();
        renderBottomActions();
    }

    private void renderJobSelector() {
        List<JobCatalogDefinition> jobs = new ArrayList<>(JobCatalogRegistry.getInstance().getAllJobs().values());
        for (int i = 0; i < Math.min(jobs.size(), 7); i++) {
            JobCatalogDefinition job = jobs.get(i);
            int slot = 10 + i;
            boolean selected = job.jobId().equals(jobId);

            ItemStack stack = new ItemStack(job.category() == com.pedrodalben.bigbangessentials.jobs.catalog.JobCategory.POKEMON_SPECIALIZATION
                ? Items.ENDER_EYE : Items.IRON_PICKAXE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                (selected ? "§a▶ " : "§7") + job.displayName()));
            stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                Component.literal("§7ID: §f" + job.jobId()),
                Component.literal("§7Ações: §f" + job.acceptedActions().size()),
                Component.literal(""),
                Component.literal(selected ? "§aSelecionado" : "§eClique para selecionar"))));

            setItem(slot, stack, p -> {
                JobEditorSimulationMenu.open(p, job.jobId(), targetId);
            });
        }
    }

    private void renderActionSelector() {
        if (jobId == null) return;

        JobCatalogRegistry.getInstance().getJob(jobId).ifPresent(def -> {
            int slot = 28;
            for (JobActionType action : def.acceptedActions()) {
                if (slot > 34) break;
                boolean selected = action.name().equals(selectedAction);

                ItemStack stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                    (selected ? "§a▶ " : "§7") + action.name()));
                stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                    Component.literal("§7Aliases: §f" + String.join(", ", action.getConfigKeys())),
                    Component.literal(""),
                    Component.literal(selected ? "§aSelecionado" : "§eClique para selecionar"))));

                setItem(slot++, stack, p -> simulate(p, def, action));
            }
        });
    }

    private void renderSimulationResult() {
        if (jobId == null) {
            setItem(22, createActionItem(22, Items.KNOWLEDGE_BOOK,
                "§e§lSelecione um Job e uma Ação",
                "§7Escolha um Job acima",
                "§7e uma ação compatível",
                "§7para simular a recompensa",
                ""));
            return;
        }

        setItem(22, createActionItem(22, Items.EXPERIENCE_BOTTLE,
            "§a§lSimulação Segura",
            "§7Nenhuma recompensa real será",
            "§7concedida. Apenas previsão.",
            "§7XP, Coins, Fragmentos e Chaves",
            "§7são calculados sem alterar dados.",
            ""));
        setItem(23, createActionItem(23, Items.GOLD_NUGGET,
            "§e§lPrevisão de Coins",
            "§7Clique em uma ação acima",
            "§7para ver a estimativa",
            ""));
        setItem(24, createActionItem(24, Items.DIAMOND,
            "§b§lPrevisão de Fragmentos",
            "§7Clique em uma ação acima",
            "§7para ver a estimativa",
            ""));
    }

    private void simulate(ServerPlayer player, JobCatalogDefinition def, JobActionType action) {
        this.selectedAction = action.name();
        render();

        StringBuilder sb = new StringBuilder();
        sb.append("§6§l=== SIMULAÇÃO: ").append(def.displayName()).append(" ===");
        sb.append("\n§7Job: §f").append(def.jobId());
        sb.append("\n§7Ação: §f").append(action.name());
        sb.append("\n§7Integração: ").append(def.requiredIntegration() != null
            ? "§a" + def.requiredIntegration() : "§7Nenhuma");
        sb.append("\n§7Rank mínimo: §f").append(def.requirements().requiredRankOrder());
        sb.append("\n§7Slot: §f").append(def.requirements().slotType());
        sb.append("\n");
        sb.append("\n§e--- Recompensa Estimada ---");
        sb.append("\n§eCoins: §f").append(def.rewardProfile().baseCoins())
            .append(" (×").append(def.rewardProfile().coinMultiplierPerLevel()).append(" por nível)");
        sb.append("\n§bXP: §f").append(def.rewardProfile().baseXp())
            .append(" (×").append(def.rewardProfile().xpMultiplierPerLevel()).append(" por nível)");
        sb.append("\n§dFragmentos: §f").append(def.rewardProfile().baseFragments());
        if (def.rewardProfile().crateKeyId() != null) {
            sb.append("\n§aChave: §f").append(def.rewardProfile().crateKeyId())
                .append(" (").append(String.format("%.2f%%", def.rewardProfile().keyChance() * 100)).append(" chance)")
                .append(" (max ").append(def.rewardProfile().keyMaxPerDay()).append("/dia)");
        } else {
            sb.append("\n§7Chave: §fNão configurada");
        }
        sb.append("\n");
        sb.append("\n§7Limite diário: §f").append(def.requirements().maxDailyEarnings() > 0
            ? def.requirements().maxDailyEarnings() : "Ilimitado");
        sb.append("\n§7Nível máximo: §f").append(def.requirements().maxLevel());

        for (String line : sb.toString().split("\n")) {
            player.sendSystemMessage(Component.literal(line));
        }

        JobConfigurationAuditService.getInstance().logSimulation(
            player.getUUID(), def.jobId(), action.name(),
            targetId != null ? targetId : "default",
            sb.toString().replaceAll("§[0-9a-fA-Fk-oK-OrR]", ""));
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
