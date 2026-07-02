package com.pedrodalben.bigbangessentials.rankup.config;

import com.google.gson.*;
import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class RankupConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private boolean enabled = true;
    private RankupLadder ladder = new RankupLadder("main", "&6Main Progression", "member",
            RankupPromotionMode.REPLACE_LADDER_INHERITANCE_AND_PRIMARY, true);
    private final Map<String, RankupRank> ranks = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public RankupLadder getLadder() { return ladder; }

    public Map<String, RankupRank> getRanks() {
        return Collections.unmodifiableMap(ranks);
    }

    public RankupRank getRank(String id) {
        return id != null ? ranks.get(id.toLowerCase()) : null;
    }

    public List<RankupRank> getOrderedRanks() {
        return ranks.values().stream()
                .sorted(Comparator.comparingInt(RankupRank::order))
                .toList();
    }

    public RankupRank getRankByOrder(int order) {
        return ranks.values().stream()
                .filter(r -> r.order() == order)
                .findFirst()
                .orElse(null);
    }

    public RankupRank getInitialRank() {
        return getRank(ladder.initialRankId());
    }

    public RankupRank getNextRank(RankupRank current) {
        if (current == null) return getInitialRank();
        return getRankByOrder(current.order() + 1);
    }

    public RankupRank getNextEnabledRank(RankupRank current) {
        RankupRank next = getNextRank(current);
        while (next != null && !next.enabled()) {
            next = getNextRank(next);
        }
        return next;
    }

    public boolean hasRank(String id) {
        return id != null && ranks.containsKey(id.toLowerCase());
    }

    public void addRank(RankupRank rank) {
        ranks.put(rank.id().toLowerCase(), rank);
    }

    public void removeRank(String id) {
        ranks.remove(id.toLowerCase());
    }

    public RankupConfig copy() {
        RankupConfig copy = new RankupConfig();
        copy.enabled = this.enabled;
        copy.ladder = this.ladder;
        for (RankupRank rank : this.ranks.values()) {
            copy.addRank(rank);
        }
        return copy;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema-version", 1);
        root.addProperty("enabled", enabled);

        JsonObject ladderObj = new JsonObject();
        ladderObj.addProperty("id", ladder.id());
        ladderObj.addProperty("display-name", ladder.displayName());
        ladderObj.addProperty("initial-rank-id", ladder.initialRankId());
        ladderObj.addProperty("luckperms-mode", ladder.luckPermsMode().name());
        ladderObj.addProperty("require-confirmation", ladder.requireConfirmation());
        root.add("ladder", ladderObj);

        JsonArray ranksArr = new JsonArray();
        List<RankupRank> ordered = getOrderedRanks();
        for (RankupRank rank : ordered) {
            ranksArr.add(rankToJson(rank));
        }
        root.add("ranks", ranksArr);
        return root;
    }

    public static RankupConfig loadAndValidate() throws Exception {
        ensureConfigExists();
        File file = ResourceUtil.getConfigFile("rankup.json");
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            return parseAndValidate(obj);
        }
    }

    public static RankupConfig parseAndValidate(JsonObject obj) {
        RankupConfig config = new RankupConfig();
        if (obj.has("enabled")) {
            config.enabled = obj.get("enabled").getAsBoolean();
        }

        if (obj.has("ladder")) {
            config.ladder = parseLadder(obj.getAsJsonObject("ladder"));
        }

        if (obj.has("ranks")) {
            JsonArray ranksArr = obj.getAsJsonArray("ranks");
            for (JsonElement el : ranksArr) {
                RankupRank rank = parseRank(el.getAsJsonObject());
                config.addRank(rank);
            }
        }

        RankupValidationResult validation = RankupConfigurationValidator.validate(config);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("RankUp configuration validation failed:\n- " + String.join("\n- ", validation.getErrors()));
        }
        for (String warning : validation.getWarnings()) {
            LOGGER.warn("RankUp config warning: {}", warning);
        }
        return config;
    }

    public static void save(RankupConfig config) throws IOException {
        ensureConfigExists();
        File file = ResourceUtil.getConfigFile("rankup.json");
        File backup = new File(file.getParentFile(), "rankup.json.bak");
        if (file.exists()) {
            Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(config.toJson(), writer);
        }
    }

    public static void ensureConfigExists() throws IOException {
        File file = ResourceUtil.getConfigFile("rankup.json");
        if (!file.exists()) {
            copyDefaultResource("/data/config/bigbangessentials/rankup.json", file);
        }
    }

    private static void copyDefaultResource(String resourcePath, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = RankupConfig.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                // Write a minimal default if resource missing
                Files.writeString(targetFile.toPath(), GSON.toJson(createDefaultConfig().toJson()), StandardCharsets.UTF_8);
                return;
            }
            Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Copied default RankUp config to: {}", targetFile.getPath());
        }
    }

    public static RankupConfig createDefaultConfig() {
        RankupConfig cfg = new RankupConfig();
        cfg.enabled = true;
        cfg.ladder = new RankupLadder("main", "&6Main Progression", "member",
                RankupPromotionMode.REPLACE_LADDER_INHERITANCE_AND_PRIMARY, true);

        RankupRank member = new RankupRank(
                "member", 0, "&7Member", List.of("&7Starting rank."),
                new RankupIcon("minecraft:wooden_sword"),
                new RankupLuckPermsSettings("member", true),
                new RankupRequirements(0.0, 0, RankupTaskMode.ALL, new ArrayList<>()),
                new RankupActions(null, new ArrayList<>()),
                true
        );

        RankupTask breakLogs = new RankupTask(
                "break_logs", "&6Wood Collector", List.of("&7Break 30 logs."),
                ObjectiveActionType.BREAK_BLOCK, 30,
                new RankupTaskFilter(List.of("#minecraft:logs"), null, null, null, null, null, null, null, null, null, null),
                true
        );

        RankupRank trainer = new RankupRank(
                "trainer", 1, "&aTrainer", List.of("&7Prove your worth."),
                new RankupIcon("minecraft:iron_sword"),
                new RankupLuckPermsSettings("trainer", true),
                new RankupRequirements(5000.0, 3, RankupTaskMode.ALL, List.of(breakLogs)),
                new RankupActions("&a%player% became a &fTrainer&a!", List.of("give %player% minecraft:diamond 3")),
                true
        );

        cfg.addRank(member);
        cfg.addRank(trainer);
        return cfg;
    }

    private static RankupLadder parseLadder(JsonObject obj) {
        String id = obj.has("id") ? obj.get("id").getAsString() : "main";
        String displayName = obj.has("display-name") ? obj.get("display-name").getAsString() : "&6Main Progression";
        String initialRankId = obj.has("initial-rank-id") ? obj.get("initial-rank-id").getAsString() : "";
        RankupPromotionMode mode = RankupPromotionMode.fromString(
                obj.has("luckperms-mode") ? obj.get("luckperms-mode").getAsString() : null);
        boolean requireConfirmation = !obj.has("require-confirmation") || obj.get("require-confirmation").getAsBoolean();
        return new RankupLadder(id, displayName, initialRankId, mode, requireConfirmation);
    }

    private static RankupRank parseRank(JsonObject obj) {
        String id = obj.get("id").getAsString();
        int order = obj.has("order") ? obj.get("order").getAsInt() : 0;
        String displayName = obj.has("display-name") ? obj.get("display-name").getAsString() : id;
        List<String> description = parseStringList(obj, "description");
        RankupIcon icon = parseIcon(obj.has("icon") ? obj.getAsJsonObject("icon") : null);
        RankupLuckPermsSettings luckPerms = parseLuckPerms(obj.has("luckperms") ? obj.getAsJsonObject("luckperms") : null);
        RankupRequirements requirements = parseRequirements(obj.has("requirements") ? obj.getAsJsonObject("requirements") : null);
        RankupActions actions = parseActions(obj.has("actions") ? obj.getAsJsonObject("actions") : null);
        boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
        return new RankupRank(id, order, displayName, description, icon, luckPerms, requirements, actions, enabled);
    }

    private static RankupIcon parseIcon(JsonObject obj) {
        if (obj == null) return new RankupIcon("minecraft:paper");
        String item = obj.has("item") ? obj.get("item").getAsString() : "minecraft:paper";
        int cmd = obj.has("custom-model-data") ? obj.get("custom-model-data").getAsInt() : 0;
        return new RankupIcon(item, cmd);
    }

    private static RankupLuckPermsSettings parseLuckPerms(JsonObject obj) {
        if (obj == null) return new RankupLuckPermsSettings("", true);
        String group = obj.has("group") ? obj.get("group").getAsString() : "";
        boolean primary = obj.has("set-as-primary-group") && obj.get("set-as-primary-group").getAsBoolean();
        RankupPromotionMode mode = RankupPromotionMode.fromString(
                obj.has("mode") ? obj.get("mode").getAsString() : null);
        return new RankupLuckPermsSettings(group, primary, mode);
    }

    private static RankupRequirements parseRequirements(JsonObject obj) {
        if (obj == null) return new RankupRequirements(0.0, 0, RankupTaskMode.ALL, new ArrayList<>());
        double money = obj.has("money") ? obj.get("money").getAsDouble() : 0.0;
        int gems = obj.has("gems") ? obj.get("gems").getAsInt() : 0;
        RankupTaskMode taskMode = RankupTaskMode.fromString(
                obj.has("task-mode") ? obj.get("task-mode").getAsString() : null);
        List<RankupTask> tasks = new ArrayList<>();
        if (obj.has("tasks")) {
            JsonArray arr = obj.getAsJsonArray("tasks");
            for (JsonElement el : arr) {
                tasks.add(parseTask(el.getAsJsonObject()));
            }
        }
        return new RankupRequirements(money, gems, taskMode, tasks);
    }

    private static RankupTask parseTask(JsonObject obj) {
        String id = obj.get("id").getAsString();
        String displayName = obj.has("display-name") ? obj.get("display-name").getAsString() : id;
        List<String> description = parseStringList(obj, "description");
        ObjectiveActionType type = ObjectiveActionType.fromString(
                obj.has("type") ? obj.get("type").getAsString() : "");
        int target = obj.has("target") ? obj.get("target").getAsInt() : 0;
        RankupTaskFilter filters = parseFilters(obj.has("filters") ? obj.getAsJsonObject("filters") : null);
        boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
        return new RankupTask(id, displayName, description, type, target, filters, enabled);
    }

    private static RankupTaskFilter parseFilters(JsonObject obj) {
        if (obj == null) return new RankupTaskFilter();
        return new RankupTaskFilter(
                parseStringList(obj, "blocks"),
                parseStringList(obj, "items"),
                parseStringList(obj, "entities"),
                parseStringList(obj, "biomes"),
                parseStringList(obj, "advancements"),
                parseStringList(obj, "species"),
                parseStringList(obj, "types"),
                obj.has("legendary") ? obj.get("legendary").getAsBoolean() : null,
                obj.has("shiny") ? obj.get("shiny").getAsBoolean() : null,
                obj.has("fish-only") ? obj.get("fish-only").getAsBoolean() : null,
                obj.has("boss-only") ? obj.get("boss-only").getAsBoolean() : null
        );
    }

    private static RankupActions parseActions(JsonObject obj) {
        if (obj == null) return new RankupActions(null, new ArrayList<>());
        String broadcast = obj.has("broadcast") ? obj.get("broadcast").getAsString() : null;
        List<String> commands = parseStringList(obj, "commands");
        return new RankupActions(broadcast, commands);
    }

    private static List<String> parseStringList(JsonObject obj, String key) {
        if (!obj.has(key)) return new ArrayList<>();
        JsonElement el = obj.get(key);
        if (el.isJsonArray()) {
            List<String> list = new ArrayList<>();
            for (JsonElement item : el.getAsJsonArray()) {
                list.add(item.getAsString());
            }
            return list;
        }
        if (el.isJsonPrimitive()) {
            return new ArrayList<>(List.of(el.getAsString()));
        }
        return new ArrayList<>();
    }

    private static JsonObject rankToJson(RankupRank rank) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", rank.id());
        obj.addProperty("order", rank.order());
        obj.addProperty("display-name", rank.displayName());
        obj.add("description", toJsonArray(rank.description()));
        obj.addProperty("enabled", rank.enabled());

        JsonObject iconObj = new JsonObject();
        iconObj.addProperty("item", rank.icon().item());
        if (rank.icon().customModelData() != 0) {
            iconObj.addProperty("custom-model-data", rank.icon().customModelData());
        }
        obj.add("icon", iconObj);

        JsonObject lpObj = new JsonObject();
        lpObj.addProperty("group", rank.luckPerms().group());
        lpObj.addProperty("set-as-primary-group", rank.luckPerms().setAsPrimaryGroup());
        lpObj.addProperty("mode", rank.luckPerms().mode().name());
        obj.add("luckperms", lpObj);

        JsonObject reqObj = new JsonObject();
        reqObj.addProperty("money", rank.requirements().money());
        reqObj.addProperty("gems", rank.requirements().gems());
        reqObj.addProperty("task-mode", rank.requirements().taskMode().name());
        JsonArray tasksArr = new JsonArray();
        for (RankupTask task : rank.requirements().tasks()) {
            tasksArr.add(taskToJson(task));
        }
        reqObj.add("tasks", tasksArr);
        obj.add("requirements", reqObj);

        JsonObject actionsObj = new JsonObject();
        if (rank.actions().broadcast() != null) {
            actionsObj.addProperty("broadcast", rank.actions().broadcast());
        }
        actionsObj.add("commands", toJsonArray(rank.actions().commands()));
        obj.add("actions", actionsObj);

        return obj;
    }

    private static JsonObject taskToJson(RankupTask task) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", task.id());
        obj.addProperty("display-name", task.displayName());
        obj.add("description", toJsonArray(task.description()));
        obj.addProperty("type", task.type().configName());
        obj.addProperty("target", task.target());
        obj.addProperty("enabled", task.enabled());

        JsonObject filtersObj = new JsonObject();
        addFilterList(filtersObj, "blocks", task.filters().blocks());
        addFilterList(filtersObj, "items", task.filters().items());
        addFilterList(filtersObj, "entities", task.filters().entities());
        addFilterList(filtersObj, "biomes", task.filters().biomes());
        addFilterList(filtersObj, "advancements", task.filters().advancements());
        addFilterList(filtersObj, "species", task.filters().species());
        addFilterList(filtersObj, "types", task.filters().types());
        addNullableBoolean(filtersObj, "legendary", task.filters().legendary());
        addNullableBoolean(filtersObj, "shiny", task.filters().shiny());
        addNullableBoolean(filtersObj, "fish-only", task.filters().fishOnly());
        addNullableBoolean(filtersObj, "boss-only", task.filters().bossOnly());
        obj.add("filters", filtersObj);

        return obj;
    }

    private static void addFilterList(JsonObject obj, String key, List<String> list) {
        if (list != null && !list.isEmpty()) {
            obj.add(key, toJsonArray(list));
        }
    }

    private static void addNullableBoolean(JsonObject obj, String key, Boolean value) {
        if (value != null) {
            obj.addProperty(key, value);
        }
    }

    private static JsonArray toJsonArray(List<String> list) {
        JsonArray arr = new JsonArray();
        for (String s : list) arr.add(s);
        return arr;
    }
}
