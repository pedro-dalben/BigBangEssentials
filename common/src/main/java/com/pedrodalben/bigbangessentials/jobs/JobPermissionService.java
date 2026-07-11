package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public class JobPermissionService {
    private static final JobPermissionService INSTANCE = new JobPermissionService();

    private JobPermissionService() {}

    public static JobPermissionService getInstance() { return INSTANCE; }

    public boolean hasPermission(UUID playerUuid, String node) {
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        String resolved = resolveNode(cfg, node);
        if (PermissionAPI.hasPermission(playerUuid, resolved)) return true;

        Map<String, String> aliases = cfg != null ? cfg.global().legacyPermissionAliases : null;
        if (aliases != null && aliases.containsKey(node)) {
            return PermissionAPI.hasPermission(playerUuid, aliases.get(node));
        }
        if (cfg != null && node.startsWith(cfg.global().permissionPrefix)) return false;

        String legacyEquivalent = node.replace("bigbangessentials.jobs", "jobs");
        if (!legacyEquivalent.equals(node)) {
            return PermissionAPI.hasPermission(playerUuid, legacyEquivalent);
        }

        String canonicalEquivalent = node.replace("jobs.", "bigbangessentials.jobs.");
        if (!canonicalEquivalent.equals(node)) {
            return PermissionAPI.hasPermission(playerUuid, canonicalEquivalent);
        }

        return PermissionAPI.hasPermission(playerUuid, node);
    }

    private String resolveNode(JobsConfig cfg, String node) {
        if (cfg == null) return node;
        String prefix = cfg.global().permissionPrefix;
        if (node.startsWith(prefix)) return node;
        Map<String, String> aliases = cfg.global().legacyPermissionAliases;
        if (aliases != null && aliases.containsKey(node)) return aliases.get(node);
        return node;
    }

    public double getGanhosPermissionMultiplier(ServerPlayer player) {
        if (player == null) return 1.0;
        for (int i = 500; i >= 1; i--) {
            if (hasPermissionNode(player.getUUID(), "jobs.ganhos." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        for (int i = 500; i >= 1; i--) {
            if (hasPermissionNode(player.getUUID(), "bigbangessentials.jobs.bonus.earnings." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        return 1.0;
    }

    public double getXpPermissionMultiplier(ServerPlayer player) {
        if (player == null) return 1.0;
        for (int i = 500; i >= 1; i--) {
            if (hasPermissionNode(player.getUUID(), "jobs.xp." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        for (int i = 500; i >= 1; i--) {
            if (hasPermissionNode(player.getUUID(), "bigbangessentials.jobs.bonus.xp." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        return 1.0;
    }

    public double getDailyLimitPermissionMultiplier(ServerPlayer player) {
        if (player == null) return 1.0;
        for (int i = 500; i >= 1; i--) {
            if (hasPermissionNode(player.getUUID(), "jobs.limitediario." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        for (int i = 500; i >= 1; i--) {
            if (hasPermissionNode(player.getUUID(), "bigbangessentials.jobs.bonus.dailylimit." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        return 1.0;
    }

    public int getMaxActiveJobs(ServerPlayer player, int configMaxActiveJobs) {
        if (player == null) return configMaxActiveJobs;
        int permLimit = -1;
        for (int i = 5; i >= 1; i--) {
            if (hasPermissionNode(player.getUUID(), "jobs.limite." + i)) {
                permLimit = i;
                break;
            }
        }
        if (permLimit == -1) {
            for (int i = 5; i >= 1; i--) {
                if (hasPermissionNode(player.getUUID(), "bigbangessentials.jobs.bonus.slots." + i)) {
                    permLimit = i;
                    break;
                }
            }
        }
        return permLimit != -1 ? permLimit : configMaxActiveJobs;
    }

    public boolean hasJobPermission(UUID playerUuid, String permission) {
        return hasPermission(playerUuid, permission);
    }

    private boolean hasPermissionNode(UUID playerUuid, String node) {
        return PermissionAPI.hasPermission(playerUuid, node);
    }
}
