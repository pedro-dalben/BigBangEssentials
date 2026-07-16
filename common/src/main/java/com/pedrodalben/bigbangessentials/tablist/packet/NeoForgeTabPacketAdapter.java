package com.pedrodalben.bigbangessentials.tablist.packet;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class NeoForgeTabPacketAdapter implements TabPacketAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeTabPacketAdapter.class);

    private final ScoreboardTeamAdapter teamAdapter = new ScoreboardTeamAdapter();

    @Override
    public void sendHeaderFooter(ServerPlayer viewer, Component header, Component footer) {
        viewer.connection.send(new ClientboundTabListPacket(header, footer));
    }

    @Override
    public void updateDisplayName(ServerPlayer viewer, UUID targetId, Component displayName) {
        ServerPlayer target = viewer.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return;

        try {
            TabListNameAccessor.set(target, displayName);
            viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(target)));
        } catch (Exception e) {
            LOGGER.error("Failed to send display name update for {} to viewer {}", target.getName().getString(), viewer.getName().getString(), e);
        }
    }

    @Override
    public void updateLatency(ServerPlayer viewer, UUID targetId, int ping) {
        ServerPlayer target = viewer.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return;

        // Set latency value on the server object so the UPDATE_LATENCY packet sends it.
        // We KEEP the value set — the Minecraft server heartbeat will overwrite with real ping next time.
        LatencyAccessor.set(target, ping);
        try {
            viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), List.of(target)));
        } catch (Exception e) {
            LOGGER.error("Failed to send latency update for {} to viewer {}", target.getName().getString(), viewer.getName().getString(), e);
        }
    }

    @Override
    public void updateListed(ServerPlayer viewer, UUID targetId, boolean listed) {
        ServerPlayer target = viewer.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return;

        if (listed) {
            addOrRestoreEntry(viewer, target);
        } else {
            removeEntry(viewer, targetId);
        }
    }

    @Override
    public void updateListOrder(ServerPlayer viewer, UUID targetId, int listOrder) {
        ServerPlayer target = viewer.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return;
        try {
            // 1.21.1 has no UPDATE_LIST_ORDER packet action. Re-add to refresh entry;
            // actual ordering is determined server-side by SortingFeature's sort.
            viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(targetId)));
            viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
        } catch (Exception e) {
            LOGGER.error("Failed to send list order update for {} to viewer {}", target.getName().getString(), viewer.getName().getString(), e);
        }
    }

    @Override
    public void removeEntry(ServerPlayer viewer, UUID targetId) {
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(targetId)));
    }

    @Override
    public void addOrRestoreEntry(ServerPlayer viewer, ServerPlayer target) {
        viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
    }

    @Override
    public void createOrUpdateTeam(ServerPlayer viewer, String teamName, Component prefix, Component suffix,
                                    String collisionRule, String nameTagVisibility, Component displayName,
                                    Collection<String> members) {
        teamAdapter.createOrUpdateTeam(viewer, teamName, prefix, suffix, collisionRule, nameTagVisibility, displayName, members, true);
    }

    @Override
    public void removeTeam(ServerPlayer viewer, String teamName) {
        teamAdapter.removeTeam(viewer, teamName);
    }

    @Override
    public void addMemberToTeam(ServerPlayer viewer, String teamName, String memberName) {
        teamAdapter.addMemberToTeam(viewer, teamName, memberName);
    }

    @Override
    public void removeMemberFromTeam(ServerPlayer viewer, String teamName, String memberName) {
        teamAdapter.removeMemberFromTeam(viewer, teamName, memberName);
    }

    @Override
    public void clearViewerTeams(ServerPlayer viewer) {
        teamAdapter.clearViewer(viewer);
    }

    @Override
    public void updateObjective(ServerPlayer viewer, String objectiveName, UUID targetId, int value, Component title) {
    }

    @Override
    public void initObjective(ServerPlayer viewer, String objectiveName, Component title, String criteriaType) {
    }

    /**
     * Sets the raw listOrder field on the target ServerPlayer via reflection.
     * This value is read by Minecraft when constructing ADD_PLAYER or
     * UPDATE_LIST_ORDER packets for <strong>new viewers</strong>.
     * Already-connected viewers see the order determined by the scoreboard
     * team name (collation-order hack), not this field.
     *
     * <p>If reflection fails the server logs once and silently skips — the
     * player list still works, just without custom ordering.</p>
     */
    @Override
    public void setListOrder(ServerPlayer target, int listOrder) {
        ListOrderAccessor.set(target, listOrder);
    }

    private static class ListOrderAccessor {
        private static final java.lang.reflect.Field LIST_ORDER_FIELD;
        private static boolean valid = false;
        private static boolean warned = false;

        static {
            java.lang.reflect.Field f = null;
            try {
                Class<?> clazz = ServerPlayer.class;
                while (clazz != null) {
                    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                        if (field.getType() == int.class) {
                            if (field.getName().equals("listOrder") || field.getName().equals("field_7072")) {
                                f = field;
                                break;
                            }
                        }
                    }
                    if (f != null) break;
                    clazz = clazz.getSuperclass();
                }
                if (f != null) {
                    f.setAccessible(true);
                    valid = true;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to find listOrder field in ServerPlayer", e);
            }
            LIST_ORDER_FIELD = f;
        }

        static void set(ServerPlayer target, int listOrder) {
            if (!valid || LIST_ORDER_FIELD == null) {
                if (!warned) {
                    LOGGER.warn("ListOrderAccessor not available (field 'listOrder' not found in ServerPlayer). Sorting may be limited to team-name ordering.");
                    warned = true;
                }
                return;
            }
            try {
                LIST_ORDER_FIELD.set(target, listOrder);
            } catch (Exception e) {
                LOGGER.error("Failed to set listOrder for {}", target.getName().getString(), e);
            }
        }
    }

    private static class TabListNameAccessor {
        private static final java.lang.reflect.Field TAB_LIST_NAME_FIELD;
        private static boolean validated = false;
        private static boolean valid = false;
        private static boolean warned = false;

        static {
            java.lang.reflect.Field f = null;
            try {
                Class<?> clazz = ServerPlayer.class;
                while (clazz != null) {
                    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                        if (field.getType() == Component.class) {
                            String name = field.getName();
                            if (name.equals("tabListDisplayName") || name.equals("listName") || name.equals("field_7115")) {
                                f = field;
                                break;
                            }
                        }
                    }
                    if (f != null) break;
                    clazz = clazz.getSuperclass();
                }
                if (f != null) {
                    f.setAccessible(true);
                    valid = true;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to find tabListDisplayName field in ServerPlayer", e);
            }
            TAB_LIST_NAME_FIELD = f;
            validated = true;
        }

        static void set(ServerPlayer target, Component name) {
            if (!valid || TAB_LIST_NAME_FIELD == null) {
                if (!warned) {
                    LOGGER.warn("TabListDisplayName reflection unavailable (field not found in ServerPlayer). Player list names will not be customized.");
                    warned = true;
                }
                return;
            }
            try {
                TAB_LIST_NAME_FIELD.set(target, name);
            } catch (Exception e) {
                LOGGER.error("Failed to set tabListDisplayName for {}", target.getName().getString(), e);
            }
        }
    }

    private static class LatencyAccessor {
        private static final java.lang.reflect.Field LATENCY_FIELD;
        private static boolean valid = false;
        private static boolean warned = false;

        static {
            java.lang.reflect.Field f = null;
            try {
                Class<?> clazz = ServerPlayer.class;
                while (clazz != null) {
                    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                        if (field.getType() == int.class) {
                            if (field.getName().equals("latency") || field.getName().equals("field_7114")) {
                                f = field;
                                break;
                            }
                        }
                    }
                    if (f != null) break;
                    clazz = clazz.getSuperclass();
                }
                if (f != null) {
                    f.setAccessible(true);
                    valid = true;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to find latency field in ServerPlayer", e);
            }
            LATENCY_FIELD = f;
        }

        static void set(ServerPlayer target, int latency) {
            if (!valid || LATENCY_FIELD == null) {
                if (!warned) {
                    LOGGER.warn("LatencyAccessor not available (field 'latency' not found in ServerPlayer). Ping column will show real latency only.");
                    warned = true;
                }
                return;
            }
            try {
                LATENCY_FIELD.set(target, latency);
            } catch (Exception e) {
                LOGGER.error("Failed to set latency for {}", target.getName().getString(), e);
            }
        }
    }
}
