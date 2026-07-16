package com.pedrodalben.bigbangessentials.menu.neoforge;

import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NeoForgeMenuRendererTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
        }
    }

    @Test
    void resolveLoreComponentsSplitsMultilineValuesIntoSeparateLines() {
        List<Component> components = NeoForgeMenuRenderer.resolveLoreComponents(
            List.of("<gray>Linha 1\n<yellow>Linha 2", "<green>Linha 3"),
            null,
            new MenuContext(UUID.randomUUID(), "pt_BR", Map.of(), Map.of(), "test", "test", UUID.randomUUID())
        );

        assertNotNull(components);
        assertEquals(3, components.size());
        assertEquals("Linha 1", components.get(0).getString());
        assertEquals("Linha 2", components.get(1).getString());
        assertEquals("Linha 3", components.get(2).getString());
    }

    @Test
    void resolveLoreComponentsSkipsEmptyPlaceholderOnlyLines() {
        List<Component> components = NeoForgeMenuRenderer.resolveLoreComponents(
            List.of("<gray>Antes", "{context:job_license_objectives}", "<gray>Depois"),
            null,
            new MenuContext(
                UUID.randomUUID(),
                "pt_BR",
                Map.of(),
                Map.of("job_license_objectives", ""),
                "test",
                "test",
                UUID.randomUUID()
            )
        );

        assertNotNull(components);
        assertEquals(2, components.size());
        assertEquals("Antes", components.get(0).getString());
        assertEquals("Depois", components.get(1).getString());
    }
}
