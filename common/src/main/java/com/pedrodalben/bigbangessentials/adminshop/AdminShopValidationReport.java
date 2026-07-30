package com.pedrodalben.bigbangessentials.adminshop;

import java.util.ArrayList;
import java.util.List;

public record AdminShopValidationReport(
        List<Finding> findings,
        int productCount,
        int storeCount,
        int categoryCount
) {
    public record Finding(Severity severity, String type, String id, String message) {}
    public enum Severity { ERROR, WARNING, INFO }

    public boolean hasErrors() {
        return findings.stream().anyMatch(f -> f.severity == Severity.ERROR);
    }

    public static AdminShopValidationReport validate(AdminShopConfig config) {
        List<Finding> findings = new ArrayList<>();
        int productCount = 0;
        int storeCount = config.stores != null ? config.stores.size() : 0;
        int categoryCount = config.categories != null ? config.categories.size() : 0;

        if (config.stores == null || config.stores.isEmpty()) {
            findings.add(new Finding(Severity.ERROR, "stores", "-", "No stores defined"));
        } else {
            for (var storeEntry : config.stores.entrySet()) {
                AdminShopConfig.Store store = storeEntry.getValue();
                if (store == null) continue;
                if (store.currency == null || store.currency.isBlank()) {
                    findings.add(new Finding(Severity.ERROR, "store", storeEntry.getKey(), "Missing currency"));
                }
                if (store.categories.isEmpty()) {
                    findings.add(new Finding(Severity.WARNING, "store", storeEntry.getKey(), "No categories assigned"));
                }
                for (String catId : store.categories) {
                    if (!config.categories.containsKey(catId)) {
                        findings.add(new Finding(Severity.ERROR, "store-category", storeEntry.getKey(),
                                "References non-existent category: " + catId));
                    }
                }
                if (store.products != null) {
                    productCount += store.products.size();
                    for (AdminShopConfig.Product product : store.products) {
                        if (product == null || product.id == null) continue;
                        if (product.category == null) {
                            findings.add(new Finding(Severity.WARNING, "product", product.id, "Missing category"));
                        } else if (!config.categories.containsKey(product.category)
                                && !store.categories.contains(product.category)) {
                            findings.add(new Finding(Severity.ERROR, "product", product.id,
                                    "References non-existent category: " + product.category));
                        }
                        if (product.itemId == null && product.item == null && !product.isCommand()) {
                            findings.add(new Finding(Severity.ERROR, "product", product.id,
                                    "No itemId, item, or command defined"));
                        }
                        if (product.isCommand() && product.command != null
                                && !product.command.contains("{transaction}")) {
                            findings.add(new Finding(Severity.WARNING, "product", product.id,
                                    "Command product missing {transaction} placeholder"));
                        }
                        if (product.buyPrice == null && product.buyEnabled) {
                            findings.add(new Finding(Severity.WARNING, "product", product.id,
                                    "Buy enabled but no buy price set"));
                        }
                        if (product.quantity < 1) {
                            findings.add(new Finding(Severity.ERROR, "product", product.id,
                                    "Invalid quantity: " + product.quantity));
                        }
                        if (product.item != null && product.itemId == null && !product.isCommand()) {
                            if (!product.item.has("item")
                                    || product.item.get("item").getAsString().isBlank()) {
                                findings.add(new Finding(Severity.WARNING, "product", product.id,
                                        "Serialized item has no 'item' field — preview will be stone"));
                            }
                        }
                    }
                }
            }
        }

        if (config.categories != null) {
            for (var catEntry : config.categories.entrySet()) {
                String catId = catEntry.getKey();
                AdminShopConfig.Category cat = catEntry.getValue();
                if (cat.title == null || cat.title.isBlank()) {
                    findings.add(new Finding(Severity.WARNING, "category", catId, "Missing title"));
                }
                String storeId = config.storeIdForCategory(catId);
                if (storeId == null) {
                    findings.add(new Finding(Severity.ERROR, "category", catId, "Orphan category"));
                }
            }
        }

        return new AdminShopValidationReport(findings, productCount, storeCount, categoryCount);
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6AdminShop Validation Report§r\n");
        sb.append("§7Stores: ").append(storeCount).append(" | Categories: ").append(categoryCount)
                .append(" | Products: ").append(productCount).append("§r\n");
        long errors = findings.stream().filter(f -> f.severity == Severity.ERROR).count();
        long warnings = findings.stream().filter(f -> f.severity == Severity.WARNING).count();
        sb.append("§cErrors: ").append(errors).append(" §eWarnings: ").append(warnings).append("§r\n");
        for (Finding f : findings) {
            String color = f.severity == Severity.ERROR ? "§c" : f.severity == Severity.WARNING ? "§e" : "§7";
            sb.append(color).append("[").append(f.severity).append("] §7").append(f.type)
                    .append(":").append(f.id).append(" §f").append(f.message).append("§r\n");
        }
        if (findings.isEmpty()) sb.append("§aNo issues found.§r\n");
        return sb.toString();
    }
}
