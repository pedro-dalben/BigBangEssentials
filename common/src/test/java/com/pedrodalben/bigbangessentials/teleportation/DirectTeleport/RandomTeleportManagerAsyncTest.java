package com.pedrodalben.bigbangessentials.teleportation.DirectTeleport;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomTeleportManagerAsyncTest {

    @Test
    void searchPathHasNoBlockingChunkLoad() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/pedrodalben/bigbangessentials/teleportation/DirectTeleport/RandomTeleportManager.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("getChunkFuture"));
        assertTrue(source.contains("MAX_SEARCHES_PER_TICK = 2"));
        assertFalse(source.contains("level.getChunk("));
        assertFalse(source.contains(".join("));
    }
}
