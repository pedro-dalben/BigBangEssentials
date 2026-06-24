package com.pedrodalben.bigbangessentials.fabric.listener;

import com.pedrodalben.bigbangessentials.jobs.listeners.JobsEventListener;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public class FabricEvents {

    public static void register() {
        // Server Tick Event (Task Scheduler)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            com.pedrodalben.bigbangessentials.scheduler.TaskScheduler.onServerTick(server);
        });

        // Server Chat Event
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            boolean[] allow = {true};
            com.pedrodalben.bigbangessentials.chat.ChatHandler.handleChat(
                sender,
                message.signedContent(),
                () -> allow[0] = false
            );
            return allow[0];
        });

        // Player Logged Out Event
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            JobsEventListener.onPlayerLoggedOut(handler.getPlayer());
        });

        // Chunk Load Event
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            JobsEventListener.onChunkLoad(chunk);
        });

        // Chunk Unload Event
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            JobsEventListener.onChunkUnload(chunk);
        });

        // Block Break Event
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                JobsEventListener.onBlockBreak(serverPlayer, pos, state);
            }
        });

        // Living Death Event (Entity Kill Job)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer player) {
                JobsEventListener.onLivingDeath(entity, player);
            }
        });
    }
}
