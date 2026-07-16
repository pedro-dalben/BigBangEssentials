package com.pedrodalben.bigbangessentials.tablist;

import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TabPlayerStateTest {

    @BeforeAll
    static void beforeAll() {
        try { Bootstrap.bootStrap(); } catch (Throwable ignored) {}
    }

    @Test
    void constructor_SetsDefaults() {
        UUID uuid = UUID.randomUUID();
        TabPlayerState state = new TabPlayerState(uuid, "TestPlayer");

        assertEquals(uuid, state.getUuid());
        assertEquals("TestPlayer", state.getRealName());
        assertEquals("", state.getNick());
        assertEquals("", state.getPrefix());
        assertEquals("", state.getSuffix());
        assertEquals("default", state.getPrimaryGroup());
        assertEquals("", state.getTag());
        assertFalse(state.isAfk());
        assertFalse(state.isVanished());
        assertEquals("", state.getWorld());
        assertEquals(0, state.getPing());
    }

    @Test
    void constructor_MarksFullDirty() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        assertTrue(state.hasDirtyFlag(TabDirtyFlag.FULL));
    }

    @Test
    void setNick_StoresValue() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.setNick("&aNick");
        assertEquals("&aNick", state.getNick());
    }

    @Test
    void setNick_NullBecomesEmpty() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.setNick(null);
        assertEquals("", state.getNick());
    }

    @Test
    void setPrefix_Suffix_Tag_Group() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.setPrefix("[Admin] ");
        state.setSuffix(" [GM]");
        state.setTag("&c[TAG]");
        state.setPrimaryGroup("admin");

        assertEquals("[Admin] ", state.getPrefix());
        assertEquals(" [GM]", state.getSuffix());
        assertEquals("&c[TAG]", state.getTag());
        assertEquals("admin", state.getPrimaryGroup());
    }

    @Test
    void markDirty_SetsFlag() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.getAndClearDirtyFlags(); // clear initial FULL

        state.markDirty(TabDirtyFlag.HEADER_FOOTER);
        assertTrue(state.hasDirtyFlag(TabDirtyFlag.HEADER_FOOTER));
        assertFalse(state.hasDirtyFlag(TabDirtyFlag.PLAYER_LIST_NAME));
    }

    @Test
    void markDirty_FullImpliesAll() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.getAndClearDirtyFlags(); // clear initial FULL

        state.markDirty(TabDirtyFlag.FULL);
        assertTrue(state.hasDirtyFlag(TabDirtyFlag.PLAYER_LIST_NAME));
        assertTrue(state.hasDirtyFlag(TabDirtyFlag.NAME_TAG));
        assertTrue(state.hasDirtyFlag(TabDirtyFlag.HEADER_FOOTER));
    }

    @Test
    void getAndClearDirtyFlags_ReturnsAndClears() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.getAndClearDirtyFlags(); // clear initial FULL

        state.markDirty(TabDirtyFlag.HEADER_FOOTER);
        state.markDirty(TabDirtyFlag.LATENCY);

        EnumSet<TabDirtyFlag> flags = state.getAndClearDirtyFlags();
        assertTrue(flags.contains(TabDirtyFlag.HEADER_FOOTER));
        assertTrue(flags.contains(TabDirtyFlag.LATENCY));
        assertFalse(state.hasDirtyFlag(TabDirtyFlag.HEADER_FOOTER));
        assertFalse(state.hasDirtyFlag(TabDirtyFlag.LATENCY));
    }

    @Test
    void snapshotDirtyFlags_DoesNotClear() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.getAndClearDirtyFlags();

        state.markDirty(TabDirtyFlag.NAME_TAG);
        EnumSet<TabDirtyFlag> snapshot = state.snapshotDirtyFlags();
        assertTrue(snapshot.contains(TabDirtyFlag.NAME_TAG));
        assertTrue(state.hasDirtyFlag(TabDirtyFlag.NAME_TAG));
    }

    @Test
    void clearDirtyFlags_ClearsAll() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.getAndClearDirtyFlags();

        state.markDirty(TabDirtyFlag.NAME_TAG);
        state.markDirty(TabDirtyFlag.SORT_ORDER);
        state.clearDirtyFlags();
        assertFalse(state.hasDirtyFlag(TabDirtyFlag.NAME_TAG));
        assertFalse(state.hasDirtyFlag(TabDirtyFlag.SORT_ORDER));
    }

    @Test
    void getDisplayNameSource_ReturnsNickWhenAvailable() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "RealName");
        state.setNick("NickName");
        assertEquals("NickName", state.getDisplayNameSource(true));
        assertEquals("RealName", state.getDisplayNameSource(false));
    }

    @Test
    void getDisplayNameSource_ReturnsRealWhenNoNick() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "RealName");
        assertEquals("RealName", state.getDisplayNameSource(true));
    }

    @Test
    void setAfk_StoresValue() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        assertFalse(state.isAfk());
        state.setAfk(true);
        assertTrue(state.isAfk());
        state.setAfk(false);
        assertFalse(state.isAfk());
    }

    @Test
    void setVanished_StoresValue() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        assertFalse(state.isVanished());
        state.setVanished(true);
        assertTrue(state.isVanished());
    }

    @Test
    void setWorld_StoresValue() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.setWorld("minecraft:overworld");
        assertEquals("minecraft:overworld", state.getWorld());
    }

    @Test
    void setPing_StoresValue() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        state.setPing(42);
        assertEquals(42, state.getPing());
    }

    @Test
    void cachedPlaceholders_StoreAndRetrieve() {
        TabPlayerState state = new TabPlayerState(UUID.randomUUID(), "p");
        assertNull(state.getCachedPlaceholder("some_key"));
        state.setCachedPlaceholder("some_key", "value");
        assertEquals("value", state.getCachedPlaceholder("some_key"));
        state.setCachedPlaceholder("some_key", null);
        assertNull(state.getCachedPlaceholder("some_key"));
    }
}
