package com.pedrodalben.bigbangessentials.holograms.action;

import com.pedrodalben.bigbangessentials.holograms.api.BigBangHolograms;
import com.pedrodalben.bigbangessentials.holograms.api.HologramAction;
import com.pedrodalben.bigbangessentials.holograms.api.HologramActionTrigger;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPage;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InteractionHandler {

    private record HologramClickContext(String hologramId, int pageIndex, long lastClickTick) {}

    private final ActionEngine actionEngine;
    private final Map<Integer, HologramClickContext> interactionMap = new ConcurrentHashMap<>();
    private int cooldownTicks = 10;

    public InteractionHandler(ActionEngine actionEngine) {
        this.actionEngine = actionEngine;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
    }

    public void register(int entityId, String hologramId, int pageIndex) {
        interactionMap.put(entityId, new HologramClickContext(hologramId, pageIndex, 0L));
    }

    public void unregister(int entityId) {
        interactionMap.remove(entityId);
    }

    public boolean handleClick(ServerPlayer player, int entityId, HologramActionTrigger trigger) {
        HologramClickContext context = interactionMap.get(entityId);
        if (context == null) {
            return false;
        }

        Optional<HologramDefinition> optDef = BigBangHolograms.getApi().findDefinition(context.hologramId);
        if (optDef.isEmpty()) {
            return false;
        }

        HologramDefinition definition = optDef.get();

        // ponytail: per-account cooldowns if throughput matters
        long gameTime = player.level().getGameTime();
        if (gameTime - context.lastClickTick < cooldownTicks) {
            return false;
        }

        if (definition.flags().contains(com.pedrodalben.bigbangessentials.holograms.api.HologramFlag.DISABLE_ACTIONS)) {
            return false;
        }

        HologramPage page = getPage(definition, context.pageIndex);
        if (page == null) {
            return false;
        }

        for (HologramAction action : page.actions()) {
            if (action.trigger() == trigger) {
                actionEngine.execute(action, player, definition);
            }
        }

        interactionMap.put(entityId, new HologramClickContext(context.hologramId, context.pageIndex, gameTime));
        return true;
    }

    public void clear() {
        interactionMap.clear();
    }

    private static HologramPage getPage(HologramDefinition definition, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= definition.pages().size()) {
            return null;
        }
        return definition.pages().get(pageIndex);
    }
}
