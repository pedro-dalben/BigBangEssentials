package com.pedrodalben.bigbangessentials.menu.integration.kits;

import com.pedrodalben.bigbangessentials.kits.Kit;
import com.pedrodalben.bigbangessentials.kits.KitManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class KitMenuSupport {
    private KitMenuSupport() {}

    static List<Kit> getSortedKits() {
        List<Kit> kits = new ArrayList<>(KitManager.getInstance().getAllKits());
        kits.sort(Comparator.comparing(Kit::getName, String.CASE_INSENSITIVE_ORDER));
        return kits;
    }

    static Map<String, Object> buildKitPlaceholders(ServerPlayer player, Kit kit) {
        Map<String, Object> values = new LinkedHashMap<>();
        KitStatus status = classify(player, kit);

        int itemCount = (int) kit.getItems().stream()
            .filter(item -> item != null && !item.isEmpty())
            .count();
        int usageCount = player != null ? KitManager.getInstance().getUsageCount(player.getUUID(), kit.getName()) : 0;

        values.put("kit_name", kit.getName());
        values.put("kit_display_name", kit.getDisplayName());
        values.put("kit_description", kit.getDescription() != null ? kit.getDescription() : "");
        values.put("kit_description_short", trim(kit.getDescription(), 80));
        values.put("kit_items", String.valueOf(itemCount));
        values.put("kit_item_count", String.valueOf(itemCount));
        values.put("kit_cooldown_display", kit.getCooldownMillis() > 0 ? formatDuration(kit.getCooldownMillis()) : "Sem cooldown");
        values.put("kit_remaining_display", status.remainingDisplay());
        values.put("kit_permission", kit.getPermission() != null && !kit.getPermission().isBlank() ? kit.getPermission() : "Nenhuma");
        values.put("kit_max_uses", kit.getMaxUses() < 0 ? "Ilimitado" : String.valueOf(kit.getMaxUses()));
        values.put("kit_usage_count", String.valueOf(usageCount));
        values.put("kit_usage_display", buildUsageDisplay(usageCount, kit.getMaxUses()));
        values.put("kit_status", status.label());
        values.put("kit_status_color", status.color());
        values.put("kit_status_key", status.key());
        values.put("kit_status_reason", status.reason() != null ? status.reason() : "");
        values.put("kit_claimable", String.valueOf(status.claimable()));
        values.put("kit_can_claim", String.valueOf(status.claimable()));
        values.put("kit_icon", resolveIcon(kit));
        values.put("kit_cooldown_ms", String.valueOf(kit.getCooldownMillis()));
        values.put("kit_remaining_ms", player != null ? String.valueOf(KitManager.getInstance().getRemainingCooldownPublic(player.getUUID(), kit.getName())) : "0");
        return values;
    }

    static Map<String, Object> buildSummaryPlaceholders(ServerPlayer player) {
        Map<String, Object> values = new LinkedHashMap<>();
        int total = 0;
        int available = 0;
        int cooldown = 0;
        int locked = 0;
        int disabled = 0;
        int used = 0;

        for (Kit kit : getSortedKits()) {
            total++;
            KitStatus status = classify(player, kit);
            switch (status.key()) {
                case "ready" -> available++;
                case "cooldown" -> cooldown++;
                case "used" -> used++;
                case "disabled" -> disabled++;
                default -> locked++;
            }
        }

        values.put("kits_total", String.valueOf(total));
        values.put("kits_available", String.valueOf(available));
        values.put("kits_ready", String.valueOf(available));
        values.put("kits_claimable", String.valueOf(available));
        values.put("kits_cooldown", String.valueOf(cooldown));
        values.put("kits_locked", String.valueOf(locked));
        values.put("kits_disabled", String.valueOf(disabled));
        values.put("kits_used", String.valueOf(used));
        values.put("total", String.valueOf(total));
        values.put("available", String.valueOf(available));
        values.put("ready", String.valueOf(available));
        values.put("claimable", String.valueOf(available));
        values.put("cooldown", String.valueOf(cooldown));
        values.put("locked", String.valueOf(locked));
        values.put("disabled", String.valueOf(disabled));
        values.put("used", String.valueOf(used));
        return values;
    }

    static KitStatus classify(ServerPlayer player, Kit kit) {
        if (kit == null) {
            return new KitStatus("unknown", "<gray>Indisponível", "<gray>", "Indisponível", false, "Kit desconhecido");
        }

        if (!kit.isEnabled()) {
            return new KitStatus("disabled", "<dark_gray>Desativado", "<dark_gray>", "Indisponível", false, "Kit desativado");
        }

        if (player == null) {
            return new KitStatus("locked", "<gray>Indisponível", "<gray>", "N/A", false, "Jogador indisponível");
        }

        KitManager.KitUsageResult canUse = KitManager.getInstance().canUseKit(player, kit.getName());
        if (canUse.isAllowed()) {
            return new KitStatus("ready", "<green>Pronto", "<green>", "Disponível", true, "");
        }

        String message = canUse.getMessage() != null ? canUse.getMessage() : "";
        String lower = message.toLowerCase(Locale.ROOT);
        long remaining = KitManager.getInstance().getRemainingCooldownPublic(player.getUUID(), kit.getName());

        if (lower.contains("cooldown")) {
            return new KitStatus("cooldown", "<yellow>Em espera", "<yellow>", remaining > 0 ? formatDuration(remaining) : "Em espera", false, message);
        }
        if (lower.contains("maximum uses")) {
            return new KitStatus("used", "<gray>Esgotado", "<gray>", "Esgotado", false, message);
        }
        if (lower.contains("permission")) {
            return new KitStatus("locked", "<red>Bloqueado", "<red>", "Sem permissão", false, message);
        }
        if (lower.contains("maximum number of kits on cooldown")) {
            return new KitStatus("locked", "<red>Bloqueado", "<red>", "Limite atingido", false, message);
        }
        if (lower.contains("disabled")) {
            return new KitStatus("disabled", "<dark_gray>Desativado", "<dark_gray>", "Indisponível", false, message);
        }

        return new KitStatus("locked", "<red>Bloqueado", "<red>", "Indisponível", false, message);
    }

    static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %dm", hours, minutes % 60);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %ds", minutes, seconds % 60);
        }
        return String.format(Locale.ROOT, "%ds", seconds);
    }

    private static String resolveIcon(Kit kit) {
        for (ItemStack item : kit.getItems()) {
            if (item != null && !item.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
                if (id != null) {
                    return id.toString();
                }
            }
        }
        return "minecraft:book";
    }

    private static String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String buildUsageDisplay(int usageCount, int maxUses) {
        if (maxUses < 0) {
            return "Ilimitado";
        }
        return usageCount + "/" + maxUses;
    }

    record KitStatus(String key, String label, String color, String remainingDisplay, boolean claimable, String reason) {}
}
