package com.pedrodalben.bigbangessentials.crates.menu;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
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

public class CrateMainEditorMenu extends AbstractCrateMenu {

    private static final int ITEMS_PER_PAGE = 35;
    private static final int[] CONTENT_SLOTS = new int[35];
    static {
        int idx = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 7; col++) {
                CONTENT_SLOTS[idx++] = row * 9 + col + 1;
            }
        }
    }

    private final List<CrateDefinition> crates;
    private int currentPage;

    public CrateMainEditorMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, player, 6);
        this.crates = new ArrayList<>(CrateService.getInstance().getAllCrates());
        this.currentPage = 0;
        crates.sort((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
        render();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new CrateMainEditorMenu(id, inv, (ServerPlayer) p),
            Component.literal("§8§lEditor de Crates")
        ));
    }

    private void render() {
        clearContainer();

        fillBorder(15, "§8§m                  §r §8[§6Crates§8] §8§m                  ");

        renderCrateItems();

        renderBottomBar();
    }

    private void renderCrateItems() {
        int totalItems = crates.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalItems);

        List<CrateDefinition> pageCrates = crates.subList(start, end);

        for (int i = 0; i < pageCrates.size() && i < CONTENT_SLOTS.length; i++) {
            CrateDefinition crate = pageCrates.get(i);
            int slot = CONTENT_SLOTS[i];
            renderCrateItem(slot, crate);
        }
    }

    private void renderCrateItem(int slot, CrateDefinition crate) {
        ItemStack display = crate.getDisplayItem() != null && !crate.getDisplayItem().isEmpty()
            ? crate.getDisplayItem().copy()
            : new ItemStack(Items.CHEST);

        List<Component> loreList = new ArrayList<>();
        loreList.add(Component.literal("§7ID: §f" + crate.getKey()));
        if (crate.getDescription() != null && !crate.getDescription().isEmpty()) {
            loreList.add(Component.literal("§7" + truncate(crate.getDescription(), 50)));
        }
        loreList.add(Component.literal("§7Recompensas: §f" + crate.getRewards().size()));
        loreList.add(Component.literal("§7Raridades: §f" + crate.getRarities().size()));
        loreList.add(Component.literal("§7Status: " + enabledDisplay(crate.isEnabled())));
        loreList.add(Component.literal(""));
        loreList.add(Component.literal("§e§lClique para editar"));

        display.set(DataComponents.CUSTOM_NAME, Component.literal(
            (crate.isEnabled() ? "§a" : "§c") + crate.getDisplayName()
        ));
        display.set(DataComponents.LORE, new ItemLore(loreList));

        setItem(slot, display, p -> CrateEditMenu.open(p, crate.getKey()));
    }

    private void renderBottomBar() {
        int totalPages = Math.max(1, (int) Math.ceil((double) crates.size() / ITEMS_PER_PAGE));

        setItem(45, createItem(new ItemStack(Items.ARROW), "§a§lPagina Anterior",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages,
            "§7" + crates.size() + " crates no total"), p -> {
            if (currentPage > 0) {
                currentPage--;
                render();
            }
        });

        setItem(46, createItem(new ItemStack(Items.HOPPER), "§e§lIr para pagina",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages), null);

        setItem(47, createItem(new ItemStack(Items.ARROW), "§a§lProxima Pagina",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages,
            "§7" + crates.size() + " crates no total"), p -> {
            int pages = Math.max(1, (int) Math.ceil((double) crates.size() / ITEMS_PER_PAGE));
            if (currentPage < pages - 1) {
                currentPage++;
                render();
            }
        });

        setItem(49, createItem(new ItemStack(Items.EMERALD_BLOCK), "§a§lCriar Crate",
            "§7Cria uma nova crate",
            "§e§lClique para criar"),
            p -> {
                p.closeContainer();
                p.sendSystemMessage(Component.literal(""));
                p.sendSystemMessage(Component.literal("§6§l=== Criar Nova Crate ==="));
                p.sendSystemMessage(Component.literal("§7Use: §e/crate create <nome>"));
                p.sendSystemMessage(Component.literal("§7Exemplo: §e/crate create minha_crate"));
                p.sendSystemMessage(Component.literal("§7Depois edite com §e/crate edit <nome>"));
                p.sendSystemMessage(Component.literal(""));
            });

        setItem(50, createItem(new ItemStack(Items.TRIPWIRE_HOOK), "§6§lGerenciar Chaves",
            "§7Editor de chaves das crates"),
            p -> CrateKeyEditorMenu.open(p));

        setItem(51, createItem(new ItemStack(Items.EXPERIENCE_BOTTLE), "§d§lGerenciar Raridades",
            "§7Gerencia raridades globais"), null);

        setItem(52, createItem(new ItemStack(Items.COMPARATOR), "§e§lRecarregar",
            "§7Recarrega todas as crates do disco"),
            p -> {
                CrateService.getInstance().reload();
                p.sendSystemMessage(Component.literal("§aCrates recarregadas do disco."));
                p.closeContainer();
            });

        setItem(53, createItem(new ItemStack(Items.BARRIER), "§c§lFechar",
            "§7Fecha o editor de crates"),
            p -> p.closeContainer());
    }
}
