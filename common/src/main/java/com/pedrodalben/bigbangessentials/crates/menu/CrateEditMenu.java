package com.pedrodalben.bigbangessentials.crates.menu;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateMilestone;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import com.pedrodalben.bigbangessentials.crates.particle.CrateParticleManager;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class CrateEditMenu extends AbstractCrateMenu {

    private final String crateKey;
    private CrateDefinition crate;

    public CrateEditMenu(int containerId, Inventory playerInventory, ServerPlayer player, String crateKey) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, player, 6);
        this.crateKey = crateKey;
        this.crate = CrateService.getInstance().getCrateByKey(crateKey);
        render();
    }

    public static void open(ServerPlayer player, String crateKey) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new CrateEditMenu(id, inv, (ServerPlayer) p, crateKey),
            Component.literal("§8§lEditando: " + crateKey)
        ));
    }

    private void render() {
        clearContainer();
        crate = CrateService.getInstance().getCrateByKey(crateKey);
        if (crate == null) {
            player.closeContainer();
            player.sendSystemMessage(Component.literal("§cCrate nao encontrada: " + crateKey));
            return;
        }

        fillBorder(7, "§8§m                §r §8[§6" + translateColorCodes(truncate(crate.getDisplayName(), 20)) + "§8] §8§m                ");

        renderGeneralInfo();
        renderEditingSections();
        renderBottomButtons();
    }

    private void renderGeneralInfo() {
        ItemStack icon = crate.getDisplayItem() != null && !crate.getDisplayItem().isEmpty()
            ? crate.getDisplayItem().copy()
            : new ItemStack(Items.CHEST);

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.literal("§7ID: §f" + crate.getKey()));
        infoLore.add(Component.literal("§7Nome: §f" + translateColorCodes(crate.getDisplayName())));
        infoLore.add(Component.literal("§7Descricao: §f" + translateColorCodes(truncate(nonNull(crate.getDescription(), ""), 40))));
        infoLore.add(Component.literal("§7Tipo: §f" + crate.getOpeningType().name()));
        infoLore.add(Component.literal("§7Cooldown: §f" + crate.getCooldownMillis() + "ms"));
        infoLore.add(Component.literal("§7Custo: §f" + (crate.getCost() > 0 ? "$" + crate.getCost() : "Gratuito")));
        infoLore.add(Component.literal("§7Status: " + enabledDisplay(crate.isEnabled())));
        infoLore.add(Component.literal("§7Recompensas: §f" + crate.getRewards().size()));
        infoLore.add(Component.literal("§7Raridades: §f" + crate.getRarities().size()));
        infoLore.add(Component.literal("§7Milestones: §f" + crate.getMilestones().size()));
        infoLore.add(Component.literal("§7Locais: §f" + CrateService.getInstance().getLocationsByCrate(crate.getKey()).size()));
        icon.set(DataComponents.CUSTOM_NAME, Component.literal(
            (crate.isEnabled() ? "§a" : "§c") + translateColorCodes(crate.getDisplayName())
        ));
        icon.set(DataComponents.LORE, new ItemLore(infoLore));

        setItem(4, icon);
    }

    private void renderEditingSections() {
        setItem(18, createItem(new ItemStack(Items.NAME_TAG), "§6§lInformacoes Gerais",
            "§7Edite nome, descricao, icone",
            "§7e status da crate"), p -> {
            p.closeContainer();
            p.sendSystemMessage(Component.literal("§6=== Editando: " + crate.getDisplayName() + " ==="));
            p.sendSystemMessage(Component.literal("§7Use comandos para editar:"));
            p.sendSystemMessage(Component.literal(" §e/crate setname " + crateKey + " <nome>"));
            p.sendSystemMessage(Component.literal(" §e/crate setdesc " + crateKey + " <descricao>"));
            p.sendSystemMessage(Component.literal(" §e/crate toggle " + crateKey));
            p.sendSystemMessage(Component.literal(" §e/crate seticon " + crateKey));
        });

        setItem(19, createItem(new ItemStack(Items.ENDER_CHEST), "§5§lPreview",
            "§7Configuracoes de preview",
            "§7(visao do jogador)"), p -> {
            p.closeContainer();
            p.sendSystemMessage(Component.literal("§6Preview configurado via comandos:"));
            p.sendSystemMessage(Component.literal(" §e/crate preview " + crateKey));
        });

        setItem(20, createItem(new ItemStack(Items.REPEATER), "§e§lConfiguracao de Abertura",
            "§7Tipo de abertura, sons,",
            "§7animacao e particulas"), p -> {
            p.closeContainer();
            p.sendSystemMessage(Component.literal("§6=== Config. Abertura: " + crate.getDisplayName() + " ==="));
            p.sendSystemMessage(Component.literal(" §e/crate setopening " + crateKey + " <tipo>"));
            p.sendSystemMessage(Component.literal(" §7Tipos: NONE, VIRTUAL, PHYSICAL"));
        });

        setItem(21, createItem(new ItemStack(Items.IRON_DOOR), "§c§lRequisitos",
            "§7Chaves, permissoes, custo,",
            "§7cooldown e limites"), p -> {
            p.closeContainer();
            p.sendSystemMessage(Component.literal("§6=== Requisitos: " + crate.getDisplayName() + " ==="));
            p.sendSystemMessage(Component.literal(" §e/crate setkey " + crateKey + " <keyId>"));
            p.sendSystemMessage(Component.literal(" §e/crate setcost " + crateKey + " <valor>"));
            p.sendSystemMessage(Component.literal(" §e/crate setcooldown " + crateKey + " <ms>"));
            p.sendSystemMessage(Component.literal(" §e/crate setperm " + crateKey + " <permissao>"));
        });

        setItem(22, createItem(new ItemStack(Items.CHEST_MINECART), "§a§lRecompensas",
            "§7Lista/edita recompensas",
            "§7(" + crate.getRewards().size() + " recompensas)"), p -> {
            CrateRewardListMenu.open(p, crateKey);
        });

        setItem(23, createItem(new ItemStack(Items.DIAMOND), "§b§lRaridades",
            "§7Gerencia raridades",
            "§7(" + crate.getRarities().size() + " raridades)"), p -> {
            p.closeContainer();
            p.sendSystemMessage(Component.literal("§6=== Raridades: " + crate.getDisplayName() + " ==="));
            for (CrateRarity r : crate.getRarities()) {
                String status = r.isActive() ? "§aAtivo" : "§cInativo";
                p.sendSystemMessage(Component.literal(" §f- " + translateColorCodes(r.getName()) + " §7(" + r.getId() + ") " + status + " §7Peso: " + r.getWeight()));
            }
            p.sendSystemMessage(Component.literal(""));
            p.sendSystemMessage(Component.literal("§e/crate addrarity " + crateKey + " <id> <nome> <cor> <peso>"));
            p.sendSystemMessage(Component.literal("§e/crate removerarity " + crateKey + " <id>"));
            p.sendSystemMessage(Component.literal("§e/crate rarity setname " + crateKey + " <id> <nome>"));
            p.sendSystemMessage(Component.literal("§e/crate rarity setcolor " + crateKey + " <id> <cor>"));
            p.sendSystemMessage(Component.literal("§e/crate rarity setweight " + crateKey + " <id> <peso>"));
            p.sendSystemMessage(Component.literal("§e/crate rarity seticon " + crateKey + " <id>"));
            p.sendSystemMessage(Component.literal("§e/crate rarity setlore " + crateKey + " <id> <linha1 | linha2>"));
            p.sendSystemMessage(Component.literal("§e/crate rarity toggle " + crateKey + " <id>"));
            p.sendSystemMessage(Component.literal("§e/crate rarity setpriority " + crateKey + " <id> <prioridade>"));
            p.sendSystemMessage(Component.literal("§e/crate rarity setdisplayorder " + crateKey + " <id> <ordem>"));
        });

        setItem(24, createItem(new ItemStack(Items.NETHER_STAR), "§d§lMilestones",
            "§7Recompensas por numero",
            "§7de aberturas (" + crate.getMilestones().size() + ")"), p -> {
            p.closeContainer();
            p.sendSystemMessage(Component.literal("§6=== Milestones: " + crate.getDisplayName() + " ==="));
            for (CrateMilestone m : crate.getMilestones()) {
                String status = m.isActive() ? "§aAtivo" : "§cInativo";
                String repeatable = m.isRepeatable() ? "§dRepetivel" : "§7Unico";
                p.sendSystemMessage(Component.literal(
                    " §f- " + translateColorCodes(m.getName()) + " §7(" + m.getId() + ") "
                        + status + " §7Reward: " + m.getRewardId()
                        + " §7Aberturas: " + m.getRequiredOpenings()
                        + " §7Tipo: " + repeatable
                        + " §7Ordem: " + m.getDisplayOrder()));
                if (m.getDescription() != null && !m.getDescription().isBlank()) {
                    p.sendSystemMessage(Component.literal("   §7Desc: " + m.getDescription()));
                }
            }
            p.sendSystemMessage(Component.literal(""));
            p.sendSystemMessage(Component.literal("§e/crate addmilestone " + crateKey + " <id> <nome> <rewardId> <aberturas>"));
            p.sendSystemMessage(Component.literal("§e/crate milestone setname " + crateKey + " <id> <nome>"));
            p.sendSystemMessage(Component.literal("§e/crate milestone setdescription " + crateKey + " <id> <descricao>"));
            p.sendSystemMessage(Component.literal("§e/crate milestone setreward " + crateKey + " <id> <rewardId>"));
            p.sendSystemMessage(Component.literal("§e/crate milestone setopenings " + crateKey + " <id> <aberturas>"));
            p.sendSystemMessage(Component.literal("§e/crate milestone toggle " + crateKey + " <id>"));
            p.sendSystemMessage(Component.literal("§e/crate milestone setrepeatable " + crateKey + " <id> <true|false>"));
            p.sendSystemMessage(Component.literal("§e/crate milestone setdisplayorder " + crateKey + " <id> <ordem>"));
            p.sendSystemMessage(Component.literal("§e/crate milestone remove " + crateKey + " <id>"));
        });

        setItem(25, createItem(new ItemStack(Items.ARMOR_STAND), "§7§lVisual",
            "§7Hologramas, particulas e",
            "§7sons da crate"), p -> {
            p.closeContainer();
            p.sendSystemMessage(Component.literal("§6=== Visual: " + crate.getDisplayName() + " ==="));
            p.sendSystemMessage(Component.literal(" §7Holograma: " + enabledDisplay(crate.getVisualConfig().isHologramEnabled())));
            p.sendSystemMessage(Component.literal(" §7Particulas IDLE: " + crate.getVisualConfig().getIdleParticleConfig().getParticleType()));
            p.sendSystemMessage(Component.literal(" §7Som abertura: " + crate.getVisualConfig().getOpenSound()));
        });

        setItem(26, createItem(new ItemStack(Items.COMPASS), "§2§lLocais",
            "§7Locais onde a crate",
            "§7esta posicionada no mundo",
            "§7(" + CrateService.getInstance().getLocationsByCrate(crate.getKey()).size() + " locais)"), p -> {
            p.closeContainer();
            List<CrateLocation> locs = CrateService.getInstance().getLocationsByCrate(crate.getKey());
            if (locs.isEmpty()) {
                p.sendSystemMessage(Component.literal("§7Nenhum local definido para esta crate."));
            } else {
                p.sendSystemMessage(Component.literal("§6=== Locais: " + crate.getDisplayName() + " ==="));
                for (CrateLocation loc : locs) {
                    p.sendSystemMessage(Component.literal(" §f- " + loc.getWorldName() + " §7(" + loc.getX() + ", " + loc.getY() + ", " + loc.getZ() + ")"));
                }
            }
            p.sendSystemMessage(Component.literal("§eUse /crate setlocation " + crateKey + " para adicionar"));
            p.sendSystemMessage(Component.literal("§e/crate location settemplate <locationId> <template>"));
            p.sendSystemMessage(Component.literal("§e/crate location setoffsety <locationId> <offset>"));
            p.sendSystemMessage(Component.literal("§e/crate location togglehologram <locationId>"));
            p.sendSystemMessage(Component.literal("§e/crate location toggleparticle <locationId>"));
            p.sendSystemMessage(Component.literal("§e/crate location toggle <locationId>"));
        });
    }

    private void renderBottomButtons() {
        setItem(45, createItem(new ItemStack(Items.ARROW), "§a§lVoltar",
            "§7Volta ao menu principal"),
            p -> CrateMainEditorMenu.open(p));

        setItem(48, createItem(new ItemStack(Items.REDSTONE_BLOCK), "§c§lDeletar Crate",
            "§7Remove esta crate permanentemente",
            "§c§lCUIDADO: Esta acao nao pode ser desfeita!",
            "§eClique para confirmar"),
            p -> {
                CrateConfirmationMenu.open(p, "§c§lDeletar Crate",
                    "§7Tem certeza que deseja deletar",
                    "§7a crate §f" + crate.getDisplayName() + "§7?",
                    confirmed -> {
                        if (confirmed) {
                            CrateService svc = CrateService.getInstance();
                            for (CrateLocation loc : svc.getLocationsByCrate(crateKey)) {
                                CrateHologramManager.getInstance().removeHologram(loc.getId());
                                CrateParticleManager.getInstance().stopParticles(loc.getId());
                                svc.deleteLocation(loc.getId());
                            }
                            svc.deleteCrate(crateKey);
                            p.sendSystemMessage(Component.literal("§aCrate '" + crate.getDisplayName() + "' deletada."));
                            CrateMainEditorMenu.open(p);
                        } else {
                            CrateEditMenu.open(p, crateKey);
                        }
                    });
            });

        setItem(49, createItem(new ItemStack(Items.EMERALD), "§a§lSalvar",
            "§7Salva a crate atual"),
            p -> {
                CrateService.getInstance().saveCrate(crate);
                p.sendSystemMessage(Component.literal("§aCrate '" + crate.getDisplayName() + "' salva!"));
                render();
            });

        setItem(50, createItem(new ItemStack(Items.BOOK), "§6§lToggle Ativo/Inativo",
            "§7Status atual: " + enabledDisplay(crate.isEnabled())),
            p -> {
                crate.setEnabled(!crate.isEnabled());
                CrateService.getInstance().saveCrate(crate);
                render();
            });

        setItem(51, createItem(new ItemStack(Items.ENDER_PEARL), "§5§lVisualizar Preview",
            "§7Veja como os jogadores",
            "§7veem esta crate"),
            p -> CratePreviewMenu.open(p, crateKey));

        setItem(53, createItem(new ItemStack(Items.BARRIER), "§c§lFechar",
            "§7Fecha o editor"),
            p -> p.closeContainer());
    }
}
