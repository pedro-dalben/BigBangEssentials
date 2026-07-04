package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrateLocationTest {

    @Test
    void constructor_SetsFields() {
        UUID id = UUID.randomUUID();
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse("minecraft:overworld"));
        BlockPos pos = new BlockPos(100, 64, 200);

        CrateLocation loc = new CrateLocation(id, "crate_vip", dimension, pos);
        assertEquals(id, loc.getId());
        assertEquals("crate_vip", loc.getCrateId());
        assertEquals(dimension, loc.getDimension());
        assertEquals(pos, loc.getPosition());
    }

    @Test
    void constructor_NullId_GeneratesNewId() {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse("minecraft:overworld"));
        CrateLocation loc = new CrateLocation(null, "crate_test", dimension, new BlockPos(0, 0, 0));
        assertNotNull(loc.getId());
    }

    @Test
    void constructor_SetsDefaults() {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse("minecraft:nether"));
        CrateLocation loc = new CrateLocation(UUID.randomUUID(), "crate_test", dimension, new BlockPos(10, 20, 30));

        assertNull(loc.getHologramTemplate());
        assertEquals(1.0, loc.getHologramOffsetY(), 0.001);
        assertTrue(loc.isHologramEnabled());
        assertTrue(loc.isParticleEnabled());
        assertTrue(loc.isActive());
        assertNotNull(loc.getCreatedAt());
        assertNotNull(loc.getUpdatedAt());
    }

    @Test
    void getWorldName_ReturnsDimensionLocation() {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse("minecraft:the_end"));
        CrateLocation loc = new CrateLocation(UUID.randomUUID(), "crate_test", dimension, new BlockPos(0, 0, 0));
        assertEquals("minecraft:the_end", loc.getWorldName());
    }

    @Test
    void getX_Y_Z_ReturnPositionValues() {
        CrateLocation loc = new CrateLocation(
            UUID.randomUUID(),
            "crate_test",
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")),
            new BlockPos(42, 128, -10)
        );
        assertEquals(42, loc.getX());
        assertEquals(128, loc.getY());
        assertEquals(-10, loc.getZ());
    }

    @Test
    void setHologramTemplate_Updates() {
        CrateLocation loc = createDefault();
        assertNull(loc.getHologramTemplate());
        loc.setHologramTemplate("custom_template");
        assertEquals("custom_template", loc.getHologramTemplate());
    }

    @Test
    void setHologramOffsetY_Updates() {
        CrateLocation loc = createDefault();
        loc.setHologramOffsetY(3.5);
        assertEquals(3.5, loc.getHologramOffsetY(), 0.001);
    }

    @Test
    void setHologramEnabled_Toggles() {
        CrateLocation loc = createDefault();
        assertTrue(loc.isHologramEnabled());
        loc.setHologramEnabled(false);
        assertFalse(loc.isHologramEnabled());
    }

    @Test
    void setParticleEnabled_Toggles() {
        CrateLocation loc = createDefault();
        assertTrue(loc.isParticleEnabled());
        loc.setParticleEnabled(false);
        assertFalse(loc.isParticleEnabled());
    }

    @Test
    void setActive_Toggles() {
        CrateLocation loc = createDefault();
        assertTrue(loc.isActive());
        loc.setActive(false);
        assertFalse(loc.isActive());
    }

    @Test
    void toJson_Roundtrip() {
        UUID id = UUID.randomUUID();
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse("minecraft:overworld"));
        BlockPos pos = new BlockPos(100, 64, -200);

        CrateLocation original = new CrateLocation(id, "crate_vip", dimension, pos);
        original.setHologramTemplate("vip_template");
        original.setHologramOffsetY(3.0);
        original.setHologramEnabled(false);
        original.setParticleEnabled(false);
        original.setActive(true);

        JsonObject json = original.toJson();
        CrateLocation restored = CrateLocation.fromJson(json);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getCrateId(), restored.getCrateId());
        assertEquals(original.getWorldName(), restored.getWorldName());
        assertEquals(original.getX(), restored.getX());
        assertEquals(original.getY(), restored.getY());
        assertEquals(original.getZ(), restored.getZ());
        assertEquals(original.getHologramTemplate(), restored.getHologramTemplate());
        assertEquals(original.getHologramOffsetY(), restored.getHologramOffsetY(), 0.001);
        assertEquals(original.isHologramEnabled(), restored.isHologramEnabled());
        assertEquals(original.isParticleEnabled(), restored.isParticleEnabled());
        assertEquals(original.isActive(), restored.isActive());
    }

    @Test
    void fromJson_RestoresAllFields() {
        JsonObject json = new JsonObject();
        json.addProperty("id", UUID.randomUUID().toString());
        json.addProperty("crateId", "crate_event");
        json.addProperty("world", "minecraft:the_end");
        json.addProperty("x", 50);
        json.addProperty("y", 80);
        json.addProperty("z", 300);
        json.addProperty("hologramTemplate", "event_template");
        json.addProperty("hologramOffsetY", 1.5);
        json.addProperty("hologramEnabled", false);
        json.addProperty("particleEnabled", true);
        json.addProperty("active", false);
        json.addProperty("createdAt", java.time.Instant.now().toString());
        json.addProperty("updatedAt", java.time.Instant.now().toString());

        CrateLocation loc = CrateLocation.fromJson(json);
        assertEquals("crate_event", loc.getCrateId());
        assertEquals("minecraft:the_end", loc.getWorldName());
        assertEquals(50, loc.getX());
        assertEquals(80, loc.getY());
        assertEquals(300, loc.getZ());
        assertEquals("event_template", loc.getHologramTemplate());
        assertEquals(1.5, loc.getHologramOffsetY(), 0.001);
        assertFalse(loc.isHologramEnabled());
        assertTrue(loc.isParticleEnabled());
        assertFalse(loc.isActive());
    }

    private CrateLocation createDefault() {
        return new CrateLocation(
            UUID.randomUUID(),
            "crate_test",
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")),
            new BlockPos(0, 0, 0)
        );
    }
}
