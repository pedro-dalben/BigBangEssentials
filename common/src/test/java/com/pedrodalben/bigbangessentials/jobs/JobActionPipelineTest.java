package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionValidator;
import com.pedrodalben.bigbangessentials.jobs.database.JobActionReceiptRepository;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobActionPipelineTest {

    static {
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    private UUID playerId;
    private ServerPlayer mockPlayer;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);
        when(mockPlayer.getName()).thenReturn(net.minecraft.network.chat.Component.literal("TestPlayer"));
        JobActionReceiptRepository.getInstance().clearMemoryCache();
    }

    @Test
    void testJobActionTypeParsingAndAliases() {
        assertEquals(JobActionType.BREAK_BLOCK, JobActionType.fromString("BREAK-BLOCK"));
        assertEquals(JobActionType.BREAK_BLOCK, JobActionType.fromString("BREAK_BLOCK"));
        assertEquals(JobActionType.PLACE_BLOCK, JobActionType.fromString("PLACE-BLOCK"));
        assertEquals(JobActionType.PLACE_BLOCK, JobActionType.fromString("PLACE-PROJECT-BLOCK"));
        assertEquals(JobActionType.FISH, JobActionType.fromString("FISH"));
        assertEquals(JobActionType.FISH, JobActionType.fromString("FISH-CATCH"));
        assertEquals(JobActionType.POKEMON_CAPTURED, JobActionType.fromString("POKEMON-CAPTURED"));
        assertEquals(JobActionType.RAID_CLEARED, JobActionType.fromString("RAID_CLEARED"));
        assertNull(JobActionType.fromString("INVALID_ACTION_TYPE"));
    }

    @Test
    void testJobActionContextBuilderAndAttributes() {
        JobActionContext context = JobActionContext.builder()
                .dimension("minecraft:overworld")
                .position("100, 64, -200")
                .playerPlacedBlock(true)
                .customAttribute("pokemon_level", 50)
                .customAttribute("shiny", true)
                .tag("minecraft:ores")
                .build();

        assertEquals("minecraft:overworld", context.getDimension());
        assertEquals("100, 64, -200", context.getPosition());
        assertTrue(context.isPlayerPlacedBlock());
        assertEquals(50, context.getCustomAttributeAsInt("pokemon_level", 1));
        assertTrue(context.getCustomAttributeAsBoolean("shiny", false));
        assertTrue(context.getTags().contains("minecraft:ores"));
        assertTrue(context.getMetadataJson().contains("\"dim\":\"minecraft:overworld\""));
        assertTrue(context.getMetadataJson().contains("\"placed\":true"));
    }

    @Test
    void testIdempotencyReservationAndDuplicationPrevention() {
        UUID actionId = UUID.randomUUID();

        // 1st reservation should succeed
        boolean firstReserve = JobActionReceiptRepository.getInstance().reserveAction(actionId, playerId);
        assertTrue(firstReserve, "Primeira reserva da ação deve ser bem-sucedida.");

        // 2nd reservation with same actionId should fail (preventing duplicate rewards)
        boolean secondReserve = JobActionReceiptRepository.getInstance().reserveAction(actionId, playerId);
        assertFalse(secondReserve, "Segunda reserva com mesmo actionId deve ser bloqueada.");

        // Check if repo marks it as processed or processing
        assertTrue(JobActionReceiptRepository.getInstance().isAlreadyProcessedOrProcessing(actionId));
    }

    @Test
    void testValidatorAntiExploitPlayerPlacedBlock() {
        JobActionContext context = JobActionContext.builder()
                .playerPlacedBlock(true)
                .build();

        JobAction action = JobAction.create(playerId, JobActionType.BREAK_BLOCK, "TEST_SOURCE", "minecraft:diamond_ore", context);

        JobActionValidator.ValidationResult result = JobActionValidator.getInstance().validate(mockPlayer, action);
        assertFalse(result.isValid(), "A quebra de bloco colocado pelo jogador deve ser considerada inválida.");
        assertEquals("Bloco colocado pelo jogador não concede recompensa ao quebrar.", result.reason());
    }
}
