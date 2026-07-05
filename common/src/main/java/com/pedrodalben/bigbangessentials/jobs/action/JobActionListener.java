package com.pedrodalben.bigbangessentials.jobs.action;

import net.minecraft.server.level.ServerPlayer;

/**
 * Listener interface for observing internal JobAction processed events.
 */
@FunctionalInterface
public interface JobActionListener {
    void onActionProcessed(ServerPlayer player, JobActionProcessedEvent event);
}
