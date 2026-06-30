package com.pedrodalben.bigbangessentials.crates.menu;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
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

public class CrateRewardListMenu extends AbstractCrateMenu {

    private static final int ITEMS_PER_PAGE = 28;
    private static final int[] CONTENT_SLOTS = new int[28];
    static {
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                CONTENT_SLOTS[idx++] = row * 9 + col;
            }
        }
    }

    private final String crateKey;
    private final List<CrateReward> rewards;
    private int currentPage;

    public CrateRewardListMenu(int containerId, Inventory playerInventory, ServerPlayer player, String crateKey) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, player, 6);
        this.crateKey = crateKey;
        CrateDefinition crate = CrateService.getInstance().getCrateByKey(crateKey);
        this.rewards = crate != null ? new ArrayList<>(crate.getRewards()) : new ArrayList<>();
        this.currentPage = 0;
        rewards.sort((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()));
        render();
    }

    public static void open(ServerPlayer player, String crateKey) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new CrateRewardListMenu(id, inv, (ServerPlayer) p, crateKey),
            Component.literal("§8§lRecompensas: " + crateKey)
        ));
    }

    private void render() {
        clearContainer();

        fillBorder(4, "§8§m                §r §8[§aRecompensas§8] §8§m                ");

        renderRewardItems();
        renderBottomBar();
    }

    private void renderRewardItems() {
        int totalItems = rewards.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalItems);

        List<CrateReward> pageRewards = rewards.subList(start, end);

        for (int i = 0; i < pageRewards.size() && i < CONTENT_SLOTS.length; i++) {
            CrateReward reward = pageRewards.get(i);
            int slot = CONTENT_SLOTS[i];
            renderRewardItem(slot, reward, start + i);
        }
    }

    private void renderRewardItem(int slot, CrateReward reward, int index) {
        ItemStack icon = reward.getIcon() != null && !reward.getIcon().isEmpty()
            ? reward.getIcon().copy()
            : new ItemStack(Items.PAPER);

        CrateDefinition crate = CrateService.getInstance().getCrateByKey(crateKey);
        CrateRarity rarity = crate != null ? crate.getRarity(reward.getRarityId()) : null;
        String rarityName = rarity != null ? rarity.getName() : reward.getRarityId();

        String chanceStr;
        if (crate != null) {
            double chance = crate.calculateRewardChance(reward.getId());
            chanceStr = String.format("%.2f%%", chance);
        } else {
            chanceStr = "?";
        }

        String rewardType = reward.getType().name();
        String activeStatus = reward.isActive() ? "§aAtivo" : "§cInativo";
        String visibleStatus = reward.isVisibleInPreview() ? "§aSim" : "§cNao";

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7Ordem: §f" + index));
        lore.add(Component.literal("§7Tipo: §f" + rewardType));
        lore.add(Component.literal("§7Raridade: §f" + rarityName));
        lore.add(Component.literal("§7Peso: §f" + reward.getWeight()));
        lore.add(Component.literal("§7Chance: §f" + chanceStr));
        lore.add(Component.literal("§7Ativo: " + activeStatus));
        lore.add(Component.literal("§7Visivel: " + visibleStatus));
        lore.add(Component.literal("§7Milestone: " + boolDisplay(reward.isMilestoneOnly())));
        if (reward.getGlobalLimit() > 0) {
            lore.add(Component.literal("§7Limite global: §f" + reward.getGlobalLimit()));
        }
        if (reward.getPlayerLimit() > 0) {
            lore.add(Component.literal("§7Limite jogador: §f" + reward.getPlayerLimit()));
        }
        lore.add(Component.literal(""));
        lore.add(Component.literal("§e§lClique para editar"));
        lore.add(Component.literal("§7Shift+clique para deletar"));

        icon.set(DataComponents.CUSTOM_NAME, Component.literal(
            (reward.isActive() ? "§a" : "§c") + reward.getName()
        ));
        icon.set(DataComponents.LORE, new ItemLore(lore));

        setItem(slot, icon, p -> {
            if (p.isShiftKeyDown()) {
                CrateConfirmationMenu.open(p, "§c§lDeletar Recompensa",
                    "§7Deletar '" + reward.getName() + "'?",
                    confirmed -> {
                        if (confirmed) {
                            CrateService.getInstance().removeRewardFromCrate(crateKey, reward.getId());
                            p.sendSystemMessage(Component.literal("§cRecompensa '" + reward.getName() + "' removida."));
                        }
                        CrateRewardListMenu.open(p, crateKey);
                    });
            } else {
                editReward(p, reward);
            }
        });
    }

    private void editReward(ServerPlayer p, CrateReward reward) {
        p.closeContainer();
        p.sendSystemMessage(Component.literal("§6=== Editando Recompensa: " + reward.getName() + " ==="));
        p.sendSystemMessage(Component.literal(" §7ID: " + reward.getId()));
        p.sendSystemMessage(Component.literal(" §7Nome: " + reward.getName()));
        p.sendSystemMessage(Component.literal(" §7Tipo: " + reward.getType().name()));
        p.sendSystemMessage(Component.literal(" §7Raridade: " + reward.getRarityId()));
        p.sendSystemMessage(Component.literal(" §7Peso: " + reward.getWeight()));
        p.sendSystemMessage(Component.literal(" §7Itens: " + reward.getItems().size()));
        p.sendSystemMessage(Component.literal(" §7Comandos: " + reward.getCommands().size()));
        p.sendSystemMessage(Component.literal(" §7Ativo: " + boolDisplay(reward.isActive())));
        p.sendSystemMessage(Component.literal(" §7Visivel no preview: " + boolDisplay(reward.isVisibleInPreview())));
        p.sendSystemMessage(Component.literal(" §7Milestone only: " + boolDisplay(reward.isMilestoneOnly())));
        p.sendSystemMessage(Component.literal(" §7Broadcast: " + boolDisplay(reward.isBroadcast())));
        p.sendSystemMessage(Component.literal(" §7Ordem de exibicao: " + reward.getDisplayOrder()));
        if (reward.getGlobalLimit() > 0) {
            p.sendSystemMessage(Component.literal(" §7Limite global: " + reward.getGlobalLimit()));
        }
        if (reward.getPlayerLimit() > 0) {
            p.sendSystemMessage(Component.literal(" §7Limite por jogador: " + reward.getPlayerLimit()));
        }
        p.sendSystemMessage(Component.literal(""));
        p.sendSystemMessage(Component.literal("§eComandos disponiveis:"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setname " + crateKey + " " + reward.getId() + " <nome>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setweight " + crateKey + " " + reward.getId() + " <peso>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setrarity " + crateKey + " " + reward.getId() + " <rarityId>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward toggle " + crateKey + " " + reward.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate reward seticon " + crateKey + " " + reward.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate reward setitems " + crateKey + " " + reward.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate reward additem " + crateKey + " " + reward.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate reward clearitems " + crateKey + " " + reward.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate reward setcommands " + crateKey + " " + reward.getId() + " <cmd1 | cmd2>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward addcommand " + crateKey + " " + reward.getId() + " <comando>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward clearcommands " + crateKey + " " + reward.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate reward remove " + crateKey + " " + reward.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate reward duplicate " + crateKey + " " + reward.getId() + " <novoId> [nome]"));
        p.sendSystemMessage(Component.literal(" §e/crate reward settype " + crateKey + " " + reward.getId() + " <ITEM|COMMAND>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setlore " + crateKey + " " + reward.getId() + " <linha1 | linha2>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setperm " + crateKey + " " + reward.getId() + " <permissao|clear>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setvisible " + crateKey + " " + reward.getId() + " <true|false>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setmilestoneonly " + crateKey + " " + reward.getId() + " <true|false>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setbroadcast " + crateKey + " " + reward.getId() + " <true|false>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setbroadcastmsg " + crateKey + " " + reward.getId() + " <mensagem>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setplayermsg " + crateKey + " " + reward.getId() + " <mensagem>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setdisplayorder " + crateKey + " " + reward.getId() + " <ordem>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setgloballimit " + crateKey + " " + reward.getId() + " <limite>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setplayerlimit " + crateKey + " " + reward.getId() + " <limite>"));
        p.sendSystemMessage(Component.literal(" §e/crate reward setblockingperms " + crateKey + " " + reward.getId() + " <perm1 | perm2>"));
    }

    private void renderBottomBar() {
        int totalPages = Math.max(1, (int) Math.ceil((double) rewards.size() / ITEMS_PER_PAGE));

        setItem(45, createItem(new ItemStack(Items.ARROW), "§a§lPagina Anterior",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages), p -> {
            if (currentPage > 0) {
                currentPage--;
                render();
            }
        });

        setItem(46, createItem(new ItemStack(Items.HOPPER), "§e§lPagina " + (currentPage + 1) + "/" + totalPages,
            "§7" + rewards.size() + " recompensas"), null);

        setItem(47, createItem(new ItemStack(Items.ARROW), "§a§lProxima Pagina",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages), p -> {
            int pages = Math.max(1, (int) Math.ceil((double) rewards.size() / ITEMS_PER_PAGE));
            if (currentPage < pages - 1) {
                currentPage++;
                render();
            }
        });

        setItem(49, createItem(new ItemStack(Items.EMERALD_BLOCK), "§a§lNova Recompensa",
            "§7Cria uma nova recompensa",
            "§7para esta crate"),
            p -> {
                p.closeContainer();
                p.sendSystemMessage(Component.literal("§6=== Criar Nova Recompensa ==="));
                p.sendSystemMessage(Component.literal("§7Use: §e/crate reward create " + crateKey + " <id> <nome> <rarityId>"));
                p.sendSystemMessage(Component.literal("§7Exemplo: §e/crate reward create " + crateKey + " espada_rara \"Espada Rara\" raro"));
            });

        setItem(51, createItem(new ItemStack(Items.ANVIL), "§6§lDuplicar Recompensa",
            "§7Duplica a primeira recompensa",
            "§7selecionada na pagina atual"), null);

        setItem(53, createItem(new ItemStack(Items.BARRIER), "§c§lFechar",
            "§7Fecha a lista de recompensas"),
            p -> {
                p.closeContainer();
                CrateEditMenu.open(p, crateKey);
            });
    }

    @Override
    protected void onClose() {
    }
}
