package com.pedrodalben.bigbangessentials.forge.listener;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import com.pedrodalben.bigbangessentials.jobs.listeners.JobsEventListener;
import com.pedrodalben.bigbangessentials.rankup.listener.RankupEventListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.brewing.PlayerBrewedPotionEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ForgeEvents {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        if (com.pedrodalben.bigbangessentials.rankup.admin.RankupAdminChatInputHandler.getInstance()
                .onChat(event.getPlayer(), event.getRawText())) {
            event.setCanceled(true);
            return;
        }
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
        com.pedrodalben.bigbangessentials.teleportation.DirectTeleport.RandomTeleportManager.getInstance().onServerStop();
        com.pedrodalben.bigbangessentials.tablist.TablistEventHandler.onServerStop(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.pedrodalben.bigbangessentials.tablist.integration.WorldTabIntegration.onWorldChange(player);
            com.pedrodalben.bigbangessentials.npcs.service.NpcManager.getInstance().onPlayerDimensionChange(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BigBangEssentials.GameEvents.onPlayerLoggedIn(player);
            JobsEventListener.onPlayerLoggedIn(player);
            RankupEventListener.onPlayerLoggedIn(player);
            com.pedrodalben.bigbangessentials.tablist.TablistEventHandler.onPlayerJoin(player, event.getEntity().getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BigBangEssentials.GameEvents.onPlayerLoggedOut(player);
            JobsEventListener.onPlayerLoggedOut(player);
            RankupEventListener.onPlayerLoggedOut(player);
            com.pedrodalben.bigbangessentials.tablist.TablistEventHandler.onPlayerQuit(player, event.getEntity().getServer());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BigBangEssentials.GameEvents.onRegisterCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        com.pedrodalben.bigbangessentials.scheduler.TaskScheduler.onServerTick(server);
        com.pedrodalben.bigbangessentials.teleportation.DirectTeleport.RandomTeleportManager.getInstance().onServerTick(server);
        com.pedrodalben.bigbangessentials.menu.integration.kits.KitMenuIntegration.onTick();
        com.pedrodalben.bigbangessentials.tablist.TablistEventHandler.onServerTick(server);
        com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager.getInstance().tick();
        com.pedrodalben.bigbangessentials.npcs.service.NpcManager.getInstance().tick();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RankupEventListener.onPlayerTick(player);
                JobsEventListener.onPlayerTick(player);
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
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            if (com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager.getInstance()
                .getInteractionHandler().handleClick(player, event.getTarget().getId(),
                    com.pedrodalben.bigbangessentials.holograms.api.HologramActionTrigger.RIGHT_CLICK)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        BigBangHologramsManager.getInstance().getLegacyCleaner().cleanupIfLegacy(event.getEntity());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !event.isCanceled()) {
            JobsEventListener.onBlockBreak(player, event.getPos(), event.getState());
            RankupEventListener.onBlockBreak(player, event.getPos(), event.getState(), false);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            JobsEventListener.onBlockPlace(player, event.getPos(), event.getPlacedBlock());
            RankupEventListener.onBlockPlace(player, event.getPos(), event.getPlacedBlock(), false);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        JobsEventListener.onFinalizeSpawn(event.getEntity(), event.getSpawnType());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            JobsEventListener.onLivingDeath(event.getEntity(), player);
            RankupEventListener.onLivingDeath(event.getEntity(), player, false);
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            JobsEventListener.onItemFished(player, event.getDrops());
            RankupEventListener.onItemFished(player, event.getDrops(), false);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobsEventListener.onItemCrafted(player, event.getCrafting(), 1);
            RankupEventListener.onItemCrafted(player, event.getCrafting(), false);
        }
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobsEventListener.onItemSmelted(player, event.getSmelting(), 1, "furnace", null);
            RankupEventListener.onItemSmelted(player, event.getSmelting(), false);
        }
    }

    @SubscribeEvent
    public static void onMagicInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            BlockState state = event.getLevel().getBlockState(event.getPos());
            if (state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.BREWING_STAND)) {
                JobsEventListener.onMagicInteraction(player, event.getPos(), state);
            }
        }
    }

    @SubscribeEvent
    public static void onBrewPotionTaken(PlayerBrewedPotionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (JobsEventListener.isRewardablePotion(event.getStack())) {
                JobsEventListener.onBrewPotionTaken(player);
            }
        }
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobsEventListener.onAnvilRepair(player, event.getOutput());
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RankupEventListener.onAdvancement(player, event.getAdvancement().getId().toString(), false);
        }
    }
}
