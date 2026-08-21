package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationAuditService;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorDraft;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorSession;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class JobEditorRewardsMenu extends AbstractJobsEditorMenu {

    private final JobCatalogDefinition definition;
    private final JobEditorDraft draft;

    public JobEditorRewardsMenu(int containerId, Inventory playerInventory,
                                ServerPlayer player, JobCatalogDefinition definition,
                                JobEditorDraft draft) {
        super(containerId, playerInventory, player, 6);
        this.definition = definition;
        this.draft = draft;
        render();
    }

    public static void open(ServerPlayer player, JobCatalogDefinition definition, JobEditorDraft draft) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorRewardsMenu(id, inv, (ServerPlayer) p, definition, draft),
            Component.literal("§8§lRecompensas: " + definition.displayName())
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(14, "§8§m          §r §8[§6Recompensas: " + definition.displayName() + "§8] §8§m          ");

        var rp = definition.rewardProfile();

        setItem(10, createActionItem(10, Items.GOLD_NUGGET,
            "§e§lCoins",
            "§7Valor Base: §e" + rp.baseCoins(),
            "§7Multiplicador por Nível: §e" + rp.coinMultiplierPerLevel(),
            "",
            "§eClique para ajustar"));
        setItem(11, createActionItem(11, Items.EXPERIENCE_BOTTLE,
            "§b§lXP",
            "§7Valor Base: §b" + rp.baseXp(),
            "§7Multiplicador por Nível: §b" + rp.xpMultiplierPerLevel(),
            "",
            "§eClique para ajustar"));
        setItem(12, createActionItem(12, Items.AMETHYST_SHARD,
            "§d§lFragmentos",
            "§7Base por ação: §d" + rp.baseFragments(),
            "§7Marco a cada: §f" + rp.fragmentMilestoneInterval() + " ações",
            "§7Bônus de marco: §d" + rp.fragmentMilestoneBonus(),
            "",
            "§eClique para ajustar"));
        setItem(13, createActionItem(13, Items.CHEST,
            "§3§lItens Diretos",
            "§7Itens configurados: §f" + rp.directItemIds().size(),
            "§7Recompensa pendente: " + (rp.awardPendingOnFullInventory() ? "§aSim" : "§cNão"),
            "",
            "§eClique para gerenciar"));
        setItem(14, createActionItem(14, MenuIcons.KEY,
            "§a§lChaves de Crate",
            "§7Chave: " + (rp.crateKeyId() != null ? "§a" + rp.crateKeyId() : "§7Não configurada"),
            "§7Chance: §f" + String.format("%.4f%%", rp.keyChance() * 100),
            "§7Peso: §f" + rp.keyWeight(),
            "§7Máx/dia: §f" + rp.keyMaxPerDay(),
            "§7Cooldown: §f" + (rp.keyCooldownMilliseconds() / 1000) + "s",
            "§7Fonte: §f" + rp.keyGrantSource().name(),
            "",
            "§eClique para ajustar"));

        setItem(16, createActionItem(16, Items.DIAMOND,
            "§b§lRecompensas por Nível",
            "§7Níveis configurados: §f" + rp.levelUpRewards().size(),
            "",
            "§eClique para gerenciar"));

        setItem(48, createActionItem(48, MenuIcons.BACK,
            "§7§lVoltar",
            "§7Retorna ao editor do Job",
            "",
            "§e§lClique para voltar"),
            p -> {
                p.closeContainer();
                JobEditorDetailsMenu.open(p, definition, draft);
            });

        setItem(49, createActionItem(49, Items.GREEN_WOOL,
            "§2§lSalvar Rascunho",
            "§7Salva as alterações",
            "§7sem publicar",
            "",
            "§e§lClique para salvar"),
            p -> {
                if (draft != null) {
                    JobEditorSession.getInstance().updateDraft(
                        p.getUUID(), draft.withDefinition(definition));
                }
                p.sendSystemMessage(Component.literal("§aRascunho salvo."));
            });
    }
}
