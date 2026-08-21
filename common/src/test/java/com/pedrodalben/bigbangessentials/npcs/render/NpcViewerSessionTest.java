package com.pedrodalben.bigbangessentials.npcs.render;

import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerSession.NpcViewState;
import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerSession.NpcViewerRenderState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NpcViewerSessionTest {

    private static NpcViewerSession session() {
        return new NpcViewerSession(UUID.randomUUID());
    }

    @Test
    void spawnTransitionsToVisible() {
        NpcViewerSession session = session();
        NpcViewState vs = session.getState("npc1");

        assertEquals(NpcViewerRenderState.NOT_VISIBLE, vs.renderState());
        assertTrue(vs.beginSpawn());
        assertEquals(NpcViewerRenderState.SPAWNING, vs.renderState());

        vs.markVisible();
        assertTrue(vs.isVisible());
        assertEquals(NpcViewerRenderState.VISIBLE, vs.renderState());
    }

    @Test
    void duplicateSpawnIsRejectedWhileSpawningOrVisible() {
        NpcViewerSession session = session();
        NpcViewState vs = session.getState("npc1");

        assertTrue(vs.beginSpawn());
        assertFalse(vs.beginSpawn(), "second spawn while SPAWNING must be rejected");

        vs.markVisible();
        assertFalse(vs.beginSpawn(), "spawn while VISIBLE must be rejected");
    }

    @Test
    void failedSpawnCanBeRetried() {
        NpcViewerSession session = session();
        NpcViewState vs = session.getState("npc1");

        vs.beginSpawn();
        vs.markSpawnFailed("boom");
        assertEquals(NpcViewerRenderState.FAILED, vs.renderState());

        assertTrue(vs.beginSpawn(), "FAILED state must allow retry");
    }

    @Test
    void despawnReturnsToNotVisible() {
        NpcViewerSession session = session();
        NpcViewState vs = session.getState("npc1");
        vs.beginSpawn();
        vs.markVisible();
        vs.onDespawn();
        assertEquals(NpcViewerRenderState.NOT_VISIBLE, vs.renderState());
        assertFalse(vs.isVisible());
        assertTrue(vs.beginSpawn(), "after despawn the NPC can spawn again");
    }

    @Test
    void abortedSpawnClearsPendingState() {
        NpcViewerSession session = session();
        NpcViewState vs = session.getState("npc1");
        vs.beginSpawn();
        vs.onSpawnAborted();
        assertEquals(NpcViewerRenderState.NOT_VISIBLE, vs.renderState());
        assertTrue(vs.beginSpawn());
    }

    @Test
    void clearResetsAllViewState() {
        NpcViewerSession session = session();
        session.getState("npc1").beginSpawn();
        session.getState("npc1").markVisible();
        session.visibleNpcIds().add("npc1");
        session.entityIdToNpc().put(123, "npc1");

        session.clear();
        assertTrue(session.visibleNpcIds().isEmpty());
        assertTrue(session.entityIdToNpc().isEmpty());
        assertTrue(session.npcStates().isEmpty());
    }
}
