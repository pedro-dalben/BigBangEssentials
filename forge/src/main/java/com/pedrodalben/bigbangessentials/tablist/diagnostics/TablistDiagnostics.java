package com.pedrodalben.bigbangessentials.tablist.diagnostics;

import com.pedrodalben.bigbangessentials.tablist.TablistModule;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class TablistDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistDiagnostics.class);

    public static void runDiagnostics(ServerPlayer initiator) {
        TablistModule module = TablistModule.getInstance();
        if (module == null || !module.isEnabled()) {
            initiator.sendSystemMessage(Component.literal("\u00a7cTablistModule is not running!"));
            return;
        }

        int online = initiator.getServer().getPlayerList().getPlayers().size();
        int states = 0;
        int backlog = 0;
        int renderedStates = 0;
        if (module.getCoordinator() != null) {
            for (UUID uuid : initiator.getServer().getPlayerList().getPlayers().stream().map(p -> p.getUUID()).toList()) {
                TabPlayerState state = module.getCoordinator().getPlayerState(uuid);
                if (state != null) states++;
            }
            backlog = module.getCoordinator().getBacklogSize();
            renderedStates = module.getCoordinator().getPlayerStatesCount();
        }

        initiator.sendSystemMessage(Component.literal(String.format(
                "\u00a76Tablist Diagnostics:\n" +
                "\u00a77  Online: \u00a7f%d\n" +
                "\u00a77  States tracked: \u00a7f%d\n" +
                "\u00a77  Rendered state count: \u00a7f%d\n" +
                "\u00a77  Pending backlog: \u00a7f%d\n" +
                "\u00a77  Module status: \u00a7%s",
                online, states, renderedStates, backlog,
                module.isEnabled() ? "aEnabled" : "cDisabled"
        )));

        // Warn about stale states (tracked but not online)
        if (states > online) {
            initiator.sendSystemMessage(Component.literal(
                    "\u00a7e\u26a0 Tracked states > online players. Possible stale entries from previous reload."
            ));
        }
        // Warn about growing backlog
        if (backlog > 50) {
            initiator.sendSystemMessage(Component.literal(
                    "\u00a7c\u26a0 High packet backlog (" + backlog + "). Consider increasing maxPacketUpdatesPerTick."
            ));
        }
    }
}
