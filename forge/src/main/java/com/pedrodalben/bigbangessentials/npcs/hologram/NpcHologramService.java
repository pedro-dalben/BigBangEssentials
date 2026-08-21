package com.pedrodalben.bigbangessentials.npcs.hologram;

import com.pedrodalben.bigbangessentials.holograms.api.*;
import com.pedrodalben.bigbangessentials.npcs.api.NpcDefinition;
import com.pedrodalben.bigbangessentials.npcs.api.NpcHologramConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NpcHologramService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcHologramService.class);
    private static final String NPC_OWNER = "bigbangessentials:npc";

    private static String hologramId(String npcId) {
        return "bigbangessentials:npc/" + npcId;
    }

    public void createOrUpdate(NpcDefinition npc) {
        HologramService api = BigBangHolograms.getApi();
        if (api == null) return;

        NpcHologramConfig config = npc.hologram();
        String holId = hologramId(npc.id());

        if (!config.enabled()) {
            api.delete(holId);
            return;
        }

        if (config.lines().isEmpty()) return;

        var existing = api.findDefinition(holId);
        HologramDefinition definition;
        try {
            List<HologramLine> lines = new ArrayList<>();
            for (String text : config.lines()) {
                lines.add(HologramLine.text(text));
            }

            HologramPage page = new HologramPage(lines);
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, npc.location().dimension());
            HologramLocation loc = new HologramLocation(dimKey, npc.location().x(),
                npc.location().y(), npc.location().z());

            definition = HologramDefinition.builder(holId)
                .ownerId(NPC_OWNER)
                .location(loc)
                .page(page)
                .viewDistance((int) config.viewDistance())
                .persistent(true)
                .offset(0, config.offsetY(), 0)
                .shadow(config.shadow())
                .seeThrough(config.seeThrough())
                .build();
        } catch (Exception e) {
            LOGGER.warn("Failed to build hologram definition for NPC '{}': {}", npc.id(), e.getMessage());
            return;
        }

        api.createOrUpdate(definition);
    }

    public void remove(String npcId) {
        HologramService api = BigBangHolograms.getApi();
        if (api != null) api.delete(hologramId(npcId));
    }

    public void cleanupOrphans(Map<String, NpcDefinition> knownNpcs) {
        HologramService api = BigBangHolograms.getApi();
        if (api == null) return;

        int removed = api.deleteByOwner(NPC_OWNER);

        for (NpcDefinition npc : knownNpcs.values()) {
            createOrUpdate(npc);
        }
    }

    public int countActive() {
        HologramService api = BigBangHolograms.getApi();
        if (api == null) return 0;
        int count = 0;
        for (HologramDefinition def : api.getDefinitions()) {
            if (def.ownerId().equals(NPC_OWNER) && def.enabled()) count++;
        }
        return count;
    }
}
