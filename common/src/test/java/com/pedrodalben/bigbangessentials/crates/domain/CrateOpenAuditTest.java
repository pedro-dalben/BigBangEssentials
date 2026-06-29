package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrateOpenAuditTest {

    @Test
    void constructor_SetsAllParameters() {
        UUID id = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        java.util.List<String> rewardIds = java.util.List.of("reward_1", "reward_2");
        java.util.List<String> rewardNames = java.util.List.of("Diamond", "Gold");

        CrateOpenAudit audit = new CrateOpenAudit(
            id, playerId, "crate_vip", "key_vip",
            GrantSource.STORE, rewardIds, rewardNames,
            CrateOpenAudit.OpenStatus.COMPLETED, 50.0, "idem-123", "server-1"
        );

        assertEquals(id, audit.getId());
        assertEquals(playerId, audit.getPlayerId());
        assertEquals("crate_vip", audit.getCrateId());
        assertEquals("key_vip", audit.getKeyId());
        assertEquals(GrantSource.STORE, audit.getSource());
        assertEquals(rewardIds, audit.getRewardIds());
        assertEquals(rewardNames, audit.getRewardNames());
        assertEquals(CrateOpenAudit.OpenStatus.COMPLETED, audit.getStatus());
        assertEquals(50.0, audit.getCostConsumed(), 0.001);
        assertEquals("idem-123", audit.getIdempotencyKey());
        assertEquals("server-1", audit.getServerId());
        assertNotNull(audit.getTimestamp());
        assertNull(audit.getErrorDetail());
    }

    @Test
    void constructor_NullId_GeneratesNewId() {
        CrateOpenAudit audit = new CrateOpenAudit(
            null, UUID.randomUUID(), "crate_1", "key_1",
            GrantSource.ADMIN_COMMAND, java.util.List.of(), java.util.List.of(),
            CrateOpenAudit.OpenStatus.PENDING, 0.0, "", "server-1"
        );
        assertNotNull(audit.getId());
    }

    @Test
    void constructor_EmptyRewardLists() {
        CrateOpenAudit audit = new CrateOpenAudit(
            UUID.randomUUID(), UUID.randomUUID(), "crate_1", "key_1",
            GrantSource.OPENING, java.util.List.of(), java.util.List.of(),
            CrateOpenAudit.OpenStatus.PENDING, 0.0, "idem-1", "s1"
        );
        assertTrue(audit.getRewardIds().isEmpty());
        assertTrue(audit.getRewardNames().isEmpty());
    }

    @Test
    void constructor_GeneratesTimestamp() {
        CrateOpenAudit audit1 = new CrateOpenAudit(
            UUID.randomUUID(), UUID.randomUUID(), "c", "k",
            GrantSource.SYSTEM, java.util.List.of(), java.util.List.of(),
            CrateOpenAudit.OpenStatus.PENDING, 0.0, "", "s"
        );
        assertNotNull(audit1.getTimestamp());
    }

    @Test
    void openStatus_AllValuesPresent() {
        assertEquals(5, CrateOpenAudit.OpenStatus.values().length);
        assertNotNull(CrateOpenAudit.OpenStatus.PENDING);
        assertNotNull(CrateOpenAudit.OpenStatus.COMPLETED);
        assertNotNull(CrateOpenAudit.OpenStatus.FAILED);
        assertNotNull(CrateOpenAudit.OpenStatus.ROLLED_BACK);
        assertNotNull(CrateOpenAudit.OpenStatus.CANCELLED);
    }

    @Test
    void setErrorDetail_UpdatesField() {
        CrateOpenAudit audit = new CrateOpenAudit(
            UUID.randomUUID(), UUID.randomUUID(), "c", "k",
            GrantSource.ADMIN_COMMAND, java.util.List.of(), java.util.List.of(),
            CrateOpenAudit.OpenStatus.FAILED, 0.0, "", "s"
        );
        assertNull(audit.getErrorDetail());

        audit.setErrorDetail("Insufficient funds");
        assertEquals("Insufficient funds", audit.getErrorDetail());
    }

    @Test
    void toJson_Roundtrip() {
        UUID id = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        java.util.List<String> rewardIds = java.util.List.of("reward_legendary");
        java.util.List<String> rewardNames = java.util.List.of("Legendary Sword");

        CrateOpenAudit original = new CrateOpenAudit(
            id, playerId, "crate_epic", "key_epic",
            GrantSource.MILESTONE, rewardIds, rewardNames,
            CrateOpenAudit.OpenStatus.COMPLETED, 0.0, "idem-m1", "server-2"
        );
        original.setErrorDetail("No error");

        JsonObject json = original.toJson();
        CrateOpenAudit restored = CrateOpenAudit.fromJson(json);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getPlayerId(), restored.getPlayerId());
        assertEquals(original.getCrateId(), restored.getCrateId());
        assertEquals(original.getKeyId(), restored.getKeyId());
        assertEquals(original.getSource(), restored.getSource());
        assertEquals(original.getRewardIds(), restored.getRewardIds());
        assertEquals(original.getRewardNames(), restored.getRewardNames());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getCostConsumed(), restored.getCostConsumed(), 0.001);
        assertEquals(original.getIdempotencyKey(), restored.getIdempotencyKey());
        assertEquals(original.getServerId(), restored.getServerId());
        assertEquals(original.getErrorDetail(), restored.getErrorDetail());
    }

    @Test
    void fromJson_MinimalData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", UUID.randomUUID().toString());
        json.addProperty("playerId", UUID.randomUUID().toString());
        json.addProperty("crateId", "crate_test");
        json.addProperty("keyId", "key_test");
        json.addProperty("source", "EVENT");
        json.addProperty("status", "FAILED");
        json.addProperty("costConsumed", 100.0);
        json.addProperty("timestamp", java.time.Instant.now().toString());
        json.addProperty("idempotencyKey", "");
        json.addProperty("serverId", "s1");

        CrateOpenAudit audit = CrateOpenAudit.fromJson(json);
        assertEquals("crate_test", audit.getCrateId());
        assertEquals(GrantSource.EVENT, audit.getSource());
        assertEquals(CrateOpenAudit.OpenStatus.FAILED, audit.getStatus());
        assertEquals(100.0, audit.getCostConsumed(), 0.001);
        assertTrue(audit.getRewardIds().isEmpty());
        assertNull(audit.getErrorDetail());
    }

    @Test
    void getRewardIds_ReturnsCopy() {
        java.util.List<String> ids = new java.util.ArrayList<>(java.util.List.of("r1"));
        CrateOpenAudit audit = new CrateOpenAudit(
            UUID.randomUUID(), UUID.randomUUID(), "c", "k",
            GrantSource.ADMIN_COMMAND, ids, java.util.List.of(),
            CrateOpenAudit.OpenStatus.PENDING, 0.0, "", "s"
        );
        java.util.List<String> returned = audit.getRewardIds();
        returned.add("r2");
        assertEquals(1, audit.getRewardIds().size());
    }

    @Test
    void transitionTo_PendingToCompleted_Valid() {
        CrateOpenAudit audit = createPendingAudit();
        audit.transitionTo(CrateOpenAudit.OpenStatus.COMPLETED);
        assertEquals(CrateOpenAudit.OpenStatus.COMPLETED, audit.getStatus());
    }

    @Test
    void transitionTo_PendingToFailed_Valid() {
        CrateOpenAudit audit = createPendingAudit();
        audit.transitionTo(CrateOpenAudit.OpenStatus.FAILED);
        assertEquals(CrateOpenAudit.OpenStatus.FAILED, audit.getStatus());
    }

    @Test
    void transitionTo_PendingToRolledBack_Valid() {
        CrateOpenAudit audit = createPendingAudit();
        audit.transitionTo(CrateOpenAudit.OpenStatus.ROLLED_BACK);
        assertEquals(CrateOpenAudit.OpenStatus.ROLLED_BACK, audit.getStatus());
    }

    @Test
    void transitionTo_PendingToCancelled_Valid() {
        CrateOpenAudit audit = createPendingAudit();
        audit.transitionTo(CrateOpenAudit.OpenStatus.CANCELLED);
        assertEquals(CrateOpenAudit.OpenStatus.CANCELLED, audit.getStatus());
    }

    @Test
    void transitionTo_SameStatus_NoOp() {
        CrateOpenAudit audit = createPendingAudit();
        audit.transitionTo(CrateOpenAudit.OpenStatus.PENDING);
        assertEquals(CrateOpenAudit.OpenStatus.PENDING, audit.getStatus());
    }

    @Test
    void transitionTo_NullStatus_Throws() {
        CrateOpenAudit audit = createPendingAudit();
        assertThrows(IllegalArgumentException.class,
            () -> audit.transitionTo(null));
    }

    @Test
    void transitionTo_TerminalStatus_RejectsAnyTransition() {
        for (CrateOpenAudit.OpenStatus terminal : List.of(
            CrateOpenAudit.OpenStatus.COMPLETED,
            CrateOpenAudit.OpenStatus.FAILED,
            CrateOpenAudit.OpenStatus.ROLLED_BACK,
            CrateOpenAudit.OpenStatus.CANCELLED)) {

            for (CrateOpenAudit.OpenStatus target : CrateOpenAudit.OpenStatus.values()) {
                if (target == terminal) continue;
                CrateOpenAudit audit = createAuditWithStatus(terminal);
                assertThrows(IllegalStateException.class,
                    () -> audit.transitionTo(target),
                    "Should reject " + terminal + " -> " + target);
            }
        }
    }

    @Test
    void terminalStates_IsTerminal_True() {
        for (CrateOpenAudit.OpenStatus terminal : List.of(
            CrateOpenAudit.OpenStatus.COMPLETED,
            CrateOpenAudit.OpenStatus.FAILED,
            CrateOpenAudit.OpenStatus.ROLLED_BACK,
            CrateOpenAudit.OpenStatus.CANCELLED)) {
            assertTrue(terminal.isTerminal(), terminal + " should be terminal");
        }
    }

    @Test
    void pending_IsTerminal_False() {
        assertFalse(CrateOpenAudit.OpenStatus.PENDING.isTerminal());
    }

    private CrateOpenAudit createPendingAudit() {
        return new CrateOpenAudit(
            UUID.randomUUID(), UUID.randomUUID(), "crate", "key",
            GrantSource.OPENING, java.util.List.of(), java.util.List.of(),
            CrateOpenAudit.OpenStatus.PENDING, 0.0, "idem", "server"
        );
    }

    private CrateOpenAudit createAuditWithStatus(CrateOpenAudit.OpenStatus status) {
        return new CrateOpenAudit(
            UUID.randomUUID(), UUID.randomUUID(), "crate", "key",
            GrantSource.OPENING, java.util.List.of(), java.util.List.of(),
            status, 0.0, "idem", "server"
        );
    }
}
