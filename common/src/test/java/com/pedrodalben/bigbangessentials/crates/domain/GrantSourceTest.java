package com.pedrodalben.bigbangessentials.crates.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrantSourceTest {

    @Test
    void enum_HasAllExpectedValues() {
        GrantSource[] sources = GrantSource.values();
        assertEquals(14, sources.length);
    }

    @Test
    void enum_ContainsJob() {
        assertTrue(contains(GrantSource.JOB));
    }

    @Test
    void enum_ContainsContract() {
        assertTrue(contains(GrantSource.CONTRACT));
    }

    @Test
    void enum_ContainsRankup() {
        assertTrue(contains(GrantSource.RANKUP));
    }

    @Test
    void enum_ContainsAdminCommand() {
        assertTrue(contains(GrantSource.ADMIN_COMMAND));
    }

    @Test
    void enum_ContainsOpening() {
        assertTrue(contains(GrantSource.OPENING));
    }

    @Test
    void enum_ContainsStore() {
        assertTrue(contains(GrantSource.STORE));
    }

    @Test
    void enum_ContainsVip() {
        assertTrue(contains(GrantSource.VIP));
    }

    @Test
    void enum_ContainsEvent() {
        assertTrue(contains(GrantSource.EVENT));
    }

    @Test
    void enum_ContainsQuest() {
        assertTrue(contains(GrantSource.QUEST));
    }

    @Test
    void enum_ContainsTournament() {
        assertTrue(contains(GrantSource.TOURNAMENT));
    }

    @Test
    void enum_ContainsSystem() {
        assertTrue(contains(GrantSource.SYSTEM));
    }

    @Test
    void enum_ContainsMilestone() {
        assertTrue(contains(GrantSource.MILESTONE));
    }

    @Test
    void enum_ContainsMassOpen() {
        assertTrue(contains(GrantSource.MASS_OPEN));
    }

    @Test
    void enum_ContainsRollback() {
        assertTrue(contains(GrantSource.ROLLBACK));
    }

    @Test
    void valueOf_ValidNames() {
        assertEquals(GrantSource.ADMIN_COMMAND, GrantSource.valueOf("ADMIN_COMMAND"));
        assertEquals(GrantSource.OPENING, GrantSource.valueOf("OPENING"));
        assertEquals(GrantSource.MILESTONE, GrantSource.valueOf("MILESTONE"));
        assertEquals(GrantSource.MASS_OPEN, GrantSource.valueOf("MASS_OPEN"));
        assertEquals(GrantSource.ROLLBACK, GrantSource.valueOf("ROLLBACK"));
        assertEquals(GrantSource.JOB, GrantSource.valueOf("JOB"));
        assertEquals(GrantSource.CONTRACT, GrantSource.valueOf("CONTRACT"));
        assertEquals(GrantSource.RANKUP, GrantSource.valueOf("RANKUP"));
    }

    @Test
    void valueOf_InvalidName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> GrantSource.valueOf("INVALID"));
    }

    @Test
    void adminCommand_And_Opening_ArePresent() {
        assertNotNull(GrantSource.ADMIN_COMMAND);
        assertNotNull(GrantSource.OPENING);
    }

    private boolean contains(GrantSource source) {
        for (GrantSource s : GrantSource.values()) {
            if (s == source) return true;
        }
        return false;
    }
}
