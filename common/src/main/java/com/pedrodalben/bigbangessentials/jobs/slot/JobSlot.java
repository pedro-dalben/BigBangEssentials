package com.pedrodalben.bigbangessentials.jobs.slot;

import java.util.Optional;

/**
 * Runtime representation of a player's job slot.
 */
public record JobSlot(
        String slotType,
        String category,
        Optional<String> activeJobId,
        long activatedAt,
        long lastChangedAt,
        long cooldownUntil,
        String source
) {
    public boolean isEmpty() {
        return activeJobId.isEmpty() || activeJobId.get().isBlank();
    }

    public boolean isOnCooldown(long currentTime) {
        return cooldownUntil > currentTime;
    }
}
