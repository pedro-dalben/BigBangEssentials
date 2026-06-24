package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public class JobPermissionService {
    private static final JobPermissionService INSTANCE = new JobPermissionService();

    private JobPermissionService() {}

    public static JobPermissionService getInstance() {
        return INSTANCE;
    }

    public double getGanhosPermissionMultiplier(ServerPlayer player) {
        if (player == null) return 1.0;
        for (int i = 500; i >= 1; i--) {
            if (PermissionAPI.hasPermission(player.getUUID(), "jobs.ganhos." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        return 1.0;
    }

    public double getXpPermissionMultiplier(ServerPlayer player) {
        if (player == null) return 1.0;
        for (int i = 500; i >= 1; i--) {
            if (PermissionAPI.hasPermission(player.getUUID(), "jobs.xp." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        return 1.0;
    }

    public double getDailyLimitPermissionMultiplier(ServerPlayer player) {
        if (player == null) return 1.0;
        for (int i = 500; i >= 1; i--) {
            if (PermissionAPI.hasPermission(player.getUUID(), "jobs.limitediario." + i)) {
                return 1.0 + (i / 100.0);
            }
        }
        return 1.0;
    }

    public int getMaxActiveJobs(ServerPlayer player, int configMaxActiveJobs) {
        if (player == null) return configMaxActiveJobs;
        int permLimit = -1;
        for (int i = 5; i >= 1; i--) {
            if (PermissionAPI.hasPermission(player.getUUID(), "jobs.limite." + i)) {
                permLimit = i;
                break;
            }
        }
        return permLimit != -1 ? permLimit : configMaxActiveJobs;
    }

    public boolean hasJobPermission(UUID playerUuid, String permission) {
        return PermissionAPI.hasPermission(playerUuid, permission);
    }
}
