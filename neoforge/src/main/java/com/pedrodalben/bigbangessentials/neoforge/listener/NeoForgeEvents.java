package com.pedrodalben.bigbangessentials.neoforge.listener;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import com.pedrodalben.bigbangessentials.jobs.listeners.JobsEventListener;
import com.pedrodalben.bigbangessentials.rankup.listener.RankupEventListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class NeoForgeEvents {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        com.pedrodalben.bigbangessentials.chat.ChatHandler.handleChat(
            event.getPlayer(),
            event.getRawText(),
            () -> event.setCanceled(true)
        );
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        BigBangEssentials.GameEvents.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        BigBangEssentials.GameEvents.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BigBangEssentials.GameEvents.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BigBangEssentials.GameEvents.onPlayerLoggedIn(player);
            JobsEventListener.onPlayerLoggedIn(player);
            RankupEventListener.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BigBangEssentials.GameEvents.onPlayerLoggedOut(player);
            JobsEventListener.onPlayerLoggedOut(player);
            RankupEventListener.onPlayerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BigBangEssentials.GameEvents.onRegisterCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        com.pedrodalben.bigbangessentials.scheduler.TaskScheduler.onServerTick(event.getServer());
        com.pedrodalben.bigbangessentials.menu.integration.kits.KitMenuIntegration.onTick();
        var server = event.getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RankupEventListener.onPlayerTick(player);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            JobsEventListener.onChunkLoad(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            JobsEventListener.onChunkUnload(chunk);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            JobsEventListener.onBlockBreak(player, event.getPos(), event.getState());
            RankupEventListener.onBlockBreak(player, event.getPos(), event.getState(), event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobsEventListener.onBlockPlace(player, event.getPos(), event.getPlacedBlock());
            RankupEventListener.onBlockPlace(player, event.getPos(), event.getPlacedBlock(), event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            JobsEventListener.onFinalizeSpawn(entity, event.getSpawnType());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            JobsEventListener.onLivingDeath(event.getEntity(), player);
            RankupEventListener.onLivingDeath(event.getEntity(), player, event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobsEventListener.onItemFished(player, event.getDrops());
            RankupEventListener.onItemFished(player, event.getDrops(), event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RankupEventListener.onAdvancement(player, event.getAdvancement().id().toString(), false);
        }
    }
}
