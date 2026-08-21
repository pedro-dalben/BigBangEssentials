package com.pedrodalben.bigbangessentials.tablist.feature.sorting;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.feature.TablistFeature;
import com.pedrodalben.bigbangessentials.tablist.packet.TabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.state.RenderedTabState;
import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class SortingFeature implements TablistFeature {
    private boolean enabled = true;
    private final List<String> rules = new ArrayList<>();
    private List<String> groupPriority = new ArrayList<>();

    @Override
    public void loadConfig(TablistConfig config) {
        enabled = config.tablist.sorting.enabled;
        rules.clear();
        rules.addAll(config.tablist.sorting.rules);
        
        groupPriority.clear();
        for (String rule : rules) {
            if (rule.startsWith("GROUP_PRIORITY:")) {
                String groupsStr = rule.substring("GROUP_PRIORITY:".length());
                for (String g : groupsStr.split(",")) {
                    groupPriority.add(g.trim().toLowerCase());
                }
            }
        }
    }

    @Override
    public void tick(MinecraftServer server, TabAnimationRegistry animationRegistry) {}

    @Override
    public void updatePlayer(ServerPlayer player, TabPlayerState state, RenderedTabState renderedState, 
                             TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        // Handled in coordination with NameTagFeature
    }
    
    public String generateSortString(ServerPlayer target, TabPlayerState targetState) {
        if (!enabled) return "z";
        
        StringBuilder sortString = new StringBuilder();
        
        for (String rule : rules) {
            if (rule.startsWith("GROUP_PRIORITY:")) {
                String group = targetState.getPrimaryGroup().toLowerCase();
                int idx = groupPriority.indexOf(group);
                if (idx == -1) idx = 99;
                sortString.append((char) ('a' + Math.min(idx, 25)));
            } else if (rule.equals("AFK_LAST")) {
                sortString.append(targetState.isAfk() ? "z" : "a");
            } else if (rule.equals("NAME_ASC")) {
                sortString.append(target.getName().getString().toLowerCase());
            } else if (rule.equals("NAME_DESC")) {
                String name = target.getName().getString().toLowerCase();
                if (!name.isEmpty()) {
                    sortString.append((char) (122 - (name.charAt(0) - 'a')));
                }
            }
        }
        
        String result = sortString.toString();
        // Use deterministic suffix from UUID to guarantee uniqueness (avoid hash collision).
        String uuidSuffix = targetState.getUuid().toString().replace("-", "").substring(0, 8);
        // Ensure result is short enough that final name fits in 16 chars
        int maxSortLen = 14 - uuidSuffix.length(); // 14 = 16 - len("_") - safety
        if (maxSortLen < 0) maxSortLen = 0;
        if (result.length() > maxSortLen) {
            result = result.substring(0, maxSortLen);
        }
        String finalName = result + "_" + uuidSuffix;
        if (finalName.length() > 16) {
            finalName = finalName.substring(0, 16);
        }
        return finalName;
    }

    public int generateListOrder(TabPlayerState targetState) {
        if (!enabled) return 9999;
        int order = 0;
        for (String rule : rules) {
            if (rule.startsWith("GROUP_PRIORITY:")) {
                String group = targetState.getPrimaryGroup().toLowerCase();
                int idx = groupPriority.indexOf(group);
                if (idx == -1) idx = 99;
                order = (order * 100) + idx;
            } else if (rule.equals("AFK_LAST")) {
                order = (order * 10) + (targetState.isAfk() ? 9 : 0);
            } else if (rule.equals("NAME_ASC")) {
                order = (order * 1000) + targetState.getRealName().hashCode();
            }
        }
        return order;
    }
}
