package com.pedrodalben.bigbangessentials.fabric.listener;

import com.pedrodalben.bigbangessentials.jobs.listeners.JobsEventListener;
import com.pedrodalben.bigbangessentials.rankup.listener.RankupEventListener;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public class FabricEvents {

    public static void register() {
        // Server Tick Event (Task Scheduler + Kit Menu Refresh + RankUp Playtime)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            com.pedrodalben.bigbangessentials.scheduler.TaskScheduler.onServerTick(server);
            com.pedrodalben.bigbangessentials.menu.integration.kits.KitMenuIntegration.onTick();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RankupEventListener.onPlayerTick(player);
            }
        });

        // Server Chat Event
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            boolean[] allow = {true};
            if (com.pedrodalben.bigbangessentials.rankup.admin.RankupAdminChatInputHandler.getInstance()
                    .onChat(sender, message.signedContent())) {
                return false;
            }
            com.pedrodalben.bigbangessentials.chat.ChatHandler.handleChat(
                sender,
                message.signedContent(),
                () -> allow[0] = false
            );
            return allow[0];
        });

        // Player Logged In Event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            com.pedrodalben.bigbangessentials.BigBangEssentials.GameEvents.onPlayerLoggedIn(handler.getPlayer());
            RankupEventListener.onPlayerLoggedIn(handler.getPlayer());
        });

        // Player Logged Out Event
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            com.pedrodalben.bigbangessentials.BigBangEssentials.GameEvents.onPlayerLoggedOut(handler.getPlayer());
            JobsEventListener.onPlayerLoggedOut(handler.getPlayer());
            RankupEventListener.onPlayerLoggedOut(handler.getPlayer());
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
                RankupEventListener.onBlockBreak(serverPlayer, pos, state, false);
            }
        });

        FabricCrateEvents.register();

        // Living Death Event (Entity Kill)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer player) {
                JobsEventListener.onLivingDeath(entity, player);
                RankupEventListener.onLivingDeath(entity, player, false);
            }
        });
    }
}
