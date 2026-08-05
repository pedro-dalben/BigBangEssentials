package com.pedrodalben.bigbangessentials.fabric.listener;

import com.pedrodalben.bigbangessentials.jobs.listeners.JobsEventListener;
import com.pedrodalben.bigbangessentials.rankup.listener.RankupEventListener;
import com.pedrodalben.bigbangessentials.shop.handlers.ShopSignRegistrationService;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FabricEvents {

    public static void register() {
        // Server Tick Event (Task Scheduler + Kit Menu Refresh + RankUp Playtime + Tablist)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            com.pedrodalben.bigbangessentials.scheduler.TaskScheduler.onServerTick(server);
            com.pedrodalben.bigbangessentials.teleportation.DirectTeleport.RandomTeleportManager.getInstance().onServerTick(server);
            com.pedrodalben.bigbangessentials.menu.integration.kits.KitMenuIntegration.onTick();
            com.pedrodalben.bigbangessentials.tablist.TablistEventHandler.onServerTick(server);
            com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager.getInstance().tick();
            ShopSignRegistrationService.tick();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RankupEventListener.onPlayerTick(player);
                JobsEventListener.onPlayerTick(player);
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

        // Player Logged In Event - includes JobsEventListener + Tablist for data loading
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            com.pedrodalben.bigbangessentials.BigBangEssentials.GameEvents.onPlayerLoggedIn(handler.getPlayer());
            JobsEventListener.onPlayerLoggedIn(handler.getPlayer());
            RankupEventListener.onPlayerLoggedIn(handler.getPlayer());
            com.pedrodalben.bigbangessentials.tablist.TablistEventHandler.onPlayerJoin(handler.getPlayer(), server);
        });

        // Player Logged Out Event
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            com.pedrodalben.bigbangessentials.BigBangEssentials.GameEvents.onPlayerLoggedOut(handler.getPlayer());
            JobsEventListener.onPlayerLoggedOut(handler.getPlayer());
            RankupEventListener.onPlayerLoggedOut(handler.getPlayer());
            com.pedrodalben.bigbangessentials.tablist.TablistEventHandler.onPlayerQuit(handler.getPlayer(), server);
        });

        // Player Dimension Change Event (for tablist conditional designs)
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                com.pedrodalben.bigbangessentials.tablist.integration.WorldTabIntegration.onWorldChange(serverPlayer);
            }
        });

        // Chunk Load Event
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            JobsEventListener.onChunkLoad(chunk);
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            BigBangHologramsManager.getInstance().getLegacyCleaner().cleanupIfLegacy(entity);
        });

        // Chunk Unload Event
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            JobsEventListener.onChunkUnload(chunk);
        });

        // Block Break Event - AFTER ensures the break completed successfully
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                JobsEventListener.onBlockBreak(serverPlayer, pos, state);
                RankupEventListener.onBlockBreak(serverPlayer, pos, state, false);
            }
        });

        FabricCrateEvents.register();
        FabricShopEvents.register();

        // USE-MAGIC session tracking: marks player session for enchanting/brewing completion detection
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && !level.isClientSide()) {
                BlockPos pos = hitResult.getBlockPos();
                var state = level.getBlockState(pos);
                if (state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.BREWING_STAND)) {
                    JobsEventListener.onMagicInteraction(serverPlayer, pos, state);
                }
            }
            return InteractionResult.PASS;
        });

        // Living Death Event (Entity Kill)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer player) {
                JobsEventListener.onLivingDeath(entity, player);
                RankupEventListener.onLivingDeath(entity, player, false);
            }
        });
    }
}
