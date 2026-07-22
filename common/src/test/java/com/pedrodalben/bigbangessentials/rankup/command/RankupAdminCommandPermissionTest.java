package com.pedrodalben.bigbangessentials.rankup.command;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
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

class RankupAdminCommandPermissionTest {

    @AfterEach
    void tearDown() {
        PermissionAPI.setExternalAdapter(null);
    }

    @Test
    void regularRankupPermissionCannotOpenAdminCommand() {
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasExactPermission(any(UUID.class), anyString()))
                .thenAnswer(call -> "bigbangessentials.rankup.use".equals(call.getArgument(1)));
        PermissionAPI.setExternalAdapter(adapter);

        assertFalse(RankupAdminCommand.hasAdminPermission(UUID.randomUUID(), "bigbangessentials.rankup.admin"));
    }

    @Test
    void explicitAdminPermissionIsAccepted() {
        ExternalPermissionAdapter adapter = mock(ExternalPermissionAdapter.class);
        when(adapter.hasExactPermission(any(UUID.class), anyString()))
                .thenAnswer(call -> "bigbangessentials.rankup.admin.reload".equals(call.getArgument(1)));
        PermissionAPI.setExternalAdapter(adapter);

        assertTrue(RankupAdminCommand.hasAdminPermission(UUID.randomUUID(), "bigbangessentials.rankup.admin.reload"));
    }
}
