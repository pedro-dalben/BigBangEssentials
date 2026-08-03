package com.pedrodalben.bigbangessentials.api.permissions;

import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionAPIExactPermissionTest {

    @AfterEach
    void tearDown() {
        PermissionAPI.setExternalAdapter(null);
    }

    @Test
    void exactPermissionDoesNotInheritFromParentNode() {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);

        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenReturn(false);
        when(adapter.hasPermission(any(UUID.class), anyString())).thenReturn(true);
        PermissionAPI.setExternalAdapter(adapter);

        assertFalse(PermissionAPI.hasAnyExactPermission(
            playerId,
            "bigbangessentials.teleport.spawn.set",
            "bigbangessentials.spawn.set"
        ));
    }

    @Test
    void exactPermissionMatchesWhenExplicitlyGranted() {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);

        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenAnswer(invocation ->
            "bigbangessentials.teleport.spawn.set".equals(invocation.getArgument(1, String.class)));
        PermissionAPI.setExternalAdapter(adapter);

        assertTrue(PermissionAPI.hasAnyExactPermission(
            playerId,
            "bigbangessentials.teleport.spawn.set",
            "bigbangessentials.spawn.set"
        ));
    }

    @Test
    void targetPermissionAcceptsExplicitWildcardButNotPlainParent() {
        UUID playerId = UUID.randomUUID();
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenAnswer(invocation ->
            "bigbangessentials.ping.*".equals(invocation.getArgument(1, String.class)));
        PermissionAPI.setExternalAdapter(adapter);

        assertTrue(PermissionAPI.hasTargetPermission(playerId, "bigbangessentials.ping.others"));

        when(adapter.hasExactPermission(any(UUID.class), anyString())).thenAnswer(invocation ->
            "bigbangessentials.ping".equals(invocation.getArgument(1, String.class)));
        assertFalse(PermissionAPI.hasTargetPermission(playerId, "bigbangessentials.ping.others"));
    }
}
