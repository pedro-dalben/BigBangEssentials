package com.pedrodalben.bigbangessentials.jobs.slot;

/**
 * Configuration definition for a Job Slot (e.g. COMMON_PRIMARY, COMMON_SECONDARY, POKEMON_SPECIALIZATION).
 */
public record JobSlotDefinition(
        String slotType,
        String displayName,
        String category,
        int cooldownMinutes
) {}
