package com.pedrodalben.bigbangessentials.holograms;

import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import com.pedrodalben.bigbangessentials.holograms.api.BigBangHolograms;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPage;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLine;
import com.pedrodalben.bigbangessentials.holograms.api.HologramHandle;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinitionBuilder;
import com.pedrodalben.bigbangessentials.holograms.api.HologramFlag;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrateHologramRegressionTest {

    private static final UUID LOCATION_UUID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final String EXPECTED_ID = "bigbangessentials:crate/" + LOCATION_UUID.toString().toLowerCase();
    private static final String CRATE_OWNER = "bigbangessentials:crate";

    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        BigBangHologramsManager.getInstance().shutdown();
    }

    // ── Stable ID ────────────────────────────────────────────────────────────

    @Test
    void testCrateHologramIdFormat() {
        CrateLocation location = new CrateLocation(
            LOCATION_UUID,
            "test_crate",
            Level.OVERWORLD,
            new BlockPos(1, 64, 1)
        );

        String id = CrateHologramManager.hologramId(location);

        assertEquals(EXPECTED_ID, id, "ID must follow format bigbangessentials:crate/<uuid>");
        assertTrue(id.startsWith("bigbangessentials:crate/"), "ID must begin with namespace prefix");
        assertTrue(id.endsWith(LOCATION_UUID.toString().toLowerCase()), "ID must end with location UUID");
    }

    @Test
    void testCrateHologramIdIsStable() {
        CrateLocation locationA = new CrateLocation(LOCATION_UUID, "a", Level.OVERWORLD, new BlockPos(0, 64, 0));
        CrateLocation locationB = new CrateLocation(LOCATION_UUID, "b", Level.OVERWORLD, new BlockPos(100, 200, 300));

        assertEquals(
            CrateHologramManager.hologramId(locationA),
            CrateHologramManager.hologramId(locationB),
            "Same location UUID must produce same hologram ID regardless of crate key or position"
        );
    }

    // ── Owner ─────────────────────────────────────────────────────────────────

    @Test
    void testCrateHologramOwner() {
        HologramDefinition definition = crateDefinition();

        assertEquals(CRATE_OWNER, definition.ownerId(), "Crate hologram owner must be " + CRATE_OWNER);
    }

    @Test
    void testCrateHologramOwnerIsNotEmpty() {
        HologramDefinition definition = crateDefinition();

        assertNotNull(definition.ownerId());
        assertFalse(definition.ownerId().isEmpty(), "Owner must not be empty for crate holograms");
    }

    // ── Immutability ──────────────────────────────────────────────────────────

    @Test
    void testHologramDefinitionPagesImmutability() {
        HologramDefinition definition = crateDefinition();
        List<HologramPage> pages = definition.pages();

        assertNotNull(pages);
        assertFalse(pages.isEmpty(), "Definition must have at least one page");
        assertThrows(UnsupportedOperationException.class,
            () -> pages.add(HologramPage.ofLines(List.of("new line"))),
            "Pages list must be unmodifiable"
        );
    }

    @Test
    void testHologramPageLinesImmutability() {
        HologramDefinition definition = crateDefinition();
        HologramPage page = definition.pages().get(0);
        List<HologramLine> lines = page.lines();

        assertNotNull(lines);
        assertFalse(lines.isEmpty(), "Page must have at least one line");
        assertThrows(UnsupportedOperationException.class,
            () -> lines.add(HologramLine.text("extra")),
            "Lines list inside a page must be unmodifiable"
        );
    }

    @Test
    void testHologramDefinitionMetadataImmutability() {
        HologramDefinition definition = crateDefinition();
        var metadata = definition.metadata();

        assertNotNull(metadata);
        assertThrows(UnsupportedOperationException.class,
            () -> metadata.put("extra", "value"),
            "Metadata map must be unmodifiable"
        );
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    @Test
    void testBuilderProducesValidDefinition() {
        ResourceKey<Level> dimension = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.parse("minecraft:overworld")
        );
        HologramLocation location = new HologramLocation(dimension, 0.0D, 64.0D, 0.0D);

        HologramDefinition definition = HologramDefinition.builder(EXPECTED_ID)
            .ownerId(CRATE_OWNER)
            .location(location)
            .lines(List.of("Line 1", "Line 2"))
            .viewDistance(32)
            .persistent(false)
            .build();

        assertEquals(EXPECTED_ID, definition.id());
        assertEquals(CRATE_OWNER, definition.ownerId());
        assertEquals(location, definition.location());
        assertEquals(2, definition.pages().get(0).lines().size());
        assertEquals("Line 1", definition.pages().get(0).lines().get(0).text());
        assertEquals("Line 2", definition.pages().get(0).lines().get(1).text());
        assertEquals(32, definition.viewDistance());
        assertFalse(definition.persistent());
    }

    @Test
    void testBuilderIdIsNormalized() {
        HologramLocation location = new HologramLocation(Level.OVERWORLD, 0.0D, 64.0D, 0.0D);

        HologramDefinition definition = HologramDefinition.builder("BigBangEssentials:CRATE/Uppercase")
            .ownerId(CRATE_OWNER)
            .location(location)
            .lines(List.of("test"))
            .build();

        assertEquals("bigbangessentials:crate/uppercase", definition.id(),
            "Builder must normalize ID to lowercase");
    }

    @Test
    void testToBuilderRoundtripPreservesAllFields() {
        HologramLocation location = new HologramLocation(Level.OVERWORLD, 1.0D, 70.0D, 2.0D);

        HologramDefinition original = HologramDefinition.builder(EXPECTED_ID)
            .ownerId(CRATE_OWNER)
            .location(location)
            .lines(List.of("Roundtrip"))
            .viewDistance(48)
            .persistent(true)
            .flags(EnumSet.of(HologramFlag.STATIC_CONTENT))
            .build();

        HologramDefinition rebuilt = original.toBuilder().build();

        assertEquals(original.id(), rebuilt.id());
        assertEquals(original.ownerId(), rebuilt.ownerId());
        assertEquals(original.location(), rebuilt.location());
        assertEquals(original.viewDistance(), rebuilt.viewDistance());
        assertEquals(original.persistent(), rebuilt.persistent());
        assertEquals(
            original.pages().get(0).lines().get(0).text(),
            rebuilt.pages().get(0).lines().get(0).text()
        );
    }

    @Test
    void testBuilderRejectsEmptyPages() {
        HologramLocation location = new HologramLocation(Level.OVERWORLD, 0.0D, 64.0D, 0.0D);

        assertThrows(IllegalArgumentException.class,
            () -> HologramDefinition.builder(EXPECTED_ID)
                .ownerId(CRATE_OWNER)
                .location(location)
                .pages(new ArrayList<>())
                .build(),
            "Builder must reject empty pages list"
        );
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    void testCreateOrUpdateIsIdempotent() {
        HologramDefinition definition = crateDefinitionWithFlags();

        HologramHandle first = BigBangHolograms.getApi().createOrUpdate(definition);
        HologramHandle second = BigBangHolograms.getApi().createOrUpdate(definition);

        assertEquals(first.id(), second.id(),
            "createOrUpdate must return same handle ID for same definition");
        assertEquals(EXPECTED_ID, first.id());

        assertTrue(BigBangHolograms.getApi().exists(EXPECTED_ID),
            "Hologram must exist after createOrUpdate");

        var stored = BigBangHolograms.getApi().findDefinition(EXPECTED_ID);
        assertTrue(stored.isPresent(), "findDefinition must return the stored definition");
        assertEquals(CRATE_OWNER, stored.get().ownerId());
        assertEquals(EXPECTED_ID, stored.get().id());

        List<String> allIds = BigBangHolograms.getApi().getDefinitions().stream()
            .map(HologramDefinition::id)
            .toList();

        assertEquals(1, allIds.stream().filter(id -> id.equals(EXPECTED_ID)).count(),
            "Calling createOrUpdate twice with same definition must not create duplicates");
    }

    @Test
    void testCreateOrUpdateWithModifiedContentPreservesId() {
        HologramLocation location = new HologramLocation(Level.OVERWORLD, 0.0D, 64.0D, 0.0D);

        HologramDefinition first = HologramDefinition.builder(EXPECTED_ID)
            .ownerId(CRATE_OWNER)
            .location(location)
            .lines(List.of("Version A"))
            .flags(EnumSet.of(HologramFlag.STATIC_CONTENT))
            .build();

        HologramDefinition second = HologramDefinition.builder(EXPECTED_ID)
            .ownerId(CRATE_OWNER)
            .location(location)
            .lines(List.of("Version B"))
            .flags(EnumSet.of(HologramFlag.STATIC_CONTENT))
            .build();

        BigBangHolograms.getApi().createOrUpdate(first);
        BigBangHolograms.getApi().createOrUpdate(second);

        var stored = BigBangHolograms.getApi().findDefinition(EXPECTED_ID);
        assertTrue(stored.isPresent());
        assertEquals("Version B", stored.get().pages().get(0).lines().get(0).text(),
            "createOrUpdate with modified content must replace previous definition");

        List<String> allIds = BigBangHolograms.getApi().getDefinitions().stream()
            .map(HologramDefinition::id)
            .toList();
        assertEquals(1, allIds.stream().filter(id -> id.equals(EXPECTED_ID)).count(),
            "Modified update must not create duplicate holograms");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static HologramDefinition crateDefinition() {
        HologramLocation location = new HologramLocation(Level.OVERWORLD, 0.0D, 64.0D, 0.0D);
        return HologramDefinition.builder(EXPECTED_ID)
            .ownerId(CRATE_OWNER)
            .location(location)
            .lines(List.of("Crate name", "Description"))
            .viewDistance(32)
            .persistent(false)
            .build();
    }

    private static HologramDefinition crateDefinitionWithFlags() {
        HologramLocation location = new HologramLocation(Level.OVERWORLD, 0.0D, 64.0D, 0.0D);
        return HologramDefinition.builder(EXPECTED_ID)
            .ownerId(CRATE_OWNER)
            .location(location)
            .lines(List.of("Crate name", "Description"))
            .viewDistance(32)
            .persistent(false)
            .flags(EnumSet.of(HologramFlag.STATIC_CONTENT))
            .build();
    }
}
