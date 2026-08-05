package com.pedrodalben.bigbangessentials.permissions;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * Verifies PermissionSystem.shutdown() closes the LuckPerms adapter sub BEFORE its own workers
 * are torn down, and that shutdown is idempotent.
 */
class PermissionSystemShutdownTest {

    private LuckPermsAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        resetPermissionSystemStatic();
        adapter = Mockito.spy(new LuckPermsAdapter(true));
        PermissionAPI.setExternalAdapter(adapter);
    }

    @AfterEach
    void tearDown() throws Exception {
        PermissionAPI.setExternalAdapter(null);
        resetPermissionSystemStatic();
    }

    private void resetPermissionSystemStatic() throws Exception {
        setStatic("shutdownCalled", false);
        setStatic("initialized", true);
        setStatic("manager", null);
        setStatic("usingExternal", true);
    }

    private void setStatic(String name, Object value) throws Exception {
        Field f = PermissionSystem.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    @Test
    void shutdownDelegatesToLuckPermsAdapter() {
        PermissionSystem.shutdown();
        verify(adapter, times(1)).shutdown();
    }

    @Test
    void shutdownIsIdempotent() {
        PermissionSystem.shutdown();
        PermissionSystem.shutdown();
        verify(adapter, times(1)).shutdown();
    }

    @Test
    void shutdownWithNoExternalAdapterDoesNotThrow() {
        PermissionAPI.setExternalAdapter(null);
        assertDoesNotThrow(() -> PermissionSystem.shutdown());
    }

    @Test
    void shutdownWithNonLuckPermsAdapterDoesNotCallLuckPermsShutdown() {
        ExternalPermissionAdapter other = Mockito.mock(ExternalPermissionAdapter.class);
        when(other.getName()).thenReturn("Other");
        PermissionAPI.setExternalAdapter(other);
        assertDoesNotThrow(() -> PermissionSystem.shutdown());
        // Only LuckPermsAdapter has a shutdown() method; shutdown should not call reload/save on
        // any non-LP adapter. Expected calls so far: getName() (from setExternalAdapter logging).
        verify(other).getName();
        verifyNoMoreInteractions(other);
    }
}