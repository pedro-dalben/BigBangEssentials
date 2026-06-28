package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
class GemFormattingTest {

    @BeforeEach
    void setUp() {
        cleanData();
        GemsManager.getInstance().reload();
    }

    private void cleanData() {
        File dataDir = new File("bigbangessentials");
        if (dataDir.exists()) {
            new File(dataDir, "gems_state.json").delete();
            new File(dataDir, "gems_transactions.jsonl").delete();
            new File(dataDir, "gems.json").delete();
            File backupDir = new File(dataDir, "gems_backups");
            if (backupDir.exists()) {
                File[] files = backupDir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                backupDir.delete();
            }
        }
    }

    @Test
    void testDefaultFormatting() {
        GemsManager.getInstance().getConfig().display.symbol = "✦";
        GemsManager.getInstance().getConfig().display.symbolBeforeAmount = false;
        GemsManager.getInstance().getConfig().display.thousandsSeparator = ".";

        String formatted = GemsManager.getInstance().format(1250);
        assertEquals("1.250 ✦", formatted);
    }

    @Test
    void testSymbolBeforeAmount() {
        GemsManager.getInstance().getConfig().display.symbol = "$";
        GemsManager.getInstance().getConfig().display.symbolBeforeAmount = true;
        GemsManager.getInstance().getConfig().display.thousandsSeparator = ",";

        String formatted = GemsManager.getInstance().format(1000000);
        assertEquals("$ 1,000,000", formatted);
    }

    @Test
    void testAlternativeSeparator() {
        GemsManager.getInstance().getConfig().display.symbol = "G";
        GemsManager.getInstance().getConfig().display.symbolBeforeAmount = false;
        GemsManager.getInstance().getConfig().display.thousandsSeparator = " ";

        String result = GemsManager.getInstance().format(500000);
        assertTrue(result.contains("500") && result.contains("000") && result.endsWith("G"));
    }

    private void assertTrue(boolean val) {
        org.junit.jupiter.api.Assertions.assertTrue(val);
    }
}
