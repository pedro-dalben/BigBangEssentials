package com.pedrodalben.bigbangessentials.util.handlers;

import com.pedrodalben.bigbangessentials.util.commands.PlayerStateCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Handles god mode damage cancellation and session tracking for playtime / god state cleanup.
 */
@EventBusSubscriber(modid = "bigbangessentials")
public class GodModeEventHandler {

    /** Cancel all incoming damage for players in god mode. */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerStateCommands.isGodMode(player.getUUID())) {
            event.setAmount(0f);
            event.setCanceled(true);
        }
    }

    /** Track session start for playtime and initialise god/fly maps. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerStateCommands.onPlayerJoin(player.getUUID());
            com.pedrodalben.bigbangessentials.util.commands.UtilityCommands.onPlayerJoin(player);
        }
    }

    /** Clean up god/session state on logout. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerStateCommands.onPlayerQuit(player.getUUID());
            com.pedrodalben.bigbangessentials.util.commands.UtilityCommands.onPlayerQuit(player.getUUID());
        }
    }
}

