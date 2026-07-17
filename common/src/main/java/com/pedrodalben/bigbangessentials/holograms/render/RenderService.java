package com.pedrodalben.bigbangessentials.holograms.render;

import com.pedrodalben.bigbangessentials.holograms.api.*;
import com.pedrodalben.bigbangessentials.holograms.animation.AnimationEngine;
import com.pedrodalben.bigbangessentials.holograms.metrics.MetricsService;
import com.pedrodalben.bigbangessentials.holograms.placeholder.PlaceholderEngine;
import com.pedrodalben.bigbangessentials.holograms.service.HologramRegistry;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import com.pedrodalben.bigbangessentials.holograms.viewer.ViewerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RenderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderService.class);
    private static final byte TEXT_FLAG_SHADOW = 0x01;
    private static final byte TEXT_FLAG_SEE_THROUGH = 0x02;

    private final HologramRegistry registry;
    private final ViewerService viewerService;
    private final MetricsService metrics;
    private final PlaceholderEngine placeholderEngine;
    private final AnimationEngine animationEngine;
    private volatile HologramRenderer renderer;
    private volatile RendererHealth rendererHealth = RendererHealth.HEALTHY;
    private volatile boolean rendererLoggedError;

    public RenderService(HologramRegistry registry, ViewerService viewerService, MetricsService metrics,
                         PlaceholderEngine placeholderEngine, AnimationEngine animationEngine) {
        this.registry = registry;
        this.viewerService = viewerService;
        this.metrics = metrics;
        this.placeholderEngine = placeholderEngine;
        this.animationEngine = animationEngine;
        this.renderer = resolveRenderer();
    }

    private HologramRenderer resolveRenderer() {
        try {
            HologramRenderer r = new ClientOnlyTextDisplayRenderer();
            this.rendererHealth = RendererHealth.HEALTHY;
            LOGGER.info("Hologram renderer initialized: ClientOnlyTextDisplayRenderer");
            return r;
        } catch (Exception e) {
            if (!rendererLoggedError) {
                rendererLoggedError = true;
                LOGGER.error("Failed to create hologram renderer, hologram visualization disabled", e);
            }
            this.rendererHealth = RendererHealth.DEGRADED;
            metrics.incrementRendererErrors();
            return NoopHologramRenderer.getInstance();
        }
    }

    public HologramRenderer renderer() {
        return renderer;
    }

    public RendererHealth rendererHealth() {
        return rendererHealth;
    }

    public void showHologram(ServerPlayer player, HologramRegistry.ManagedHologram hologram) {
        RenderSnapshot snapshot = buildSnapshot(hologram, player, 0);
        renderer().show(player, snapshot);
        metrics.incrementSpawnPackets();
        viewerService.setFingerprint(player, hologram.definition().id(),
            RenderFingerprint.compute(snapshot, hologram.activePage()));
        BigBangHologramsManager.getInstance().fireOnShown(hologram.definition(), player);
    }

    public void updateHologram(ServerPlayer player, HologramRegistry.ManagedHologram hologram, long animationTick) {
        RenderSnapshot snapshot = buildSnapshot(hologram, player, animationTick);
        RenderFingerprint fingerprint = RenderFingerprint.compute(snapshot, hologram.activePage());

        if (viewerService.hasFingerprint(player, hologram.definition().id(), fingerprint)) {
            metrics.incrementFingerprintCacheHits();
            return;
        }
        metrics.incrementFingerprintCacheMisses();

        renderer().update(player, snapshot);
        metrics.incrementUpdatePackets();
        viewerService.setFingerprint(player, hologram.definition().id(), fingerprint);
    }

    public void hideHologram(ServerPlayer player, int entityId) {
        renderer().hide(player, entityId);
        metrics.incrementDestroyPackets();
    }

    public RenderSnapshot buildSnapshot(HologramRegistry.ManagedHologram hologram, ServerPlayer player, long animationTick) {
        int page = Math.min(Math.max(hologram.activePage(), 0), hologram.definition().pages().size() - 1);

        Integer override = viewerService.getCurrentPage(player, hologram.definition().id());
        if (override != null) {
            page = Math.min(Math.max(override, 0), hologram.definition().pages().size() - 1);
        }

        HologramDefinition def = hologram.definition();
        boolean disablePlaceholders = def.flags().contains(HologramFlag.DISABLE_PLACEHOLDERS) || def.flags().contains(HologramFlag.STATIC_CONTENT);
        boolean disableAnimations = def.flags().contains(HologramFlag.DISABLE_ANIMATIONS) || def.flags().contains(HologramFlag.STATIC_CONTENT);

        Component text;
        if (disablePlaceholders) {
            HologramPage pageData = def.pages().get(page);
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < pageData.lines().size(); i++) {
                if (i > 0) raw.append('\n');
                raw.append(pageData.lines().get(i).text() != null ? pageData.lines().get(i).text() : "");
            }
            text = com.pedrodalben.bigbangessentials.util.ChatComponentUtil.parseColorCodes(raw.toString());
        } else {
            PlaceholderEngine.ResolvedContent resolved = placeholderEngine.resolve(def, page, player);
            text = resolved.component();
        }

        String rawText = text.getString();
        if (!disableAnimations && animationEngine.hasAnimation(rawText)) {
            rawText = animationEngine.processAnimation(rawText, (int) animationTick, player.getUUID());
            text = Component.literal(rawText);
        }

        return new RenderSnapshot(
            hologram.entityId(),
            hologram.entityUuid(),
            def.location(),
            def.offsetX(),
            def.offsetY(),
            def.offsetZ(),
            text,
            def.lineWidth(),
            def.textOpacity(),
            def.backgroundColor(),
            textFlags(def),
            def.viewDistance(),
            def.billboard(),
            def.scale()
        );
    }

    private static byte textFlags(HologramDefinition definition) {
        byte flags = 0;
        if (definition.shadow() && !definition.flags().contains(HologramFlag.DISABLE_SHADOW)) {
            flags |= TEXT_FLAG_SHADOW;
        }
        if (definition.seeThrough()) {
            flags |= TEXT_FLAG_SEE_THROUGH;
        }
        return flags;
    }
}
