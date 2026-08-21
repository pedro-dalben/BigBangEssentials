package com.pedrodalben.bigbangessentials.crates.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CratePreviewMenu extends AbstractCrateMenu {

    private final String crateKey;
    private CrateDefinition crate;

    private static final int[] REWARD_AREA = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public CratePreviewMenu(int containerId, Inventory playerInventory, ServerPlayer player, String crateKey) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, player, 6);
        this.crateKey = crateKey;
        this.crate = CrateService.getInstance().getCrateByKey(crateKey);
        render();
    }

    public static void open(ServerPlayer player, String crateKey) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new CratePreviewMenu(id, inv, (ServerPlayer) p, crateKey),
            Component.literal("§8§lPreview: " + crateKey)
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

        fillBorder(3);

        renderTitleInfo();
        renderRewards();
        renderActionButtons();
    }

    private void renderTitleInfo() {
        ItemStack icon = crate.getDisplayItem() != null && !crate.getDisplayItem().isEmpty()
            ? crate.getDisplayItem().copy()
            : new ItemStack(Items.CHEST);

        List<Component> lore = new ArrayList<>();
        if (crate.getDescription() != null && !crate.getDescription().isEmpty()) {
            lore.add(Component.literal("§7" + translateColorCodes(crate.getDescription())));
            lore.add(Component.literal(""));
        }
        lore.add(Component.literal("§7Recompensas: §f" + crate.getRewards().size()));
        lore.add(Component.literal("§7Raridades: §f" + crate.getRarities().size()));

        if (crate.getRequirements().hasKeyRequirement()) {
            lore.add(Component.literal(""));
            lore.add(Component.literal("§7Chaves necessarias:"));
            for (String keyId : crate.getRequirements().getAcceptedKeyIds()) {
                lore.add(Component.literal(" §8- §f" + keyId));
            }
        }

        icon.setHoverName(Component.literal("§6§l" + translateColorCodes(crate.getDisplayName())));
        ItemLoreHelper.setLore(icon, lore);
        setItem(4, icon);
    }

    private void renderRewards() {
        List<CrateReward> crateRewards = new ArrayList<>(crate.getRewards());
        crateRewards.sort(Comparator.comparingInt(CrateReward::getDisplayOrder));

        List<CrateRarity> rarities = crate.getRarities().stream()
            .filter(CrateRarity::isActive)
            .sorted(Comparator.comparingInt(CrateRarity::getDisplayOrder))
            .toList();

        java.util.Map<String, CrateRarity> rarityMap = new java.util.HashMap<>();
        for (CrateRarity r : rarities) {
            rarityMap.put(r.getId(), r);
        }

        int rewardIndex = 0;
        for (CrateReward reward : crateRewards) {
            if (rewardIndex >= REWARD_AREA.length) break;
            if (!reward.isVisibleInPreview()) continue;
            if (!reward.isActive()) continue;

            int slot = REWARD_AREA[rewardIndex];
            renderRewardItem(slot, reward, rarityMap.get(reward.getRarityId()));
            rewardIndex++;
        }
    }

    private void renderRewardItem(int slot, CrateReward reward, CrateRarity rarity) {
        ItemStack icon;
        if (reward.getType().name().equals("ITEM") && !reward.getItems().isEmpty()) {
            icon = reward.getItems().get(0).copy();
        } else {
            icon = reward.getIcon() != null && !reward.getIcon().isEmpty()
                ? reward.getIcon().copy()
                : new ItemStack(Items.PAPER);
        }

        double chance = crate.calculateRewardChance(reward.getId());
        String chanceStr = String.format("%.2f%%", chance);

        String rarityColor = translateColorCodes(rarity != null && rarity.getColor() != null ? rarity.getColor() : "§f");
        String rarityName = rarity != null ? rarity.getName() : reward.getRarityId();

        String displayName = rarityColor + translateColorCodes(reward.getName());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7Raridade: " + rarityColor + translateColorCodes(rarityName)));
        lore.add(Component.literal("§7Chance: §f" + chanceStr));

        if (reward.getType().name().equals("ITEM") && !reward.getItems().isEmpty()) {
            lore.add(Component.literal(""));
            lore.add(Component.literal("§7Itens:"));
            for (ItemStack item : reward.getItems()) {
                if (!item.isEmpty()) {
                    lore.add(Component.literal(" §8- §r" + item.getHoverName().getString() + " §7x" + item.getCount()));
                }
            }
        }

        if (reward.getRequiredPermission() != null && !reward.getRequiredPermission().isBlank()) {
            lore.add(Component.literal(""));
            lore.add(Component.literal("§cRequer permissao: " + reward.getRequiredPermission()));
        }

        icon.setHoverName(Component.literal(displayName));
        ItemLoreHelper.setLore(icon, lore);

        setItem(slot, icon);
    }

    private void renderActionButtons() {
        setItem(48, createItem(new ItemStack(Items.TRIPWIRE_HOOK), "§a§lAbrir Crate",
            "§7Clique para abrir esta crate",
            "§7(se tiver uma chave)"),
            p -> {
                p.closeContainer();
                p.sendSystemMessage(Component.literal("§7Abrindo crate: §f" + crate.getDisplayName()));
                p.sendSystemMessage(Component.literal("§7Use §e/crate open " + crateKey + "§7 para abrir."));
            });

        if (crate.getPreviewConfig().isShowOpenAllButton()) {
            setItem(50, createItem(new ItemStack(Items.HOPPER), "§e§lAbrir Multiplo",
                "§7Abre a crate varias vezes",
                "§7Use: §e/crate massopen " + crateKey + " <quantidade>"),
                p -> {
                    p.closeContainer();
                    p.sendSystemMessage(ChatComponentUtil.createClickableSuggestion(
                        "§eComando pronto: §6/crate massopen " + crateKey + " ",
                        "crate massopen " + crateKey + " ",
                        "Clique para preencher o comando e informe a quantidade depois."));
                });
        }

        setItem(53, createItem(new ItemStack(Items.BARRIER), "§c§lFechar",
            "§7Fecha o preview"),
            p -> p.closeContainer());
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }
}
