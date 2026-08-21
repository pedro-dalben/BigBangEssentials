package com.pedrodalben.bigbangessentials.npcs;

import com.pedrodalben.bigbangessentials.npcs.api.*;
import com.pedrodalben.bigbangessentials.npcs.config.NpcConfig;
import com.pedrodalben.bigbangessentials.npcs.config.NpcConfigStore;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence round-trip: an NPC survives save → new store → load with all
 * fields intact (position, dimension, yaw/pitch, name, skin player name,
 * action, hologram, look settings, interaction, enabled).
 */
class NpcLifecycleTest {

    private static final Path CONFIG_DIR = Path.of("world/serverconfig/bigbangessentials/npcs");

    @BeforeEach
    @AfterEach
    void cleanConfigDir() throws IOException {
        if (Files.exists(CONFIG_DIR)) {
            try (var paths = Files.walk(CONFIG_DIR)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    private static NpcDefinition fullNpc() {
        NpcLocation loc = new NpcLocation(ResourceLocation.parse("minecraft:the_nether"), 12.5, 70.25, -300.75, 45.0f, -10.0f);
        return new NpcDefinition("loja_npc", true, "Loja",
            loc, NpcSkin.unresolved("Dalbesmr"),
            NpcAction.playerCommand("shop"),
            new NpcHologramConfig(true, java.util.List.of("<green>Loja", "<gray>Clique"), 2.25, 32.0, true, false),
            new NpcLookSettings(true, 12.0, 4, 2.0, 100.0, 45.0, 35.0, true, true),
            64.0, 80.0,
            new NpcInteractionConfig(5.0, 1000, "bigbangessentials.npcs.use"));
    }

    @Test
    void saveThenReloadPreservesEveryField() {
        NpcConfigStore store = new NpcConfigStore();
        NpcDefinition npc = fullNpc();
        NpcConfig config = NpcConfig.defaults().withNpcs(new LinkedHashMap<>(Map.of(npc.id(), npc)));

        store.save(config);

        // Simulate a server restart with a brand-new store instance.
        NpcConfigStore fresh = new NpcConfigStore();
        NpcConfig loaded = fresh.load();

        assertTrue(loaded.npcs().containsKey("loja_npc"), "NPC must survive the round trip");
        NpcDefinition restored = loaded.npcs().get("loja_npc");

        assertEquals("loja_npc", restored.id());
        assertTrue(restored.enabled());
        assertEquals("Loja", restored.displayName());
        assertEquals(ResourceLocation.parse("minecraft:the_nether"), restored.location().dimension());
        assertEquals(12.5, restored.location().x());
        assertEquals(70.25, restored.location().y());
        assertEquals(-300.75, restored.location().z());
        assertEquals(45.0f, restored.location().yaw());
        assertEquals(-10.0f, restored.location().pitch());
        assertEquals("Dalbesmr", restored.skin().playerName());
        assertEquals(NpcActionType.PLAYER_COMMAND, restored.action().type());
        assertEquals("shop", restored.action().command());
        assertTrue(restored.hologram().enabled());
        assertEquals(2, restored.hologram().lines().size());
        assertEquals("<green>Loja", restored.hologram().lines().get(0));
        assertEquals(2.25, restored.hologram().offsetY());
        assertTrue(restored.lookSettings().enabled());
        assertEquals(12.0, restored.lookSettings().range());
        assertEquals(64.0, restored.viewDistance());
        assertEquals(80.0, restored.despawnDistance());
        assertEquals(5.0, restored.interaction().distance());
        assertEquals(1000, restored.interaction().cooldownMillis());
        assertEquals("bigbangessentials.npcs.use", restored.interaction().permission());
    }

    @Test
    void missingConfigCreatesDefaults() {
        NpcConfigStore store = new NpcConfigStore();
        NpcConfig loaded = store.load();
        assertTrue(loaded.npcs().isEmpty());
        assertEquals(48.0, loaded.defaultViewDistance());
    }

    @Test
    void disabledNpcIsPreserved() {
        NpcConfigStore store = new NpcConfigStore();
        NpcDefinition disabled = new NpcDefinition("old_npc", false, "Old", fullNpc().location(),
            NpcSkin.unresolved("Notch"), NpcAction.none(), NpcHologramConfig.disabled(),
            NpcLookSettings.disabled(), 48.0, 56.0, NpcInteractionConfig.defaults());
        store.save(NpcConfig.defaults().withNpcs(new LinkedHashMap<>(Map.of(disabled.id(), disabled))));

        NpcDefinition restored = new NpcConfigStore().load().npcs().get("old_npc");
        assertNotNull(restored);
        assertFalse(restored.enabled());
        assertEquals("Notch", restored.skin().playerName());
    }
}
