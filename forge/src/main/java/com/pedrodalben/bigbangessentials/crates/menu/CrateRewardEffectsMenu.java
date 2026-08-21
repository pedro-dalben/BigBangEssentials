package com.pedrodalben.bigbangessentials.crates.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class CrateRewardEffectsMenu extends AbstractCrateMenu {

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
    private final CrateReward reward;
    private int currentPage;

    public CrateRewardEffectsMenu(int containerId, Inventory playerInventory, ServerPlayer player,
                                   String crateKey, CrateReward reward) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, player, 6);
        this.crateKey = crateKey;
        this.reward = reward;
        this.currentPage = 0;
        render();
    }

    public static void open(ServerPlayer player, String crateKey, CrateReward reward) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new CrateRewardEffectsMenu(id, inv, (ServerPlayer) p, crateKey, reward),
            Component.literal("§8§lEfeitos: " + reward.getName())
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(4, "§8§m                §r §8[§eEfeitos§8] §8§m                ");

        List<String> effects = reward.getWinEffects();
        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, effects.size());

        for (int i = start; i < end; i++) {
            int slotIdx = i - start;
            int slot = CONTENT_SLOTS[slotIdx];
            String effect = effects.get(i);

            ItemStack icon = getEffectIcon(effect);
            String type = effect.contains(":") ? effect.substring(0, effect.indexOf(':')).toUpperCase() : "?";
            String value = effect.contains(":") ? effect.substring(effect.indexOf(':') + 1) : effect;

            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Tipo: §f" + type));
            lore.add(Component.literal("§7Valor: §f" + value));
            lore.add(Component.literal(""));
            lore.add(Component.literal("§e§lClique para editar via comando"));
            lore.add(Component.literal("§7Shift+clique para remover"));

            icon.setHoverName(Component.literal("§f#" + (start + slotIdx) + " §8- §7" + type));
            ItemLoreHelper.setLore(icon, lore);

            int idx = i;
            setItem(slot, icon, p -> {
                if (p.isShiftKeyDown()) {
                    reward.removeWinEffect(idx);
                    CrateService.getInstance().updateReward(crateKey, reward);
                    p.sendSystemMessage(Component.literal("§cEfeito #" + idx + " removido."));
                    render();
                } else {
                    p.closeContainer();
                    p.sendSystemMessage(Component.literal("§6=== Editando efeito #" + idx + " ==="));
                    p.sendSystemMessage(Component.literal(" §7Tipo: " + type));
                    p.sendSystemMessage(Component.literal(" §7Valor: " + value));
                    p.sendSystemMessage(Component.literal(""));
                    p.sendSystemMessage(Component.literal("§eUse: /crate reward removeffect " + crateKey + " " + reward.getId() + " " + idx));
                    p.sendSystemMessage(Component.literal("§eOu remova shifted aqui e adicione novo com os botoes abaixo."));
                }
            });
        }

        renderBottomBar(effects.size());
    }

    private ItemStack getEffectIcon(String effect) {
        String type = effect.contains(":") ? effect.substring(0, effect.indexOf(':')).toUpperCase() : "";
        return switch (type) {
            case "SOUND" -> new ItemStack(Items.MUSIC_DISC_CAT);
            case "FIREWORK" -> new ItemStack(Items.FIREWORK_ROCKET);
            case "PARTICLE" -> new ItemStack(Items.HEART_OF_THE_SEA);
            default -> new ItemStack(Items.PAPER);
        };
    }

    private void renderBottomBar(int totalEffects) {
        int totalPages = Math.max(1, (int) Math.ceil((double) totalEffects / ITEMS_PER_PAGE));

        setItem(45, createItem(new ItemStack(Items.ARROW), "§a§lPagina Anterior",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages), p -> {
            if (currentPage > 0) {
                currentPage--;
                render();
            }
        });

        setItem(46, createItem(new ItemStack(Items.HOPPER), "§e§lPagina " + (currentPage + 1) + "/" + totalPages,
            "§7" + totalEffects + " efeitos"), null);

        setItem(47, createItem(new ItemStack(Items.ARROW), "§a§lProxima Pagina",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages), p -> {
            int pages = Math.max(1, (int) Math.ceil((double) totalEffects / ITEMS_PER_PAGE));
            if (currentPage < pages - 1) {
                currentPage++;
                render();
            }
        });

        setItem(48, createItem(new ItemStack(Items.MUSIC_DISC_CAT), "§b§lAdicionar Som",
            "§7Adiciona efeito sonoro padrao",
            "§eClique para adicionar"), p -> {
            reward.addWinEffect("SOUND:minecraft:entity.firework_rocket.launch");
            CrateService.getInstance().updateReward(crateKey, reward);
            p.sendSystemMessage(Component.literal("§aSom adicionado! Use /crate reward addeffect para personalizar."));
            render();
        });

        setItem(49, createItem(new ItemStack(Items.FIREWORK_ROCKET), "§b§lAdicionar Fogos",
            "§7Adiciona efeito de fogos padrao",
            "§eClique para adicionar"), p -> {
            reward.addWinEffect("FIREWORK:RED_STAR");
            CrateService.getInstance().updateReward(crateKey, reward);
            p.sendSystemMessage(Component.literal("§aFogos adicionados! Use /crate reward addeffect para personalizar."));
            render();
        });

        setItem(50, createItem(new ItemStack(Items.HEART_OF_THE_SEA), "§b§lAdicionar Particula",
            "§7Adiciona efeito de particula padrao",
            "§eClique para adicionar"), p -> {
            reward.addWinEffect("PARTICLE:minecraft:heart");
            CrateService.getInstance().updateReward(crateKey, reward);
            p.sendSystemMessage(Component.literal("§aParticula adicionada! Use /crate reward addeffect para personalizar."));
            render();
        });

        setItem(53, createItem(new ItemStack(Items.BARRIER), "§c§lVoltar",
            "§7Voltar para lista de recompensas"), p -> {
            CrateRewardListMenu.open(p, crateKey);
        });
    }
}
