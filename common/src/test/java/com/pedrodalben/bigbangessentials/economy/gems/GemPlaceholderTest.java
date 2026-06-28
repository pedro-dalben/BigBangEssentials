package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.api.DefaultPlaceholderExpansion;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Isolated
class GemPlaceholderTest {

    private DefaultPlaceholderExpansion expansion;

    @BeforeEach
    void setUp() {
        cleanData();
        GemsManager.getInstance().reload();
        expansion = new DefaultPlaceholderExpansion();
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
    void testStaticGemsPlaceholdersWithNullPlayer() {
        String currencyName = expansion.onPlaceholderRequest(null, "gems_currency_name", null);
        assertEquals("Gemas", currencyName);

        String currencySymbol = expansion.onPlaceholderRequest(null, "gems_currency_symbol", null);
        assertEquals("✦", currencySymbol);
    }

    @Test
    void testDynamicGemsPlaceholdersWithNullPlayerReturnsNull() {
        assertNull(expansion.onPlaceholderRequest(null, "gems", null));
        assertNull(expansion.onPlaceholderRequest(null, "gems_formatted", null));
        assertNull(expansion.onPlaceholderRequest(null, "gems_available", null));
        assertNull(expansion.onPlaceholderRequest(null, "gems_held", null));
    }
}
