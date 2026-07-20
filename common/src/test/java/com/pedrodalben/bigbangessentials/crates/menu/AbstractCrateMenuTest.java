package com.pedrodalben.bigbangessentials.crates.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractCrateMenuTest {
    @Test
    void translatesHexColorsWithOrWithoutSectionPrefix() {
        String expected = "§x§F§F§D§7§0§0Reward";

        assertEquals(expected, AbstractCrateMenu.translateColorCodes("#FFD700Reward"));
        assertEquals(expected, AbstractCrateMenu.translateColorCodes("§#FFD700Reward"));
    }
}
