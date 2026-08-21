package com.pedrodalben.bigbangessentials.teleportation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Event hooks for home cache maintenance.
 */
@EventBusSubscriber(modid = "bigbangessentials")
public class HomeEventHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        HomeManager.getInstance().invalidateMaxHomesCache(player.getUUID());
        com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager.getInstance()
            .invalidateMaxPlayerWarpsCache(player.getUUID());
    }
}
