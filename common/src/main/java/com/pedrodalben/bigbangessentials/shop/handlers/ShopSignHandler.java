package com.pedrodalben.bigbangessentials.shop.handlers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Listens for sign placement and subsequent text finalization to register new ChestShop signs.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>{@link BlockEvent.EntityPlaceEvent} — a sign is placed; we record the position + player as "pending".</li>
 *   <li>{@link ServerTickEvent.Post} — every tick we re-check pending signs.
 *       Once a sign has text on all 4 lines (player finished editing), we attempt to parse it
 *       as a shop. Pending entries time out after 30 seconds if never filled.</li>
 * </ol>
 *
 * This deferred approach is necessary because NeoForge 1.21.1 has no sign-text-written event;
 * the {@code ServerboundSignUpdatePacket} is processed server-side before any NeoForge event fires.
 */
@EventBusSubscriber(modid = "bigbangessentials")
public final class ShopSignHandler {

    // ── Sign placed ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onSignPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ShopSignRegistrationService.trackPlacement(player, level, event.getPos(), event.getState());
    }

    // ── Tick check ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ShopSignRegistrationService.tick();
    }

    // ── Shop registration ─────────────────────────────────────────────────────

    /**
     * Attempt to register the sign at {@code pos} as a ChestShop.
     * Also called externally from /chestshop convert for existing signs.
     *
     * <p>Handles both auto-assign features:
     * <ul>
     *   <li>Line 0 blank → auto-assigned to the placing player's name</li>
     *   <li>Line 3 "?"   → shop registered in pending state; owner must right-click with item</li>
     * </ul>
     */
    public static void tryRegisterShop(ServerPlayer player, String[] lines,
                                       BlockPos pos, String dimension, ServerLevel level) {
        ShopSignRegistrationService.tryRegisterShop(player, lines, pos, dimension, level);
    }

    // ── Sign text helpers ─────────────────────────────────────────────────────

    /** Read raw plain text from all 4 front-face lines of a sign. */
    public static String[] readSignLines(SignBlockEntity sign) {
        return ShopSignText.read(sign);
    }

    /** Write formatted text back to a sign's 4 lines and sync to clients. */
    public static void writeSignLines(ServerLevel level, BlockPos pos, String[] lines) {
        ShopSignText.write(level, pos, lines);
    }
}
