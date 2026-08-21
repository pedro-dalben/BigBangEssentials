package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobRequirements;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorDraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class JobEditorPermissionsMenu extends AbstractJobsEditorMenu {

    private final JobCatalogDefinition definition;
    private final JobEditorDraft draft;

    public JobEditorPermissionsMenu(int containerId, Inventory playerInventory,
                                    ServerPlayer player, JobCatalogDefinition definition,
                                    JobEditorDraft draft) {
        super(containerId, playerInventory, player, 6);
        this.definition = definition;
        this.draft = draft;
        render();
    }

    public static void open(ServerPlayer player, JobCatalogDefinition definition, JobEditorDraft draft) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorPermissionsMenu(id, inv, (ServerPlayer) p, definition, draft),
            Component.literal("§8§lPermissões: " + definition.displayName())
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(1, "§8§m          §r §8[§cPermissões: " + definition.displayName() + "§8] §8§m          ");

        JobRequirements req = definition.requirements();

        setItem(10, createActionItem(10, Items.REDSTONE_TORCH,
            "§c§lPermission Node",
            "§7Atual: §f" + (req.permissionNode() != null
                ? req.permissionNode() : "jobs.profissao." + definition.jobId()),
            "§7Modo: §f" + req.permissionMode().name(),
            "",
            "§eClique para alternar modo"));

        setItem(12, createActionItem(12, Items.IRON_DOOR,
            "§7§lModo de Requisito",
            "§7Atual: §f" + req.permissionMode().name(),
            "",
            verDescricao(req.permissionMode()),
            "",
            "§eClique para alternar"),
            p -> cyclePermissionMode(p));

        setItem(14, createActionItem(14, Items.GOLDEN_HELMET,
            "§6§lBypass Permissions",
            "§7bigbangessentials.jobs.bypass.rank",
            "§7bigbangessentials.jobs.bypass.license",
            "§7bigbangessentials.jobs.bypass.slot",
            "§7bigbangessentials.jobs.bypass.cooldown",
            "§7bigbangessentials.jobs.bypass.integration",
            "",
            "§cSomente para admins!"));

        setItem(16, createActionItem(16, Items.WRITABLE_BOOK,
            "§a§lRegras",
            "§7• Bypass não pula licença por padrão",
            "§7• Permissões de bypass são administrativas",
            "§7• Editor NÃO concede permissões a jogadores",
            "§7• Use o sistema de permissões real",
            "§7• Auditoria registrada em cada mudança",
            ""));

        renderBottomActions();
    }

    private String verDescricao(JobRequirements.PermissionMode mode) {
        return switch (mode) {
            case NONE -> "§7Sem verificação de permissão";
            case ALL_REQUIREMENTS -> "§7Exige Rank + Licença + Slot + Integração + Job habilitado";
            case RANK_OR_PERMISSION -> "§7Exige Rank OU permissão específica";
            case RANK_AND_PERMISSION -> "§7Exige Rank E permissão específica";
        };
    }

    private void cyclePermissionMode(ServerPlayer player) {
        JobRequirements.PermissionMode current = definition.requirements().permissionMode();
        JobRequirements.PermissionMode[] modes = JobRequirements.PermissionMode.values();
        JobRequirements.PermissionMode next = modes[(current.ordinal() + 1) % modes.length];

        player.sendSystemMessage(Component.literal("§aModo de permissão alterado: §f"
            + current.name() + " §7→ §f" + next.name()));
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
