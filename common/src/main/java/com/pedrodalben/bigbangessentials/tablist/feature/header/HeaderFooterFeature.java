package com.pedrodalben.bigbangessentials.tablist.feature.header;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.feature.TablistFeature;
import com.pedrodalben.bigbangessentials.tablist.packet.TabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.CompiledTabTemplate;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.render.TabConditionEngine;
import com.pedrodalben.bigbangessentials.tablist.state.RenderedTabState;
import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.tablist.TablistModule;

import java.util.*;

public class HeaderFooterFeature implements TablistFeature {
    private boolean enabled = true;
    private final List<HeaderFooterDesign> designs = new ArrayList<>();
    private HeaderFooterDesign defaultDesign = null;
    
    // Track animation frames for header/footer so we can trigger re-render when they change
    private final Map<String, String> lastAnimationFrames = new HashMap<>();
    private boolean animationsScanned = false;

    @Override
    public void loadConfig(TablistConfig config) {
        enabled = config.tablist.headerFooter.enabled;
        designs.clear();
        defaultDesign = null;

        for (TablistConfig.DesignSection section : config.tablist.headerFooter.designs) {
            HeaderFooterDesign design = new HeaderFooterDesign(section);
            designs.add(design);
            if (design.isDefault() && defaultDesign == null) {
                defaultDesign = design;
            }
        }
        
        // Sort by priority descending
        designs.sort(Comparator.comparingInt(HeaderFooterDesign::getPriority).reversed());
        lastAnimationFrames.clear();
        animationsScanned = false;
    }

    @Override
    public void tick(MinecraftServer server, TabAnimationRegistry animationRegistry) {
        if (!enabled) return;
        // Detect animation frame changes and mark HEADER_FOOTER dirty for all players.
        // This ensures animated headers/footers re-render on every frame advance.
        boolean frameChanged = false;
        for (Map.Entry<String, String> entry : lastAnimationFrames.entrySet()) {
            String currentFrame = animationRegistry.getCurrentFrame(entry.getKey());
            if (!currentFrame.equals(entry.getValue())) {
                entry.setValue(currentFrame);
                frameChanged = true;
            }
        }
        // On first tick, collect all animation dependencies from designs
        if (!animationsScanned) {
            for (HeaderFooterDesign design : designs) {
                for (CompiledTabTemplate t : design.getHeader()) {
                    for (String animId : t.getAnimationDependencies()) {
                        lastAnimationFrames.put(animId, animationRegistry.getCurrentFrame(animId));
                    }
                }
                for (CompiledTabTemplate t : design.getFooter()) {
                    for (String animId : t.getAnimationDependencies()) {
                        lastAnimationFrames.put(animId, animationRegistry.getCurrentFrame(animId));
                    }
                }
            }
            animationsScanned = true;
            frameChanged = false; // Don't trigger on initial collection
        }
        if (frameChanged && TablistModule.getInstance() != null) {
            TablistModule.getInstance().invalidateAll(
                com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason.WORLD_CHANGED);
        }
    }

    @Override
    public void updatePlayer(ServerPlayer player, TabPlayerState state, RenderedTabState renderedState, 
                             TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        if (!enabled) return;
        if (!state.hasDirtyFlag(TabDirtyFlag.HEADER_FOOTER)) return;

        HeaderFooterDesign activeDesign = null;
        for (HeaderFooterDesign design : designs) {
            if (TabConditionEngine.evaluate(design.getCondition(), player, state)) {
                activeDesign = design;
                break;
            }
        }

        if (activeDesign == null) {
            activeDesign = defaultDesign;
        }

        if (activeDesign == null) return;

        StringBuilder headerText = new StringBuilder();
        List<CompiledTabTemplate> headerTemplates = activeDesign.getHeader();
        for (int i = 0; i < headerTemplates.size(); i++) {
            headerText.append(headerTemplates.get(i).render(player, animationRegistry));
            if (i < headerTemplates.size() - 1) headerText.append("\n");
        }

        StringBuilder footerText = new StringBuilder();
        List<CompiledTabTemplate> footerTemplates = activeDesign.getFooter();
        for (int i = 0; i < footerTemplates.size(); i++) {
            footerText.append(footerTemplates.get(i).render(player, animationRegistry));
            if (i < footerTemplates.size() - 1) footerText.append("\n");
        }

        Component headerComp = Component.literal(headerText.toString());
        Component footerComp = Component.literal(footerText.toString());

        if (renderedState.hasHeaderFooterChanged(headerComp, footerComp)) {
            packetAdapter.sendHeaderFooter(player, headerComp, footerComp);
            renderedState.setLastHeader(headerComp);
            renderedState.setLastFooter(footerComp);
        }
    }
}
