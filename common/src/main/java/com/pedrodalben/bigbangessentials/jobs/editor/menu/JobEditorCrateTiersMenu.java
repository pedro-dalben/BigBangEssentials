package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCrateTierProfile;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCrateTierProfile.CrateTier;
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

public class JobEditorCrateTiersMenu extends AbstractJobsEditorMenu {

    private final String scope;

    public JobEditorCrateTiersMenu(int containerId, Inventory playerInventory,
                                   ServerPlayer player, String scope) {
        super(containerId, playerInventory, player, 6);
        this.scope = scope;
        render();
    }

    public static void open(ServerPlayer player, String scope) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorCrateTiersMenu(id, inv, (ServerPlayer) p, scope),
            Component.literal("§8§lCrate Tiers — BigBangCraft")
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(10, "§8§m          §r §8[§5Crate Tiers§8] §8§m          ");

        renderTier(10, "beginner", "§aCaixa Iniciante", Items.GREEN_WOOL);
        renderTier(13, "intermediate", "§6Caixa Intermediária", Items.ORANGE_WOOL);
        renderTier(16, "advanced", "§5Caixa Avançada", Items.PURPLE_WOOL);

        renderAvailableCrates();
        renderBottomActions();
    }

    private void renderTier(int baseSlot, String tierId, String displayName, Item icon) {
        CrateTier tier = getTier(tierId);
        String status;
        if (!tier.enabled()) {
            status = "§cDesativado";
        } else if (!tier.isConfigured()) {
            status = "§6Configuração Pendente";
        } else {
            status = "§aVinculado: " + tier.crateId();
        }

        ItemStack stack = new ItemStack(icon);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7Tier ID: §f" + tier.tierId()));
        lore.add(Component.literal("§7Nome Exibido: §f" + tier.displayName()));
        lore.add(Component.literal("§7Status: " + translateColorCodes(status)));
        lore.add(Component.literal("§7Crate: §f" + (tier.crateId() != null ? tier.crateId() : "Nenhuma")));
        lore.add(Component.literal("§7Chave: §f" + (tier.keyType() != null ? tier.keyType() : "Nenhuma")));
        lore.add(Component.literal("§7Tipo: §f" + (tier.virtualKey() ? "Virtual" : "Física")));
        lore.add(Component.literal(""));

        if (!tier.isConfigured()) {
            lore.add(Component.literal("§e⚠ Vincule uma crate e chave existentes"));
            lore.add(Component.literal("§epara ativar este tier."));
        }
        lore.add(Component.literal(""));
        lore.add(Component.literal("§e§lClique para configurar"));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));

        setItem(baseSlot, stack, p -> selectCrateForTier(p, tierId));
        setItem(baseSlot + 1, createActionItem(baseSlot + 1, tier.enabled() ? MenuIcons.ACTIVE : MenuIcons.INACTIVE,
            "§e§lAtivar/Desativar",
            "§7Status atual: " + (tier.enabled() ? "§aAtivo" : "§cInativo"),
            "",
            "§eClique para alternar"));
    }

    private void renderAvailableCrates() {
        CrateService cs = CrateService.getInstance();
        List<CrateDefinition> crates = cs != null ? cs.getAllCrates() : List.of();

        for (int i = 0; i < Math.min(crates.size(), 14); i++) {
            CrateDefinition crate = crates.get(i);
            int slot = 28 + i;
            if (slot > 41) break;

            ItemStack stack = crate.getDisplayItem() != null && !crate.getDisplayItem().isEmpty()
                ? crate.getDisplayItem().copy() : new ItemStack(Items.CHEST);

            stack.set(DataComponents.CUSTOM_NAME, Component.literal("§a" + crate.getKey()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Nome: §f" + crate.getDisplayName()));
            lore.add(Component.literal("§7Status: " + (crate.isEnabled() ? "§aAtiva" : "§cDesativada")));
            lore.add(Component.literal("§7Recompensas: §f" + crate.getRewards().size()));
            lore.add(Component.literal(""));
            lore.add(Component.literal("§e§lClique para vincular a um tier"));
            stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));

            setItem(slot, stack, p -> {
                p.sendSystemMessage(Component.literal("§eUse /jobsadmin editor vincular <tier> <crateId> <keyId> para vincular."));
            });
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

    private void selectCrateForTier(ServerPlayer player, String tierId) {
        player.sendSystemMessage(Component.literal("§aSelecione a crate para o tier '" + tierId
            + "' clicando em uma crate abaixo."));
        player.sendSystemMessage(Component.literal("§7Ou use: /jobsadmin editor tiers vincular " + tierId + " <crateId> <keyId>"));
    }

    private CrateTier getTier(String tierId) {
        return switch (tierId) {
            case "beginner" -> JobCrateTierProfile.DEFAULT.beginnerTier();
            case "intermediate" -> JobCrateTierProfile.DEFAULT.intermediateTier();
            case "advanced" -> JobCrateTierProfile.DEFAULT.advancedTier();
            default -> CrateTier.unconfigured(tierId, tierId);
        };
    }
}
