package com.pedrodalben.bigbangessentials.economy.magnata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MagnataManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagnataManager.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final MagnataManager INSTANCE = new MagnataManager();

    public static MagnataManager getInstance() {
        return INSTANCE;
    }

    private final File configFile = ResourceUtil.getConfigFile("magnata.yml");
    private final File dataFile = ResourceUtil.getDataFile("magnata_history.json");

    private boolean enabled = true;
    private int checkIntervalSeconds = 300;

    private String newMagnataBroadcast = "&6[&bbigbangcraft&6] &e%player% &fé o novo magnata do servidor!";
    private String magnataJoinBroadcast = "&6[&bbigbangcraft&6] &fO Magnata &e%player% &flogou no servidor!";
    private String magnataCommandInfo = "&6[&bbigbangcraft&6] &fO Magnata atual é: &e%player% &fcom &a$%balance%&f!";
    private String noMagnataMessage = "&6[&bbigbangcraft&6] &cNenhum magnata definido ainda.";
    private String moduleDisabledMessage = "&cO módulo de Magnata está desativado.";

    private List<String> newMagnataCommands = new ArrayList<>();
    private List<String> oldMagnataCommands = new ArrayList<>();

    private UUID currentMagnataUuid = null;
    private String currentMagnataName = null;
    private BigDecimal currentMagnataBalance = BigDecimal.ZERO;

    private ScheduledExecutorService scheduler;
    private MinecraftServer server;

    private MagnataManager() {
        if (newMagnataCommands.isEmpty()) {
            newMagnataCommands.add("lp user %player% parent add magnata");
        }
        if (oldMagnataCommands.isEmpty()) {
            oldMagnataCommands.add("lp user %player% parent remove magnata");
        }
    }

    public synchronized void init(MinecraftServer server) {
        this.server = server;
        loadConfig();
        loadHistoryData();

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        if (enabled) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Magnata-Checker");
                t.setDaemon(true);
                return t;
            });

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    checkMagnata();
                } catch (Exception e) {
                    LOGGER.error("Error during Magnata periodic check", e);
                }
            }, 10, checkIntervalSeconds, TimeUnit.SECONDS);

            LOGGER.info("Magnata module initialized. Checking every {} seconds.", checkIntervalSeconds);
        } else {
            LOGGER.info("Magnata module is disabled.");
        }
    }

    public synchronized void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    public void loadConfig() {
        try {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            if (!configFile.exists()) {
                saveDefaultConfig();
                return;
            }

            Yaml yaml = new Yaml();
            try (FileReader reader = new FileReader(configFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data == null) return;

                if (data.containsKey("enabled")) {
                    this.enabled = Boolean.TRUE.equals(data.get("enabled"));
                }
                if (data.containsKey("check-interval-seconds")) {
                    this.checkIntervalSeconds = ((Number) data.get("check-interval-seconds")).intValue();
                }

                if (data.get("messages") instanceof Map<?, ?> msgs) {
                    if (msgs.containsKey("new-magnata-broadcast")) this.newMagnataBroadcast = String.valueOf(msgs.get("new-magnata-broadcast"));
                    if (msgs.containsKey("magnata-join-broadcast")) this.magnataJoinBroadcast = String.valueOf(msgs.get("magnata-join-broadcast"));
                    if (msgs.containsKey("magnata-command-info")) this.magnataCommandInfo = String.valueOf(msgs.get("magnata-command-info"));
                    if (msgs.containsKey("no-magnata")) this.noMagnataMessage = String.valueOf(msgs.get("no-magnata"));
                    if (msgs.containsKey("module-disabled")) this.moduleDisabledMessage = String.valueOf(msgs.get("module-disabled"));
                }

                if (data.get("commands") instanceof Map<?, ?> cmds) {
                    if (cmds.get("new-magnata") instanceof List<?> list) {
                        this.newMagnataCommands = list.stream().map(Object::toString).toList();
                    }
                    if (cmds.get("old-magnata") instanceof List<?> list) {
                        this.oldMagnataCommands = list.stream().map(Object::toString).toList();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load magnata.yml", e);
        }
    }

    private void saveDefaultConfig() {
        try (var in = getClass().getResourceAsStream("/default-config/bigbangessentials/magnata.yml")) {
            if (in != null) {
                Files.copy(in, configFile.toPath());
                LOGGER.info("Copied default magnata.yml from jar resources.");
                return;
            }
        } catch (Exception ignored) {
        }

        String defaultConfig = """
                # Configuration for Magnata module in BigBangEssentials
                enabled: true

                # Interval in seconds to check for new Magnata (300 seconds = 5 minutes)
                check-interval-seconds: 300

                messages:
                  new-magnata-broadcast: "&6[&bbigbangcraft&6] &e%player% &fé o novo magnata do servidor!"
                  magnata-join-broadcast: "&6[&bbigbangcraft&6] &fO Magnata &e%player% &flogou no servidor!"
                  magnata-command-info: "&6[&bbigbangcraft&6] &fO Magnata atual é: &e%player% &fcom &a$%balance%&f!"
                  no-magnata: "&6[&bbigbangcraft&6] &cNenhum magnata definido ainda."
                  module-disabled: "&cO módulo de Magnata está desativado."

                commands:
                  new-magnata:
                    - "lp user %player% parent add magnata"
                  old-magnata:
                    - "lp user %player% parent remove magnata"
                """;

        try {
            Files.writeString(configFile.toPath(), defaultConfig);
            LOGGER.info("Saved default magnata.yml configuration.");
        } catch (Exception e) {
            LOGGER.error("Failed to save default magnata.yml", e);
        }
    }

    private void loadHistoryData() {
        if (!dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            if (obj.has("uuid")) {
                this.currentMagnataUuid = UUID.fromString(obj.get("uuid").getAsString());
            }
            if (obj.has("name")) {
                this.currentMagnataName = obj.get("name").getAsString();
            }
            if (obj.has("balance")) {
                this.currentMagnataBalance = new BigDecimal(obj.get("balance").getAsString());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load magnata_history.json", e);
        }
    }

    private void saveHistoryData() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            JsonObject obj = new JsonObject();
            if (currentMagnataUuid != null) obj.addProperty("uuid", currentMagnataUuid.toString());
            if (currentMagnataName != null) obj.addProperty("name", currentMagnataName);
            if (currentMagnataBalance != null) obj.addProperty("balance", currentMagnataBalance.toPlainString());
            obj.addProperty("updatedAt", System.currentTimeMillis());

            try (FileWriter writer = new FileWriter(dataFile)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save magnata_history.json", e);
        }
    }

    public synchronized void checkMagnata() {
        if (!enabled || server == null) return;

        Map<UUID, BigDecimal> balances = EconomyManager.getInstance().getAllBalances();
        if (balances.isEmpty()) return;

        UUID topUuid = null;
        BigDecimal maxBalance = BigDecimal.ZERO;

        for (Map.Entry<UUID, BigDecimal> entry : balances.entrySet()) {
            if (entry.getValue().compareTo(maxBalance) > 0) {
                maxBalance = entry.getValue();
                topUuid = entry.getKey();
            }
        }

        if (topUuid == null) return;

        String topName = resolvePlayerName(topUuid);

        // If top player is the current Magnata, update balance and do nothing else
        if (currentMagnataUuid != null && currentMagnataUuid.equals(topUuid)) {
            this.currentMagnataBalance = maxBalance;
            this.currentMagnataName = topName;
            saveHistoryData();
            return;
        }

        // New Magnata detected!
        UUID oldUuid = this.currentMagnataUuid;
        String oldName = this.currentMagnataName;

        this.currentMagnataUuid = topUuid;
        this.currentMagnataName = topName;
        this.currentMagnataBalance = maxBalance;
        saveHistoryData();

        String formattedBalance = DECIMAL_FORMAT.format(maxBalance);

        // Broadcast new magnata
        String broadcast = newMagnataBroadcast
                .replace("%player%", topName)
                .replace("%balance%", formattedBalance);
        server.getPlayerList().broadcastSystemMessage(ChatComponentUtil.parseColorCodes(broadcast), false);

        // Run old-magnata commands
        if (oldName != null && !oldName.isEmpty()) {
            for (String cmd : oldMagnataCommands) {
                String runCmd = cmd.replace("%player%", oldName);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), runCmd);
            }
        }

        // Run new-magnata commands
        for (String cmd : newMagnataCommands) {
            String runCmd = cmd.replace("%player%", topName);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), runCmd);
        }
    }

    public void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        if (!enabled || currentMagnataUuid == null) return;

        if (player.getUUID().equals(currentMagnataUuid)) {
            this.currentMagnataName = player.getGameProfile().getName();
            saveHistoryData();

            String formattedBalance = DECIMAL_FORMAT.format(currentMagnataBalance);
            String broadcast = magnataJoinBroadcast
                    .replace("%player%", currentMagnataName)
                    .replace("%balance%", formattedBalance);
            server.getPlayerList().broadcastSystemMessage(ChatComponentUtil.parseColorCodes(broadcast), false);
        }
    }

    private String resolvePlayerName(UUID uuid) {
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                return online.getGameProfile().getName();
            }
        }
        if (uuid.equals(currentMagnataUuid) && currentMagnataName != null) {
            return currentMagnataName;
        }
        return uuid.toString();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UUID getCurrentMagnataUuid() {
        return currentMagnataUuid;
    }

    public String getCurrentMagnataName() {
        return currentMagnataName;
    }

    public BigDecimal getCurrentMagnataBalance() {
        return currentMagnataBalance;
    }

    public String getModuleDisabledMessage() {
        return moduleDisabledMessage;
    }

    public String getMagnataInfoMessage() {
        if (currentMagnataUuid == null || currentMagnataName == null) {
            return noMagnataMessage;
        }
        return magnataCommandInfo
                .replace("%player%", currentMagnataName)
                .replace("%balance%", DECIMAL_FORMAT.format(currentMagnataBalance));
    }
}
