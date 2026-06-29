package com.pedrodalben.bigbangessentials.crates.command.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CratePermissionsTest {

    @Test
    void allConstants_AreNotNullAndNotEmpty() {
        assertNotNull(CratePermissions.USE);
        assertFalse(CratePermissions.USE.isEmpty());

        assertNotNull(CratePermissions.PREVIEW);
        assertFalse(CratePermissions.PREVIEW.isEmpty());

        assertNotNull(CratePermissions.OPEN);
        assertFalse(CratePermissions.OPEN.isEmpty());

        assertNotNull(CratePermissions.BYPASS_COOLDOWN);
        assertFalse(CratePermissions.BYPASS_COOLDOWN.isEmpty());

        assertNotNull(CratePermissions.BYPASS_REQUIREMENTS);
        assertFalse(CratePermissions.BYPASS_REQUIREMENTS.isEmpty());

        assertNotNull(CratePermissions.ADMIN);
        assertFalse(CratePermissions.ADMIN.isEmpty());

        assertNotNull(CratePermissions.EDITOR);
        assertFalse(CratePermissions.EDITOR.isEmpty());

        assertNotNull(CratePermissions.MANAGE);
        assertFalse(CratePermissions.MANAGE.isEmpty());

        assertNotNull(CratePermissions.GIVE);
        assertFalse(CratePermissions.GIVE.isEmpty());

        assertNotNull(CratePermissions.GIVEALL);
        assertFalse(CratePermissions.GIVEALL.isEmpty());

        assertNotNull(CratePermissions.KEY_GIVE);
        assertFalse(CratePermissions.KEY_GIVE.isEmpty());

        assertNotNull(CratePermissions.KEY_TAKE);
        assertFalse(CratePermissions.KEY_TAKE.isEmpty());

        assertNotNull(CratePermissions.KEY_SET);
        assertFalse(CratePermissions.KEY_SET.isEmpty());

        assertNotNull(CratePermissions.KEY_INSPECT);
        assertFalse(CratePermissions.KEY_INSPECT.isEmpty());

        assertNotNull(CratePermissions.LOGS);
        assertFalse(CratePermissions.LOGS.isEmpty());

        assertNotNull(CratePermissions.RELOAD);
        assertFalse(CratePermissions.RELOAD.isEmpty());
    }

    @Test
    void allConstants_StartWithExpectedPrefix() {
        assertTrue(CratePermissions.USE.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.PREVIEW.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.OPEN.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.BYPASS_COOLDOWN.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.BYPASS_REQUIREMENTS.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.ADMIN.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.EDITOR.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.MANAGE.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.GIVE.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.GIVEALL.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.KEY_GIVE.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.KEY_TAKE.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.KEY_SET.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.KEY_INSPECT.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.LOGS.startsWith("bigbangessentials.crates"));
        assertTrue(CratePermissions.RELOAD.startsWith("bigbangessentials.crates"));
    }

    @Test
    void forCrateOpen_GeneratesCorrectPermission() {
        String permission = CratePermissions.OPEN + "vip_crate";
        assertEquals("bigbangessentials.crates.open.vip_crate", permission);
    }

    @Test
    void forCrateOpen_WithDifferentCrateId() {
        String permission1 = CratePermissions.OPEN + "daily";
        String permission2 = CratePermissions.OPEN + "event";
        assertEquals("bigbangessentials.crates.open.daily", permission1);
        assertEquals("bigbangessentials.crates.open.event", permission2);
        assertNotEquals(permission1, permission2);
    }

    @Test
    void usePermission_IsUsableInFormat() {
        String formatted = CratePermissions.USE + ".test";
        assertEquals("bigbangessentials.crates.use.test", formatted);
    }

    @Test
    void previewPermission_IsUsableInFormat() {
        String formatted = CratePermissions.PREVIEW + ".test";
        assertEquals("bigbangessentials.crates.preview.test", formatted);
    }

}
