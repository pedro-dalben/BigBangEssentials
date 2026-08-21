package com.pedrodalben.bigbangessentials.webdashboard.map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event listener for tracking player locations on the map
 * Updates player positions periodically for real-time map display
 */
@EventBusSubscriber(modid = "bigbangessentials")
public class MapPlayerTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapPlayerTracker.class);
    
    // Update player locations every second (20 ticks)
    private static final int UPDATE_INTERVAL = 20;
    private static int tickCounter = 0;
    
    /**
     * Track player join events
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLocationTracker tracker = PlayerLocationTracker.getInstance();
            tracker.updatePlayerLocation(player);
            LOGGER.debug("Map: Player joined - {}", player.getGameProfile().getName());
        }
    }
    
    /**
     * Track player leave events
     */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLocationTracker tracker = PlayerLocationTracker.getInstance();
            tracker.removePlayer(player.getUUID());
            LOGGER.debug("Map: Player left - {}", player.getGameProfile().getName());
        }
    }
    
    /**
     * Update player locations on server tick
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        
        // Update player locations every second
        if (tickCounter >= UPDATE_INTERVAL) {
            tickCounter = 0;
            
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                PlayerLocationTracker tracker = PlayerLocationTracker.getInstance();
                
                // Update all online players
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    tracker.updatePlayerLocation(player);
                }
            }
        }
    }
}
