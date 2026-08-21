package com.pedrodalben.bigbangessentials.economy.commands;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.economy.migration.EconomyJsonMigrationService;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Administrative surface for economy operations. */
public final class EconomyAdminCommands {
    private EconomyAdminCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("economy")
            .requires(s -> s.hasPermission(3))
            .then(Commands.literal("status").executes(EconomyAdminCommands::status))
            .then(Commands.literal("migrate-json")
                .then(Commands.literal("--dry-run").executes(EconomyAdminCommands::dryRun))
                .then(Commands.literal("--execute").then(Commands.literal("--confirm").executes(EconomyAdminCommands::execute))))
            .then(Commands.literal("export").executes(EconomyAdminCommands::export))
            .then(Commands.literal("export-json").executes(EconomyAdminCommands::export))
            .then(Commands.literal("reconcile").executes(EconomyAdminCommands::reconcileAll));
    }

    private static int dryRun(CommandContext<CommandSourceStack> ctx) {
        try {
            var r = new EconomyJsonMigrationService().dryRun();
            ctx.getSource().sendSuccess(() -> report(r), false); return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal("Economy migration validation failed: " + e.getMessage())); return 0; }
    }
    private static int execute(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("Economy JSON import started; the database remains the only active backend."), false);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { var r = new EconomyJsonMigrationService().execute(); ctx.getSource().sendSuccess(() -> report(r), true); }
            catch (Exception e) { ctx.getSource().sendFailure(Component.literal("Economy migration failed: " + e.getMessage())); }
        });
        return 1;
    }
    private static int status(CommandContext<CommandSourceStack> ctx) {
        var db = DatabaseManager.getInstance();
        ctx.getSource().sendSuccess(() -> Component.literal("Economy backend: " + com.pedrodalben.bigbangessentials.config.ConfigManager.getEconomyBackend() + " | database=" + db.getState()), false);
        return 1;
    }

    /** Export all accounts to a timestamped JSON file. */
    private static int export(CommandContext<CommandSourceStack> ctx) {
        var db = DatabaseManager.getInstance();
        if (!db.isReady()) { ctx.getSource().sendFailure(Component.literal("Database unavailable")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Exporting economy accounts..."), false);
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject export = new JsonObject();
                var totalMinor = new long[]{0};
                db.getExecutor().queryList("export.accounts", "SELECT player_uuid,balance_minor,version FROM bbe_economy_accounts ORDER BY player_uuid", null, (ResultSet r) -> {
                    String uuid = r.getString("player_uuid");
                    long minor = r.getLong("balance_minor");
                    BigDecimal decimal = BigDecimal.valueOf(minor, com.pedrodalben.bigbangessentials.config.ConfigManager.getEconomyCurrencyScale());
                    export.addProperty(uuid, decimal.toPlainString());
                    totalMinor[0] += minor;
                    return null;
                }).join();
                String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(export);
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                String checksum = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                Path exportPath = ResourceUtil.getDataPath("economy-export-" + java.time.LocalDate.now() + "-" + checksum.substring(0, 8) + ".json");
                Files.write(exportPath, bytes);
                ctx.getSource().sendSuccess(() -> Component.literal("Export: " + exportPath.getFileName() + " | accounts=" + export.size() + " | total_minor=" + totalMinor[0] + " | sha256=" + checksum), true);
            } catch (Exception e) {
                ctx.getSource().sendFailure(Component.literal("Export failed: " + e.getMessage()));
            }
        });
        return 1;
    }

    /** Reconcile: find PENDING operations, accounts without history, negative balances. Read-only. */
    private static int reconcileAll(CommandContext<CommandSourceStack> ctx) {
        var db = DatabaseManager.getInstance();
        if (!db.isReady()) { ctx.getSource().sendFailure(Component.literal("Database unavailable")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("§6Economy reconcile (read-only):"), false);
        db.getExecutor().queryOne("reconcile.pending", "SELECT COUNT(*) FROM bbe_economy_operations WHERE status NOT IN ('COMPLETED','REJECTED')", null, r -> r.getLong(1))
            .whenComplete((v, t) -> ctx.getSource().sendSuccess(() -> Component.literal(" §ePending operations: §f" + (t == null ? v : "error")), false));
        db.getExecutor().queryOne("reconcile.orphan", "SELECT COUNT(*) FROM bbe_economy_operations o LEFT JOIN bbe_economy_accounts a ON o.player_uuid=a.player_uuid WHERE a.player_uuid IS NULL", null, r -> r.getLong(1))
            .whenComplete((v, t) -> ctx.getSource().sendSuccess(() -> Component.literal(" §eOrphan operations: §f" + (t == null ? v : "error")), false));
        db.getExecutor().queryOne("reconcile.negative", "SELECT COUNT(*) FROM bbe_economy_accounts WHERE balance_minor < 0", null, r -> r.getLong(1))
            .whenComplete((v, t) -> ctx.getSource().sendSuccess(() -> Component.literal(" §eNegative balances: §f" + (t == null ? v : "error")), false));
        db.getExecutor().queryOne("reconcile.total", "SELECT COUNT(*) FROM bbe_economy_accounts", null, r -> r.getLong(1))
            .whenComplete((v, t) -> ctx.getSource().sendSuccess(() -> Component.literal(" §eTotal accounts: §f" + (t == null ? v : "error")), false));
        db.getExecutor().queryOne("reconcile.sum", "SELECT COALESCE(SUM(balance_minor),0) FROM bbe_economy_accounts", null, r -> r.getLong(1))
            .whenComplete((v, t) -> ctx.getSource().sendSuccess(() -> Component.literal(" §eTotal balance minor: §f" + (t == null ? v : "error")), false));
        db.getExecutor().queryOne("reconcile.idempotency", "SELECT COUNT(*) FROM (SELECT idempotency_key,COUNT(*) FROM bbe_economy_operations GROUP BY idempotency_key HAVING COUNT(*) > 1) t", null, r -> r.getLong(1))
            .whenComplete((v, t) -> ctx.getSource().sendSuccess(() -> Component.literal(" §eDuplicate idempotency keys: §f" + (t == null ? v : "error")), false));
        return 1;
    }

    private static Component report(EconomyJsonMigrationService.MigrationReport r) {
        return Component.literal("Economy JSON: found=" + r.found() + ", valid=" + r.valid() + ", rejected=" + r.rejected() + ", totalMinor=" + r.totalBalanceMinor() + ", checksum=" + r.checksum() + ", status=" + r.status());
    }
}
