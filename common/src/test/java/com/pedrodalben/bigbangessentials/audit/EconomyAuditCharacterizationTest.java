package com.pedrodalben.bigbangessentials.audit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the current audit evidence; it is not a production behavior change. */
class EconomyAuditCharacterizationTest {
    @Test
    void adminShopDoesNotDropValidClicksWithAFixedDebounce() throws IOException {
        String source = source("com/pedrodalben/bigbangessentials/adminshop/AdminShopTransactionService.java");

        // Per-product serialization moved from productLocks (synchronized) to
        // productQueues (async FIFO chain); clicks still queue instead of dropping.
        assertTrue(source.contains("productQueues"));
        assertTrue(!source.contains("Aguarde a conclusão da transação."));
    }

    @Test
    void auditTargetsExposeTheirCurrentMoneyBoundaries() throws IOException {
        String sell = source("com/pedrodalben/bigbangessentials/economy/worth/SellCommand.java");
        String jobs = source("com/pedrodalben/bigbangessentials/jobs/pipeline/JobRewardBatcher.java");
        String crates = source("com/pedrodalben/bigbangessentials/crates/service/CrateOpeningService.java");
        String rankup = source("com/pedrodalben/bigbangessentials/rankup/service/RankupPromotionService.java");
        String pay = source("com/pedrodalben/bigbangessentials/api/EconomyAPI.java");

        assertTrue(sell.indexOf("EconomyManager.getInstance().credit(player.getUUID(), earned")
                < sell.indexOf("int removed = removeFromInventory(player, template, toSell)"));
        // Jobs rewards are batched: per-action idempotent keys moved to the batcher
        // (one aggregated credit per player/job per flush window).
        assertTrue(jobs.contains("jobs:reward:batch:"));
        assertTrue(crates.contains("crate:refund:"));
        assertTrue(rankup.contains("rankup:refund:"));
        assertTrue(pay.contains("return manager.transfer(sender, receiver, amount, fee, requestKey)"));
    }

    private static String source(String relative) throws IOException {
        Path module = Path.of("src/main/java", relative);
        if (Files.exists(module)) return Files.readString(module);
        return Files.readString(Path.of("common/src/main/java", relative));
    }
}
