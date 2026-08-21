package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.jobs.catalog.*;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationAuditService;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationPublisher;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationRevision;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationValidator;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorDraft;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorSession;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorValidationResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JobEditorDetailsMenu extends AbstractJobsEditorMenu {

    private final JobCatalogDefinition definition;
    private final JobEditorDraft draft;

    public JobEditorDetailsMenu(int containerId, Inventory playerInventory,
                                ServerPlayer player, JobCatalogDefinition definition,
                                JobEditorDraft draft) {
        super(containerId, playerInventory, player, 6);
        this.definition = definition;
        this.draft = draft;
        render();
    }

    public static void open(ServerPlayer player, JobCatalogDefinition definition, JobEditorDraft draft) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorDetailsMenu(id, inv, (ServerPlayer) p, definition, draft),
            Component.literal("§8§lEditando: " + definition.displayName())
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(7, "§8§m              §r §8[§6" + definition.displayName() + "§8] §8§m              ");

        renderJobIdentity();
        renderStatusActions();
        renderRequirements();
        renderRewardSummary();
        renderBottomActions();
    }

    private void renderJobIdentity() {
        setItem(10, createActionItem(10, Items.NAME_TAG,
            "§6§l" + definition.displayName(),
            "§7ID: §f" + definition.jobId(),
            "§7Categoria: §f" + definition.category().name(),
            "§7Descrição: §f" + definition.description(),
            ""));

        JobRequirements req = definition.requirements();
        setItem(11, createActionItem(11, Items.BOOKSHELF,
            "§a§lRequisitos",
            "§7Rank mínimo: §f" + req.requiredRankOrder(),
            "§7Licença: " + (req.licenseRequired() ? "§aSim" : "§7Não"),
            "§7Slot: §f" + req.slotType(),
            "§7Padrão: " + (req.unlockedByDefault() ? "§aLivre" : "§cRestrito"),
            "§7Permissão: §f" + (req.permissionNode() != null ? req.permissionNode() : "§7Nenhuma"),
            "§7Modo: §f" + req.permissionMode().name(),
            ""));
    }

    private void renderStatusActions() {
        setItem(12, createActionItem(12, definition.enabled() ? MenuIcons.ACTIVE : MenuIcons.INACTIVE,
            "§e§lStatus: " + fmtStatus(definition.enabled()),
            "§7Disponibilidade: §f" + definition.availability().name(),
            definition.availability().isOperational() ? "§aOperacional" : "§cIndisponível",
            "",
            "§eClique para ativar/desativar",
            ""),
            p -> toggleEnabled(p));

        setItem(13, createActionItem(13, MenuIcons.REWARD,
            "§6§lRecompensas",
            "§7Coins base: §e" + definition.rewardProfile().baseCoins(),
            "§7XP base: §b" + definition.rewardProfile().baseXp(),
            "§7Fragmentos: §d" + definition.rewardProfile().baseFragments(),
            "§7Chave: " + (definition.rewardProfile().crateKeyId() != null
                ? "§a" + definition.rewardProfile().crateKeyId() : "§7Não configurada"),
            "",
            "§e§lClique para editar recompensas"),
            p -> JobEditorRewardsMenu.open(p, definition, draft));

        setItem(14, createActionItem(14, MenuIcons.CRATE,
            "§5§lCrates e Tiers",
            "§7Crates habilitadas: " + fmtEnabled(definition.crateTierProfile().crateKeysEnabled()),
            "§7Iniciante: " + (definition.crateTierProfile().beginnerTier().isConfigured() ? "§a" + definition.crateTierProfile().beginnerTier().crateId() : "§cNão vinculada"),
            "§7Intermediária: " + (definition.crateTierProfile().intermediateTier().isConfigured() ? "§a" + definition.crateTierProfile().intermediateTier().crateId() : "§cNão vinculada"),
            "§7Avançada: " + (definition.crateTierProfile().advancedTier().isConfigured() ? "§a" + definition.crateTierProfile().advancedTier().crateId() : "§cNão vinculada"),
            "",
            "§e§lClique para editar"),
            p -> JobEditorCrateTiersMenu.open(p, definition.jobId()));
    }

    private void renderRequirements() {
        setItem(15, createActionItem(15, MenuIcons.PERMISSION,
            "§c§lPermissões",
            "§7Node: §f" + definition.requirements().permissionNode(),
            "§7Modo: §f" + definition.requirements().permissionMode().name(),
            "",
            "§e§lClique para editar"),
            p -> JobEditorPermissionsMenu.open(p, definition, draft));

        setItem(16, createActionItem(16, MenuIcons.CONTRACT,
            "§6§lContratos",
            "§7Habilitados: " + fmtEnabled(definition.contractProfile().contractsEnabled()),
            "§7Máx ativos: §f" + definition.contractProfile().maxActiveContracts(),
            "§7Períodos: §f" + definition.contractProfile().availablePeriods().size(),
            "",
            "§e§lClique para editar"),
            p -> JobEditorContractsMenu.open(p, definition, draft));
    }

    private void renderRewardSummary() {
        JobRewardProfile rp = definition.rewardProfile();
        setItem(20, createActionItem(20, Items.GOLD_NUGGET,
            "§e§lEconomia",
            "§7Coins base: §e" + rp.baseCoins(),
            "§7Multiplicador/nível: §e" + rp.coinMultiplierPerLevel(),
            "§7XP base: §b" + rp.baseXp(),
            "§7Mult. XP/nível: §b" + rp.xpMultiplierPerLevel(),
            ""));

        setItem(21, createActionItem(21, Items.AMETHYST_SHARD,
            "§d§lFragmentos",
            "§7Base: §d" + rp.baseFragments(),
            "§7Marco a cada: §f" + rp.fragmentMilestoneInterval() + " ações",
            "§7Bônus: §d" + rp.fragmentMilestoneBonus(),
            ""));

        setItem(22, createActionItem(22, MenuIcons.KEY,
            "§a§lChaves",
            "§7ID: " + (rp.crateKeyId() != null ? "§a" + rp.crateKeyId() : "§7Nenhuma"),
            "§7Chance: §f" + String.format("%.4f%%", rp.keyChance() * 100),
            "§7Máx/dia: §f" + rp.keyMaxPerDay(),
            "§7Cooldown: §f" + (rp.keyCooldownMilliseconds() / 1000) + "s",
            ""));

        setItem(23, createActionItem(23, Items.DIAMOND,
            "§3§lItens Diretos",
            "§7Itens configurados: §f" + rp.directItemIds().size(),
            "§7Inventário cheio: " + (rp.awardPendingOnFullInventory() ? "§aPendente" : "§cRecusar"),
            "",
            "§e§lClique para editar itens"),
            p -> JobEditorRewardsMenu.open(p, definition, draft));
    }

    private void renderBottomActions() {
        setItem(48, createActionItem(48, MenuIcons.SIMULATE,
            "§a§lSimular Recompensa",
            "§7Teste sem conceder",
            "§7recompensa real",
            "",
            "§e§lClique para simular"),
            p -> JobEditorSimulationMenu.open(p, definition.jobId(), null));

        setItem(49, createActionItem(49, MenuIcons.VALIDATE,
            "§9§lValidar",
            "§7Verifica se a configuração",
            "§7está correta e segura",
            "",
            "§e§lClique para validar"),
            p -> validateAndShow(p));

        setItem(50, createActionItem(50, MenuIcons.PUBLISH,
            "§2§lPublicar",
            "§7Salva e aplica",
            "§7esta configuração",
            "",
            "§e§lClique para publicar"),
            p -> publishCurrent(p));

        setItem(51, createActionItem(51, MenuIcons.DISCARD,
            "§c§lDescartar",
            "§7Descarta o rascunho",
            "§7e volta ao menu principal",
            "",
            "§e§lClique para descartar"),
            p -> {
                JobEditorSession.getInstance().discardDraft(p.getUUID(), definition.jobId());
                p.closeContainer();
                JobsEditorMainMenu.open(p);
            });

        setItem(52, createActionItem(52, MenuIcons.BACK,
            "§7§lVoltar",
            "§7Retorna ao dashboard",
            "",
            "§e§lClique para voltar"),
            p -> {
                p.closeContainer();
                JobsEditorMainMenu.open(p);
            });

        setItem(45, createActionItem(45, MenuIcons.AUDIT,
            "§7§lAuditoria",
            "§7Histórico de alterações",
            "§7para este Job",
            "",
            "§e§lClique para ver"),
            p -> JobEditorRevisionsMenu.open(p, definition.jobId()));
    }

    private void toggleEnabled(ServerPlayer player) {
        UUID editorUuid = player.getUUID();
        JobEditorDraft currentDraft = JobEditorSession.getInstance()
            .openDraft(editorUuid, definition);

        JobCatalogDefinition toggled = JobCatalogDefinition.builder(definition.jobId())
            .displayName(definition.displayName())
            .description(definition.description())
            .category(definition.category())
            .enabled(!definition.enabled())
            .availability(definition.availability())
            .unavailabilityReason(definition.unavailabilityReason())
            .acceptedActions(definition.acceptedActions())
            .requiredIntegration(definition.requiredIntegration())
            .iconMaterialIndex(definition.iconMaterialIndex())
            .colorOrStyle(definition.colorOrStyle())
            .requirements(definition.requirements())
            .rewardProfile(definition.rewardProfile())
            .contractProfile(definition.contractProfile())
            .crateTierProfile(definition.crateTierProfile())
            .extraSettings(definition.extraSettings())
            .build();

        JobEditorDraft updated = currentDraft.withDefinition(toggled);
        JobEditorSession.getInstance().updateDraft(editorUuid, updated);
        JobConfigurationAuditService.getInstance().logEdit(editorUuid, definition.jobId(),
            "enabled", String.valueOf(definition.enabled()), String.valueOf(!definition.enabled()));

        open(player, toggled, updated);
    }

    private void validateAndShow(ServerPlayer player) {
        JobEditorValidationResult result = JobConfigurationValidator.getInstance().validate(definition);
        if (result.valid()) {
            player.sendSystemMessage(Component.literal("§a✔ Configuração do Job '" + definition.jobId() + "' é válida."));
            if (!result.warnings().isEmpty()) {
                for (var w : result.warnings()) {
                    player.sendSystemMessage(Component.literal("§e⚠ " + w.toString()));
                }
            }
        } else {
            player.sendSystemMessage(Component.literal("§c✘ Configuração do Job '" + definition.jobId() + "' tem erros:"));
            for (var e : result.errors()) {
                player.sendSystemMessage(Component.literal("§c  - " + e.toString()));
            }
        }
    }

    private void publishCurrent(ServerPlayer player) {
        UUID editorUuid = player.getUUID();
        JobEditorDraft currentDraft = JobEditorSession.getInstance()
            .openDraft(editorUuid, definition);

        JobConfigurationRevision.PublishResult result = JobConfigurationPublisher.getInstance()
            .publish(editorUuid, currentDraft);

        if (result.success()) {
            player.sendSystemMessage(Component.literal("§a✔ Job '" + definition.jobId()
                + "' publicado com sucesso! Revisão: " + result.revisionId()
                + (result.warningCount() > 0 ? " (com " + result.warningCount() + " avisos)" : "")));
        } else {
            player.sendSystemMessage(Component.literal("§c✘ Falha ao publicar Job '" + definition.jobId() + "':"));
            for (String msg : result.messages()) {
                player.sendSystemMessage(Component.literal("§c  - " + msg));
            }
        }
    }
}
