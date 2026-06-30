package com.pedrodalben.bigbangessentials.crates.menu;

import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
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

public class CrateKeyEditorMenu extends AbstractCrateMenu {

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

    private final List<KeyDefinition> keys;
    private int currentPage;

    public CrateKeyEditorMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, player, 6);
        this.keys = new ArrayList<>(CrateService.getInstance().getAllKeys());
        this.currentPage = 0;
        keys.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));
        render();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new CrateKeyEditorMenu(id, inv, (ServerPlayer) p),
            Component.literal("§8§lEditor de Chaves")
        ));
    }

    private void render() {
        clearContainer();

        fillBorder(1, "§8§m                §r §8[§6Chaves§8] §8§m                ");

        renderKeyItems();
        renderBottomBar();
    }

    private void renderKeyItems() {
        int totalItems = keys.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalItems);

        List<KeyDefinition> pageKeys = keys.subList(start, end);

        for (int i = 0; i < pageKeys.size() && i < CONTENT_SLOTS.length; i++) {
            KeyDefinition key = pageKeys.get(i);
            int slot = CONTENT_SLOTS[i];
            renderKeyItem(slot, key);
        }
    }

    private void renderKeyItem(int slot, KeyDefinition key) {
        ItemStack icon = key.getPhysicalItem() != null && !key.getPhysicalItem().isEmpty()
            ? key.getPhysicalItem().copy()
            : new ItemStack(Items.TRIPWIRE_HOOK);

        String typeStr = key.isVirtual() ? "§dVirtual" : "§7Fisica";
        String activeStr = key.isActive() ? "§aAtivo" : "§cInativo";

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7ID: §f" + key.getId()));
        lore.add(Component.literal("§7Tipo: " + typeStr));
        lore.add(Component.literal("§7Status: " + activeStr));
        lore.add(Component.literal("§7Crates compativeis: §f" + key.getCompatibleCrateIds().size()));
        if (key.getRequiredPermission() != null && !key.getRequiredPermission().isBlank()) {
            lore.add(Component.literal("§7Permissao: §f" + key.getRequiredPermission()));
        }
        lore.add(Component.literal(""));
        lore.add(Component.literal("§eClique para editar"));
        lore.add(Component.literal("§7Shift+clique para deletar"));

        icon.set(DataComponents.CUSTOM_NAME, Component.literal(
            (key.isActive() ? "§a" : "§c") + key.getName()
        ));
        icon.set(DataComponents.LORE, new ItemLore(lore));

        setItem(slot, icon, p -> {
            if (p.isShiftKeyDown()) {
                CrateConfirmationMenu.open(p, "§c§lDeletar Chave",
                    "§7Deletar chave '" + key.getName() + "'?",
                    "§7ID: " + key.getId(),
                    confirmed -> {
                        if (confirmed) {
                            CrateService.getInstance().deleteKey(key.getId());
                            p.sendSystemMessage(Component.literal("§cChave '" + key.getName() + "' deletada."));
                        }
                        CrateKeyEditorMenu.open(p);
                    });
            } else {
                editKey(p, key);
            }
        });
    }

    private void editKey(ServerPlayer p, KeyDefinition key) {
        p.closeContainer();
        p.sendSystemMessage(Component.literal("§6=== Chave: " + key.getName() + " ==="));
        p.sendSystemMessage(Component.literal(" §7ID: " + key.getId()));
        p.sendSystemMessage(Component.literal(" §7Nome: " + key.getName()));
        p.sendSystemMessage(Component.literal(" §7Virtual: " + boolDisplay(key.isVirtual())));
        p.sendSystemMessage(Component.literal(" §7Ativo: " + boolDisplay(key.isActive())));
        p.sendSystemMessage(Component.literal(""));
        p.sendSystemMessage(Component.literal("§eComandos:"));
        p.sendSystemMessage(Component.literal(" §e/crate key setname " + key.getId() + " <nome>"));
        p.sendSystemMessage(Component.literal(" §e/crate key settype " + key.getId() + " <virtual|physical>"));
        p.sendSystemMessage(Component.literal(" §e/crate key toggle " + key.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate key seticon " + key.getId()));
        p.sendSystemMessage(Component.literal(" §e/crate key addcrate " + key.getId() + " <crateKey>"));
    }

    private void renderBottomBar() {
        int totalPages = Math.max(1, (int) Math.ceil((double) keys.size() / ITEMS_PER_PAGE));

        setItem(45, createItem(new ItemStack(Items.ARROW), "§a§lPagina Anterior",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages), p -> {
            if (currentPage > 0) {
                currentPage--;
                render();
            }
        });

        setItem(46, createItem(new ItemStack(Items.HOPPER), "§e§lPagina " + (currentPage + 1) + "/" + totalPages,
            "§7" + keys.size() + " chaves"), null);

        setItem(47, createItem(new ItemStack(Items.ARROW), "§a§lProxima Pagina",
            "§7Pagina " + (currentPage + 1) + "/" + totalPages), p -> {
            int pages = Math.max(1, (int) Math.ceil((double) keys.size() / ITEMS_PER_PAGE));
            if (currentPage < pages - 1) {
                currentPage++;
                render();
            }
        });

        setItem(49, createItem(new ItemStack(Items.EMERALD_BLOCK), "§a§lNova Chave",
            "§7Cria uma nova chave",
            "§7Use o comando abaixo"),
            p -> {
                p.closeContainer();
                p.sendSystemMessage(Component.literal("§6=== Criar Nova Chave ==="));
                p.sendSystemMessage(Component.literal("§7Use: §e/crate key create <id> [nome]"));
                p.sendSystemMessage(Component.literal("§7Exemplo: §e/crate key create chave_vip \"Chave VIP\""));
            });

        setItem(53, createItem(new ItemStack(Items.BARRIER), "§c§lFechar",
            "§7Fecha o editor de chaves"),
            p -> {
                p.closeContainer();
                CrateMainEditorMenu.open(p);
            });
    }

    @Override
    protected void onClose() {
    }
}
