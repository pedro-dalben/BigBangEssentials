package com.pedrodalben.bigbangessentials.jobs.rewards;

import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class JobRewardNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobRewardNotificationService.class);
    private static final JobRewardNotificationService INSTANCE = new JobRewardNotificationService();
    private MinecraftServer server;

    public static JobRewardNotificationService getInstance() {
        return INSTANCE;
    }

    private JobRewardNotificationService() {}

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void notifyFragmentsGained(UUID playerUuid, long amount, long totalBalance) {
        ServerPlayer player = getPlayer(playerUuid);
        if (player == null) return;
        player.sendSystemMessage(MessageUtil.coloredText("<green>+<bold>" + amount + "</bold> Fragmento" + (amount > 1 ? "s" : "") + " de Jornada! <gray>(Total: <yellow>" + totalBalance + "<gray>)"));
    }

    public void notifyKeyFound(UUID playerUuid, String keyId) {
        notifyKeyFound(playerUuid, keyId, keyId);
    }

    public void notifyKeyFound(UUID playerUuid, String keyId, String keyDisplayName) {
        ServerPlayer player = getPlayer(playerUuid);
        if (player == null) return;
        if (keyDisplayName == null || keyDisplayName.isBlank()) {
            keyDisplayName = keyId;
        }
        player.sendSystemMessage(MessageUtil.coloredText(
                "<gold><bold>Sorte no Trabalho!</bold> <yellow>Você encontrou <bold>1x "
                + keyDisplayName + "</bold>!</yellow>"));
    }

    public void notifyKeyExchanged(UUID playerUuid, int amount, String keyId) {
        ServerPlayer player = getPlayer(playerUuid);
        if (player == null) return;
        String keyName = "craft_key".equalsIgnoreCase(keyId) ? "Chave do Ofício" : "Chave de Ascensão";
        player.sendSystemMessage(MessageUtil.coloredText("<green><bold>Conversão Concluída!</bold> <yellow>Você obteve <bold>" + amount + "x " + keyName + "</bold>!"));
    }

    private ServerPlayer getPlayer(UUID playerUuid) {
        if (server == null || playerUuid == null) return null;
        return server.getPlayerList().getPlayer(playerUuid);
    }
}
