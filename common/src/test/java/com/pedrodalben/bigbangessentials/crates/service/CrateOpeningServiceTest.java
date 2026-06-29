package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrateOpeningServiceTest {

    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @Test
    void crateOpeningResult_Success() {
        CrateOpenAudit audit = new CrateOpenAudit(
            UUID.randomUUID(), UUID.randomUUID(), "crate_1", "key_1",
            GrantSource.ADMIN_COMMAND, java.util.List.of(), java.util.List.of(),
            CrateOpenAudit.OpenStatus.PENDING, 0.0, "", "server-1"
        );
        CrateOpeningService.CrateOpeningResult result =
            new CrateOpeningService.CrateOpeningResult(true, "Success", audit);
        assertTrue(result.success());
        assertEquals("Success", result.message());
        assertEquals(audit, result.audit());
    }

    @Test
    void crateOpeningResult_Failure() {
        CrateOpeningService.CrateOpeningResult result =
            new CrateOpeningService.CrateOpeningResult(false, "Failed", null);
        assertFalse(result.success());
        assertEquals("Failed", result.message());
        assertNull(result.audit());
    }

    @Test
    void openCrate_DisabledCrate_ReturnsError() {
        CrateOpeningService service = CrateOpeningService.getInstance();

        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setEnabled(false);

        CrateOpeningService.CrateOpeningResult result =
            service.openCrate(player, crate, GrantSource.ADMIN_COMMAND, null);
        assertFalse(result.success());
        assertEquals("Crate is disabled", result.message());
    }

    @Test
    void openCrate_NoValidRewards_ReturnsError() {
        CrateOpeningService service = CrateOpeningService.getInstance();

        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setEnabled(true);

        CrateOpeningService.CrateOpeningResult result =
            service.openCrate(player, crate, GrantSource.ADMIN_COMMAND, null);
        assertFalse(result.success());
        assertEquals("Crate has no valid rewards", result.message());
    }

    @Test
    void massOpen_ZeroTimes_ReturnsEmptyList() {
        CrateOpeningService service = CrateOpeningService.getInstance();

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setEnabled(false);

        var results = service.massOpen(player, crate, 0, GrantSource.ADMIN_COMMAND);
        assertTrue(results.isEmpty());
    }

    @Test
    void massOpen_SingleTimeWithDisabledCrate_ReturnsError() {
        CrateOpeningService service = CrateOpeningService.getInstance();

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setEnabled(false);

        var results = service.massOpen(player, crate, 1, GrantSource.ADMIN_COMMAND);
        assertEquals(1, results.size());
        assertFalse(results.get(0).success());
        assertEquals("Crate is disabled", results.get(0).message());
    }

    @Test
    void massOpen_StopsOnFirstFailure() {
        CrateOpeningService service = CrateOpeningService.getInstance();

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), "test", "Test");
        crate.setEnabled(false);

        var results = service.massOpen(player, crate, 5, GrantSource.ADMIN_COMMAND);
        assertEquals(1, results.size());
    }
}
