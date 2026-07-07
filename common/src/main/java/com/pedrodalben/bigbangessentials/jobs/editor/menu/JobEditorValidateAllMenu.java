package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.jobs.catalog.*;
import com.pedrodalben.bigbangessentials.jobs.editor.JobConfigurationValidator;
import com.pedrodalben.bigbangessentials.jobs.editor.JobEditorValidationResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class JobEditorValidateAllMenu extends AbstractJobsEditorMenu {

    private final List<JobEditorValidationResult> results;

    public JobEditorValidateAllMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(containerId, playerInventory, player, 6);
        this.results = JobCatalogRegistry.getInstance().getAllJobs().values().stream()
            .map(def -> JobConfigurationValidator.getInstance().validate(def))
            .toList();
        render();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new JobEditorValidateAllMenu(id, inv, (ServerPlayer) p),
            Component.literal("§8§lValidação de Todos os Jobs")
        ));
    }

    private void render() {
        clearContainer();
        fillBorder(9, "§8§m        §r §8[§9Validação de Todos os Jobs§8] §8§m        ");

        long validCount = results.stream().filter(JobEditorValidationResult::valid).count();
        long invalidCount = results.size() - validCount;
        long totalWarnings = results.stream().mapToLong(r -> r.warnings().size()).sum();
        long totalErrors = results.stream().mapToLong(r -> r.errors().size()).sum();

        setItem(4, createActionItem(4, Items.LIME_WOOL,
            "§a§lResultado",
            "§7Jobs válidos: §a" + validCount,
            "§7Jobs com erro: §c" + invalidCount,
            "§7Total avisos: §e" + totalWarnings,
            "§7Total erros: §c" + totalErrors,
            ""));

        int slot = 10;
        for (JobEditorValidationResult result : results) {
            if (slot > 34) break;

            boolean valid = result.valid();
            ItemStack stack = new ItemStack(valid ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                (valid ? "§a✔ " : "§c✘ ") + result.jobId()));

            java.util.List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.literal("§7Status: " + (valid ? "§aVálido" : "§cInválido")));
            lore.add(Component.literal("§7Erros: §c" + result.errors().size()));
            lore.add(Component.literal("§7Avisos: §e" + result.warnings().size()));

            for (JobEditorValidationResult.ValidationError error : result.errors()) {
                lore.add(Component.literal("§c  • " + error.field() + ": " + error.cause()));
            }
            for (JobEditorValidationResult.ValidationWarning warning : result.warnings()) {
                lore.add(Component.literal("§e  • " + warning.field() + ": " + warning.message()));
            }

            stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
            setItem(slot++, stack);
        }

        renderBottomActions();
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
}
