package com.pedrodalben.bigbangessentials.jobs.config;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.*;
import com.pedrodalben.bigbangessentials.jobs.rewards.CrateRewardDefinition;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotDefinition;
import com.pedrodalben.bigbangessentials.jobs.progression.RankMilestoneDefinition;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseObjective;
import com.pedrodalben.bigbangessentials.jobs.JobConfigurationValidator;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JobsConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsConfigLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BACKUP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Set<String> PROFESSION_IDS = Set.of(
            "miner", "woodcutter", "farmer", "builder", "blacksmith", "crafter",
            "explorer", "ranger", "culinarian", "magician", "fisherman",
            "researcher", "breeder", "trainer", "pasture_keeper", "paleontologist", "raider");

    // Minecraft's server config lives with the world. The old config/ path is
    // kept only as an input for the one-time migration below.
    private static final String CANONICAL_DIR = ResourceUtil.CONFIG_DIR + "jobs";
    private static final String LEGACY_DIR = ResourceUtil.LEGACY_CONFIG_DIR + "jobs";

    private static Path canonicalDir;
    private static Path professionsDir;

    public static synchronized void init() {
        canonicalDir = Path.of(CANONICAL_DIR);
        professionsDir = canonicalDir.resolve("professions");
    }

    public static JobsConfig loadAndValidate() throws Exception {
        init();
        migrateIfNeeded();
        ensureDirectories();

        GlobalConfig global = loadGlobal();
        Map<String, JobSlotDefinition> slots = loadSlots();
        Map<String, RankMilestoneDefinition> milestones = loadMilestones();
        Map<String, JobDefinition> professions = loadProfessions();

        validateAll(global, professions, slots, milestones);

        return JobsConfig.builder()
                .global(global)
                .addAllProfessions(professions)
                .addAllSlots(slots)
                .addAllMilestones(milestones)
                .build();
    }

    private static void migrateIfNeeded() {
        Path legacy = Path.of(LEGACY_DIR);
        try {
            ensureDirectories();
            migrateNewerFlatOverrides(Path.of(CANONICAL_DIR));
            if (canonicalConfigsAlreadyExist()) return;

            Path source = findMigrationSource(legacy);
            if (source == null) return;

            Path backupDir = Path.of(CANONICAL_DIR + "_backup_" + BACKUP_FMT.format(LocalDateTime.now()));
            if (Files.exists(canonicalDir) && hasJsonFiles(canonicalDir)) {
                copyDir(canonicalDir, backupDir);
                LOGGER.info("Jobs config backup created at {}", backupDir);
            }
            copyProfessionFiles(source);
            copySharedConfigFiles(source.getParent());
            if (Files.exists(legacy)) {
                Files.writeString(legacy.resolve(".migrated"),
                        "migrated_to=" + canonicalDir.toAbsolutePath(), StandardCharsets.UTF_8);
            }
            LOGGER.info("Migrated jobs configuration from {} to {}", source, canonicalDir);
        } catch (Exception e) {
            LOGGER.warn("Jobs configuration migration from {} failed: {}", legacy, e.getMessage());
        }
    }

    private static Path findMigrationSource(Path legacy) throws IOException {
        if (hasJsonFiles(professionsDir)) return professionsDir;
        Path canonicalFlat = canonicalDir;
        if (hasProfessionFiles(canonicalFlat)) return canonicalFlat;
        if (hasJsonFiles(legacy.resolve("professions"))) return legacy.resolve("professions");
        if (hasProfessionFiles(legacy)) return legacy;
        return null;
    }

    private static void migrateNewerFlatOverrides(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        for (String professionId : PROFESSION_IDS) {
            Path flat = dir.resolve(professionId + ".json");
            Path nested = professionsDir.resolve(professionId + ".json");
            if (Files.isRegularFile(flat) && Files.isRegularFile(nested)
                    && Files.getLastModifiedTime(flat).compareTo(Files.getLastModifiedTime(nested)) > 0) {
                Files.copy(flat, nested, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Migrated newer flat profession config {} to {}", flat, nested);
            }
        }
    }

    private static void copyProfessionFiles(Path source) throws IOException {
        Files.createDirectories(professionsDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source, "*.json")) {
            for (Path file : stream) {
                String id = file.getFileName().toString();
                String professionId = id.substring(0, id.length() - ".json".length()).toLowerCase(Locale.ROOT);
                if (!PROFESSION_IDS.contains(professionId)) continue;
                Files.copy(file, professionsDir.resolve(id), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void copySharedConfigFiles(Path sourceDir) throws IOException {
        for (String name : List.of("global.json", "slots.json", "milestones.json")) {
            Path source = sourceDir.resolve(name);
            Path target = canonicalDir.resolve(name);
            if (Files.exists(source) && !Files.exists(target)) {
                Files.copy(source, target);
            }
        }
    }

    private static boolean hasJsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            return stream.iterator().hasNext();
        }
    }

    private static boolean hasProfessionFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (!Set.of("global.json", "slots.json", "milestones.json").contains(name)) return true;
            }
        }
        return false;
    }

    private static boolean canonicalConfigsAlreadyExist() {
        if (!Files.exists(professionsDir)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(professionsDir, "*.json")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private static void ensureDirectories() throws IOException {
        Files.createDirectories(canonicalDir);
        Files.createDirectories(professionsDir);
    }

    private static GlobalConfig loadGlobal() throws IOException {
        Path file = canonicalDir.resolve("global.json");
        if (!Files.exists(file)) {
            writeDefaultGlobal(file);
            LOGGER.info("Created default global config: {}", file);
        }
        JsonObject root = readJson(file);
        int schemaVersion = getInt(root, "schema-version", 3);
        JsonObject dl = getObject(root, "daily-limit");
        JsonObject afk = getObject(root, "afk-prevention");
        JsonObject perms = getObject(root, "permissions");

        GlobalConfig.Builder b = GlobalConfig.builder()
                .schemaVersion(schemaVersion)
                .dailyLimitGlobal(getDouble(dl, "global-limit", 50000.0))
                .dailyLimitEnabled(getBool(dl, "enabled", true))
                .dailyLimitTimezone(getString(dl, "timezone", "America/Sao_Paulo"))
                .dailyLimitResetTime(getString(dl, "reset-time", "00:00"))
                .maxActiveJobs(getInt(root, "max-active-jobs", 2))
                .maxInProgressLicenses(getInt(root, "max-in-progress-licenses", 1))
                .preventEarningsWhileAfk(getBool(afk, "prevent-earnings-while-afk", true))
                .preventXpWhileAfk(getBool(afk, "prevent-xp-while-afk", true))
                .continueXpAfterLimit(getBool(afk, "continue-xp-after-limit", false))
                .switchCooldownMinutes(getInt(root, "switch-cooldown-minutes", 30))
                .permissionPrefix(getString(perms, "prefix", "bigbangessentials.jobs"));

        if (perms.has("legacy-aliases")) {
            JsonObject aliases = perms.getAsJsonObject("legacy-aliases");
            for (Map.Entry<String, JsonElement> e : aliases.entrySet()) {
                b.legacyPermissionAlias(e.getKey(), e.getValue().getAsString());
            }
        }
        return b.build();
    }

    private static Map<String, JobSlotDefinition> loadSlots() throws IOException {
        Path file = canonicalDir.resolve("slots.json");
        if (!Files.exists(file)) {
            writeDefaultSlots(file);
            LOGGER.info("Created default slots config: {}", file);
        }
        JsonObject root = readJson(file);
        JsonObject slotsObj = getObject(root, "slots");
        Map<String, JobSlotDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : slotsObj.entrySet()) {
            JsonObject s = e.getValue().getAsJsonObject();
            result.put(e.getKey(), new JobSlotDefinition(
                    getString(s, "slot-type", e.getKey()),
                    getString(s, "category", "COMMON"),
                    getInt(s, "cooldown-minutes", 30)
            ));
        }
        return result;
    }

    private static Map<String, RankMilestoneDefinition> loadMilestones() throws IOException {
        Path file = canonicalDir.resolve("milestones.json");
        if (!Files.exists(file)) {
            writeDefaultMilestones(file);
            LOGGER.info("Created default milestones config: {}", file);
        }
        JsonObject root = readJson(file);
        JsonObject milestonesObj = getObject(root, "milestones");
        Map<String, RankMilestoneDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : milestonesObj.entrySet()) {
            JsonObject m = e.getValue().getAsJsonObject();
            List<String> unlockedSlots = jsonArrayToList(m.getAsJsonArray("unlocked-slots"));
            List<String> eligibleJobs = jsonArrayToList(m.getAsJsonArray("eligible-jobs"));
            result.put(e.getKey(), new RankMilestoneDefinition(
                    getString(m, "id", e.getKey()),
                    getString(m, "display-name", e.getKey()),
                    getString(m, "required-rank-id", ""),
                    getInt(m, "required-rank-order", 1),
                    unlockedSlots,
                    eligibleJobs
            ));
        }
        return result;
    }

    private static Map<String, JobDefinition> loadProfessions() throws IOException {
        Map<String, JobDefinition> result = new LinkedHashMap<>();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(professionsDir, "*.json")) {
            stream.forEach(files::add);
        } catch (IOException ignored) {
            // Directory may not exist yet; default files will be created on first write
        }

        String[] defaultIds = PROFESSION_IDS.toArray(String[]::new);

        if (files.isEmpty()) {
            for (String id : defaultIds) {
                Path p = professionsDir.resolve(id + ".json");
                writeDefaultProfession(p, id);
                LOGGER.info("Created default profession config: {}", p);
                files.add(p);
            }
        } else {
            for (String id : defaultIds) {
                Path p = professionsDir.resolve(id + ".json");
                if (!Files.exists(p)) {
                    writeDefaultProfession(p, id);
                    LOGGER.info("Created missing default profession config: {}", p);
                    files.add(p);
                }
            }
        }

        Set<String> seenIds = new HashSet<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            try {
                JsonObject root = readJson(file);
                JobDefinition job = parseProfession(root, fileName);
                String fileId = fileName.replace(".json", "");
                if (!job.id.equalsIgnoreCase(fileId)) {
                    LOGGER.warn("Skipping {}: job id '{}' does not match filename (expected '{}')",
                            file.toAbsolutePath(), job.id, fileId);
                    continue;
                }
                if (seenIds.contains(job.id.toLowerCase())) {
                    LOGGER.warn("Skipping {}: duplicate job id '{}'", file.toAbsolutePath(), job.id);
                    continue;
                }
                seenIds.add(job.id.toLowerCase());
                JobConfigurationValidator.validateJob(job, fileName);
                result.put(job.id.toLowerCase(), job);
                LOGGER.info("Loaded profession '{}' from {} ({} action groups)",
                        job.id, file.toAbsolutePath(), job.actions.size());
            } catch (Exception e) {
                LOGGER.warn("Skipping {}: {}", file.toAbsolutePath(), e.getMessage());
            }
        }
        return result;
    }

    private static JobDefinition parseProfession(JsonObject root, String filename) {
        String id = requireNonEmpty(root, "id", filename);
        JobDefinition.Builder b = JobDefinition.builder(id)
                .enabled(getBool(root, "enabled", true))
                .displayName(requireNonEmpty(root, "display-name", filename))
                .shortDescription(getString(root, "short-description", ""))
                .description(getString(root, "description", ""))
                .icon(requireNonEmpty(root, "icon", filename))
                .category(requireNonEmpty(root, "category", filename))
                .sortOrder(getInt(root, "sort-order", 99))
                .permission(getString(root, "permission", "bigbangessentials.jobs.profession." + id))
                .visibleWithoutPermission(getBool(root, "visible-without-permission", true))
                .unlockedByDefault(getBool(root, "unlocked-by-default", true))
                .licenseRequired(getBool(root, "license-required", false))
                .requiredIntegration(getStringOrNull(root, "required-integration"))
                .maxLevel(getInt(root, "max-level", 100))
                .maxDailyEarnings(getDouble(root, "max-daily-earnings", -1))
                .moneyBonusPerLevel(getDouble(root, "money-bonus-per-level", 0.5))
                .maxLevelMoneyBonus(getDouble(root, "max-level-money-bonus", 50.0))
                .skillPointsEvery(getInt(root, "skill-points-every", 2))
                .resetProgressOnLeave(getBool(root, "reset-progress-on-leave", false));

        if (root.has("xp-curve")) {
            JsonObject xc = root.getAsJsonObject("xp-curve");
            b.xpCurve(new XpCurve(
                    getString(xc, "type", "polynomial"),
                    getDouble(xc, "base", 100.0),
                    getDouble(xc, "multiplier", 1.0),
                    getDouble(xc, "exponent", 1.5)
            ));
        }

        b.actions(parseActions(root));
        b.skills(parseSkills(root));
        b.messages(parseMessages(root));
        b.levelUpRewards(parseLevelUpRewards(root));
        b.howToEarn(parseHowToEarn(root));
        b.licenseObjectives(parseLicenseObjectives(root));
        b.crateRewards(parseCrateRewards(root));
        b.unlockRequirements(parseUnlockRequirements(root));
        b.visibility(parseVisibility(root));

        return b.build();
    }

    private static Map<String, Map<String, ActionReward>> parseActions(JsonObject root) {
        Map<String, Map<String, ActionReward>> result = new LinkedHashMap<>();
        if (!root.has("actions")) return result;
        JsonObject actionsObj = root.getAsJsonObject("actions");
        for (Map.Entry<String, JsonElement> actEntry : actionsObj.entrySet()) {
            Map<String, ActionReward> targets = new LinkedHashMap<>();
            JsonObject targetsObj = actEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> tEntry : targetsObj.entrySet()) {
                String targetId = tEntry.getKey();
                if (targetId.equals("*")) {
                    LOGGER.warn("Skipping wildcard '*' in action '{}' — use 'default-reward' instead.", actEntry.getKey());
                    continue;
                }
                JsonObject rewardObj = tEntry.getValue().getAsJsonObject();
                double money = rewardObj.has("money") ? rewardObj.get("money").getAsDouble() : 0;
                double xp = rewardObj.has("xp") ? rewardObj.get("xp").getAsDouble() : 0;
                double chance = rewardObj.has("chance") ? rewardObj.get("chance").getAsDouble() : 1.0;
                targets.put(targetId, new ActionReward(money, xp, chance));
            }
            JobActionType actionType = JobActionType.fromString(actEntry.getKey());
            String actionKey = actionType != null
                    ? actionType.getConfigKeys().get(0)
                    : actEntry.getKey().toUpperCase(Locale.ROOT).replace('_', '-');
            result.put(actionKey, targets);
        }
        return result;
    }

    private static Map<String, SkillDefinition> parseSkills(JsonObject root) {
        Map<String, SkillDefinition> result = new LinkedHashMap<>();
        if (!root.has("skills")) return result;
        JsonObject skillsObj = root.getAsJsonObject("skills");
        for (Map.Entry<String, JsonElement> entry : skillsObj.entrySet()) {
            JsonObject s = entry.getValue().getAsJsonObject();
            List<String> prereqs = jsonArrayToList(s.getAsJsonArray("prerequisites"));
            Map<String, Double> effects = new LinkedHashMap<>();
            if (s.has("effects")) {
                JsonObject effObj = s.getAsJsonObject("effects");
                for (Map.Entry<String, JsonElement> e : effObj.entrySet()) {
                    effects.put(e.getKey(), e.getValue().getAsDouble());
                }
            }
            result.put(entry.getKey(), new SkillDefinition(
                    getString(s, "id", entry.getKey()),
                    getString(s, "name", entry.getKey()),
                    getString(s, "description", ""),
                    getInt(s, "max-level", 5),
                    getInt(s, "max-rank", 1),
                    getInt(s, "point-cost", 1),
                    prereqs,
                    effects,
                    getInt(s, "required-level", 1)
            ));
        }
        return result;
    }

    private static Map<String, String> parseMessages(JsonObject root) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!root.has("messages")) return result;
        JsonObject msgs = root.getAsJsonObject("messages");
        for (Map.Entry<String, JsonElement> e : msgs.entrySet()) {
            result.put(e.getKey(), e.getValue().getAsString());
        }
        return result;
    }

    private static Map<Integer, List<String>> parseLevelUpRewards(JsonObject root) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        if (!root.has("level-up-rewards")) return result;
        JsonObject rewards = root.getAsJsonObject("level-up-rewards");
        for (Map.Entry<String, JsonElement> e : rewards.entrySet()) {
            int level = Integer.parseInt(e.getKey());
            result.put(level, jsonArrayToList(e.getValue().getAsJsonArray()));
        }
        return result;
    }

    private static HowToEarn parseHowToEarn(JsonObject root) {
        if (!root.has("how-to-earn")) return HowToEarn.empty();
        JsonObject hte = root.getAsJsonObject("how-to-earn");
        return new HowToEarn(
                getString(hte, "money-header", null),
                getString(hte, "xp-header", null),
                jsonArrayToList(hte.getAsJsonArray("money-lines")),
                jsonArrayToList(hte.getAsJsonArray("xp-lines")),
                jsonArrayToList(hte.getAsJsonArray("example-targets"))
        );
    }

    private static List<JobLicenseObjective> parseLicenseObjectives(JsonObject root) {
        List<JobLicenseObjective> result = new ArrayList<>();
        if (!root.has("license-objectives")) return result;
        JsonArray arr = root.getAsJsonArray("license-objectives");
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            result.add(new JobLicenseObjective(
                    getString(obj, "objective-id", UUID.randomUUID().toString()),
                    getString(obj, "action-type", "BREAK-BLOCK"),
                    getInt(obj, "required-amount", 100),
                    0,
                    Optional.empty(),
                    jsonArrayToList(obj.getAsJsonArray("match-tags")),
                    jsonArrayToList(obj.getAsJsonArray("match-target-ids")),
                    getBool(obj, "require-non-player-placed", false),
                    getBool(obj, "require-mature", false),
                    getString(obj, "progress-message", "")
            ));
        }
        return result;
    }

    private static List<CrateRewardDefinition> parseCrateRewards(JsonObject root) {
        List<CrateRewardDefinition> result = new ArrayList<>();
        if (!root.has("crate-rewards")) return result;
        JsonArray arr = root.getAsJsonArray("crate-rewards");
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            List<String> actions = jsonArrayToList(obj.getAsJsonArray("actions"));
            String keyId = getString(obj, "key-id", "craft_key");
            String keyDisplayName = getString(obj, "key-display-name", keyId);
            double chance = getDouble(obj, "chance", 0.005);
            int amount = getInt(obj, "amount", 1);
            int minimumJobLevel = getInt(obj, "minimum-job-level", 1);
            String requiredRankId = getStringOrNull(obj, "required-rank-id");
            int dailyLimit = getInt(obj, "daily-limit", 3);
            long cooldownSeconds = getLong(obj, "cooldown-seconds", 1800L);
            int priority = getInt(obj, "priority", 0);
            boolean oneRewardPerAction = getBool(obj, "one-reward-per-action", false);
            boolean physicalKey = getBool(obj, "physical-key", false);

            CrateRewardDefinition.Builder b = CrateRewardDefinition.builder()
                    .actions(actions)
                    .keyId(keyId)
                    .keyDisplayName(keyDisplayName)
                    .chance(chance)
                    .amount(amount)
                    .minimumJobLevel(minimumJobLevel)
                    .dailyLimit(dailyLimit)
                    .cooldownSeconds(cooldownSeconds)
                    .priority(priority)
                    .oneRewardPerAction(oneRewardPerAction)
                    .physicalKey(physicalKey);
            if (requiredRankId != null) {
                b.requiredRankId(requiredRankId);
            }
            result.add(b.build());
        }
        return result;
    }

    private static UnlockRequirements parseUnlockRequirements(JsonObject root) {
        if (!root.has("unlock-requirements")) return UnlockRequirements.DEFAULT;
        JsonObject obj = root.getAsJsonObject("unlock-requirements");
        boolean unlockedByDefault = getBool(obj, "unlocked-by-default", true);
        String requiredRankId = getStringOrNull(obj, "required-rank-id");
        int requiredRankOrder = getInt(obj, "required-rank-order", 0);
        String permission = getStringOrNull(obj, "permission");
        return new UnlockRequirements(unlockedByDefault, requiredRankId, requiredRankOrder, permission);
    }

    private static VisibilityConfig parseVisibility(JsonObject root) {
        if (!root.has("visibility")) return VisibilityConfig.ALWAYS_VISIBLE;
        JsonObject obj = root.getAsJsonObject("visibility");
        String modeStr = getString(obj, "mode", "ALWAYS_VISIBLE");
        VisibilityMode mode = VisibilityMode.fromString(modeStr);
        boolean showReqs = getBool(obj, "show-requirements-when-locked", true);
        boolean allowPreview = getBool(obj, "allow-preview", true);
        return new VisibilityConfig(mode, showReqs, allowPreview);
    }

    private static void validateAll(GlobalConfig global, Map<String, JobDefinition> professions,
                                     Map<String, JobSlotDefinition> slots,
                                     Map<String, RankMilestoneDefinition> milestones) {
        if (global.schemaVersion < 3)
            LOGGER.warn("global.json: schema-version is {} (< 3). New features will use defaults. Please update to schema-version 3.", global.schemaVersion);
        if (professions.isEmpty())
            throw new IllegalArgumentException("No professions loaded");
        if (slots.isEmpty())
            throw new IllegalArgumentException("No slots configured");

        for (JobDefinition job : professions.values()) {
            for (Map.Entry<String, Map<String, JobsConfig.ActionReward>> entry : job.actions.entrySet()) {
                String actionKey = entry.getKey();
                Map<String, JobsConfig.ActionReward> targets = entry.getValue();
                if (targets.containsKey("*") && isEconomicActionType(actionKey)) {
                    LOGGER.warn("professions/{}.json: wildcard '*' in economic action '{}' is ignored. Use 'default-reward' instead.",
                            job.id, actionKey);
                }
            }
        }

        for (JobDefinition job : professions.values()) {
            if (job.displayName == null || job.displayName.isBlank())
                throw new IllegalArgumentException("professions/" + job.id + ".json: display-name is empty");
            if (job.category == null || job.category.isBlank())
                throw new IllegalArgumentException("professions/" + job.id + ".json: category is empty");
            String cat = job.category.toUpperCase();
            if (!cat.equals("COMMON") && !cat.equals("POKEMON_SPECIALIZATION"))
                throw new IllegalArgumentException("professions/" + job.id + ".json: unknown category '" + job.category + "'");
            if (job.maxLevel < 1)
                throw new IllegalArgumentException("professions/" + job.id + ".json: max-level must be >= 1");
            if (job.moneyBonusPerLevel < 0)
                throw new IllegalArgumentException("professions/" + job.id + ".json: money-bonus-per-level cannot be negative");
            if (job.maxLevelMoneyBonus < 0)
                throw new IllegalArgumentException("professions/" + job.id + ".json: max-level-money-bonus cannot be negative");
            if (job.requiredIntegration != null && !job.requiredIntegration.isBlank()) {
                LOGGER.info("Profession '{}' requires integration '{}'",
                        job.id, job.requiredIntegration);
            }
        }

        for (RankMilestoneDefinition m : milestones.values()) {
            for (String jobId : m.eligibleJobs()) {
                if (!professions.containsKey(jobId.toLowerCase())) {
                    LOGGER.warn("milestones.json: milestone '{}' references non-existent job '{}'. Skipping this reference.",
                            m.id(), jobId);
                }
            }
            for (String slotType : m.unlockedSlots()) {
                if (!slots.containsKey(slotType)) {
                    LOGGER.warn("milestones.json: milestone '{}' references non-existent slot '{}'. Skipping this reference.",
                            m.id(), slotType);
                }
            }
        }
    }

    private static boolean isEconomicActionType(String actionKey) {
        if (actionKey == null) return false;
        String upper = actionKey.toUpperCase(Locale.ROOT).replace('-', '_');
        return List.of("BREAK_BLOCK", "HARVEST_CROP", "KILL_ENTITY", "FISH", "CRAFT_ITEM",
                "SMELT_ITEM", "FISH_CATCH", "CRAFT_RECIPE", "SMELT_RECIPE", "PLACE_BLOCK",
                "PLACE_PROJECT_BLOCK").contains(upper);
    }

    public static void createBackup() {
        try {
            Path backupDir = Path.of(CANONICAL_DIR + "_backup_" + BACKUP_FMT.format(LocalDateTime.now()));
            if (Files.exists(canonicalDir)) {
                copyDir(canonicalDir, backupDir);
                LOGGER.info("Config backup created at {}", backupDir);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create config backup", e);
        }
    }

    public static String getConfigDirPath() { return CANONICAL_DIR; }

    private static JsonObject readJson(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) throw new IOException("Empty file: " + file);
        try {
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON in " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static void writeDefaultGlobal(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String content = "{\n" +
            "  \"schema-version\": 3,\n" +
            "  \"daily-limit\": {\n" +
            "    \"enabled\": true,\n" +
            "    \"global-limit\": 50000.0,\n" +
            "    \"timezone\": \"America/Sao_Paulo\",\n" +
            "    \"reset-time\": \"00:00\"\n" +
            "  },\n" +
            "  \"max-active-jobs\": 2,\n" +
            "  \"max-in-progress-licenses\": 1,\n" +
            "  \"switch-cooldown-minutes\": 30,\n" +
            "  \"afk-prevention\": {\n" +
            "    \"prevent-earnings-while-afk\": true,\n" +
            "    \"prevent-xp-while-afk\": true,\n" +
            "    \"continue-xp-after-limit\": false\n" +
            "  },\n" +
            "  \"permissions\": {\n" +
            "    \"prefix\": \"bigbangessentials.jobs\",\n" +
            "    \"legacy-aliases\": {\n" +
            "      \"jobs.command.jobs\": \"bigbangessentials.jobs.command.menu\",\n" +
            "      \"jobs.command.list\": \"bigbangessentials.jobs.command.list\",\n" +
            "      \"jobs.command.entrar\": \"bigbangessentials.jobs.command.join\",\n" +
            "      \"jobs.command.sair\": \"bigbangessentials.jobs.command.leave\",\n" +
            "      \"jobs.command.info\": \"bigbangessentials.jobs.command.info\",\n" +
            "      \"jobs.command.ganhos\": \"bigbangessentials.jobs.command.earnings\",\n" +
            "      \"jobs.command.habilidades\": \"bigbangessentials.jobs.command.skills\",\n" +
            "      \"jobs.command.top\": \"bigbangessentials.jobs.command.top\",\n" +
            "      \"jobs.command.license\": \"bigbangessentials.jobs.command.license\",\n" +
            "      \"jobs.command.slot\": \"bigbangessentials.jobs.command.slot\",\n" +
            "      \"jobs.ganhos.*\": \"bigbangessentials.jobs.bonus.earnings\",\n" +
            "      \"jobs.xp.*\": \"bigbangessentials.jobs.bonus.xp\",\n" +
            "      \"jobs.limitediario.*\": \"bigbangessentials.jobs.bonus.dailylimit\",\n" +
            "      \"jobs.limite.*\": \"bigbangessentials.jobs.bonus.slots\",\n" +
            "      \"jobs.admin.*\": \"bigbangessentials.jobs.admin\",\n" +
            "      \"jobs.profissao.*\": \"bigbangessentials.jobs.profession\"\n" +
            "    }\n" +
            "  }\n" +
            "}";
        Files.writeString(file, content);
    }

    private static void writeDefaultSlots(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String content = "{\n" +
            "  \"schema-version\": 2,\n" +
            "  \"slots\": {\n" +
            "    \"COMMON_PRIMARY\": {\"slot-type\":\"COMMON_PRIMARY\",\"display-name\":\"Profissão Primária\",\"category\":\"COMMON\",\"cooldown-minutes\":30},\n" +
            "    \"COMMON_SECONDARY\": {\"slot-type\":\"COMMON_SECONDARY\",\"display-name\":\"Profissão Secundária\",\"category\":\"COMMON\",\"cooldown-minutes\":60},\n" +
            "    \"POKEMON_SPECIALIZATION\": {\"slot-type\":\"POKEMON_SPECIALIZATION\",\"display-name\":\"Especialização Pokémon\",\"category\":\"POKEMON_SPECIALIZATION\",\"cooldown-minutes\":120}\n" +
            "  }\n" +
            "}";
        Files.writeString(file, content);
    }

    private static void writeDefaultMilestones(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String content = "{\n" +
            "  \"schema-version\": 2,\n" +
            "  \"milestones\": {\n" +
            "    \"novice\": {\"id\":\"novice\",\"display-name\":\"Novato\",\"required-rank-id\":\"novice\",\"required-rank-order\":1,\"unlocked-slots\":[\"COMMON_PRIMARY\"],\"eligible-jobs\":[\"miner\",\"woodcutter\",\"farmer\",\"builder\",\"blacksmith\",\"crafter\",\"explorer\",\"ranger\",\"culinarian\",\"magician\",\"fisherman\"]},\n" +
            "    \"veteran\": {\"id\":\"veteran\",\"display-name\":\"Veterano\",\"required-rank-id\":\"veteran\",\"required-rank-order\":2,\"unlocked-slots\":[\"COMMON_SECONDARY\"],\"eligible-jobs\":[\"miner\",\"woodcutter\",\"farmer\",\"builder\",\"blacksmith\",\"crafter\",\"explorer\",\"ranger\",\"culinarian\",\"magician\",\"fisherman\"]},\n" +
            "    \"adept\": {\"id\":\"adept\",\"display-name\":\"Adepto\",\"required-rank-id\":\"adept\",\"required-rank-order\":3,\"unlocked-slots\":[\"POKEMON_SPECIALIZATION\"],\"eligible-jobs\":[\"researcher\",\"breeder\",\"trainer\",\"pasture_keeper\",\"paleontologist\",\"raider\"]}\n" +
            "  }\n" +
            "}";
        Files.writeString(file, content);
    }

    private static void writeDefaultProfession(Path file, String id) throws IOException {
        Files.createDirectories(file.getParent());
        String content = buildDefaultProfessionJson(id);
        Files.writeString(file, content);
    }

    private static String buildDefaultProfessionJson(String id) {
        switch (id) {
            case "miner":
                return buildMinerJson();
            case "woodcutter":
                return buildWoodcutterJson();
            case "farmer":
                return buildFarmerJson();
            case "builder":
                return buildBuilderJson();
            case "blacksmith":
                return buildBlacksmithJson();
            case "crafter":
                return buildCrafterJson();
            case "explorer":
                return buildExplorerJson();
            case "ranger":
                return buildRangerJson();
            case "culinarian":
                return buildCulinarianJson();
            case "magician":
                return buildMagicianJson();
            case "fisherman":
                return buildFishermanJson();
            case "researcher":
                return buildResearcherJson();
            case "breeder":
                return buildBreederJson();
            case "trainer":
                return buildTrainerJson();
            case "pasture_keeper":
                return buildPastureKeeperJson();
            case "paleontologist":
                return buildPaleontologistJson();
            case "raider":
                return buildRaiderJson();
            default:
                return "{}";
        }
    }

    private static String buildMinerJson() {
        return "{\n  \"id\":\"miner\",\"enabled\":true,\"display-name\":\"Minerador\",\n" +
            "  \"short-description\":\"Extraia minérios e pedras preciosas\",\n" +
            "  \"description\":\"Profissão dedicada à mineração e extração de recursos minerais.\",\n" +
            "  \"icon\":\"minecraft:diamond_pickaxe\",\"category\":\"COMMON\",\"sort-order\":1,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.miner\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"BREAK-BLOCK\":{\"minecraft:coal_ore\":{\"money\":5,\"xp\":10},\"minecraft:copper_ore\":{\"money\":7,\"xp\":12},\"minecraft:iron_ore\":{\"money\":10,\"xp\":15},\"minecraft:gold_ore\":{\"money\":15,\"xp\":20},\"minecraft:redstone_ore\":{\"money\":12,\"xp\":18},\"minecraft:lapis_ore\":{\"money\":12,\"xp\":18},\"minecraft:diamond_ore\":{\"money\":30,\"xp\":40},\"minecraft:emerald_ore\":{\"money\":35,\"xp\":45},\"minecraft:nether_quartz_ore\":{\"money\":10,\"xp\":15},\"minecraft:nether_gold_ore\":{\"money\":12,\"xp\":18},\"minecraft:ancient_debris\":{\"money\":100,\"xp\":150},\"minecraft:stone\":{\"money\":1,\"xp\":2},\"minecraft:deepslate\":{\"money\":1.5,\"xp\":2.5}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Quebre minérios naturais para receber dinheiro.\",\"Minérios raros como Diamante, Esmeralda e Netherita pagam mais.\"],\"xp-lines\":[\"Todo bloco quebrado que paga dinheiro também concede XP.\"],\"example-targets\":[\"minecraft:diamond_ore\",\"minecraft:coal_ore\",\"minecraft:iron_ore\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Minerador!\",\"leave\":\"&cVocê deixou a profissão de Minerador.\",\"level-up\":\"&aParabéns! Nível %level% de Minerador! +%points% pontos\"}\n}";
    }

    private static String buildWoodcutterJson() {
        return "{\n  \"id\":\"woodcutter\",\"enabled\":true,\"display-name\":\"Lenhador\",\n" +
            "  \"short-description\":\"Corte árvores e colete madeira\",\n" +
            "  \"description\":\"Profissão dedicada ao corte de madeira e coleta de recursos florestais.\",\n" +
            "  \"icon\":\"minecraft:diamond_axe\",\"category\":\"COMMON\",\"sort-order\":2,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.woodcutter\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"BREAK-BLOCK\":{\"minecraft:oak_log\":{\"money\":5,\"xp\":10},\"minecraft:spruce_log\":{\"money\":5,\"xp\":10},\"minecraft:birch_log\":{\"money\":5,\"xp\":10},\"minecraft:jungle_log\":{\"money\":5,\"xp\":10},\"minecraft:acacia_log\":{\"money\":5,\"xp\":10},\"minecraft:dark_oak_log\":{\"money\":5,\"xp\":10},\"minecraft:mangrove_log\":{\"money\":5,\"xp\":10},\"minecraft:cherry_log\":{\"money\":6,\"xp\":11},\"minecraft:crimson_stem\":{\"money\":7,\"xp\":14},\"minecraft:warped_stem\":{\"money\":7,\"xp\":14}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Quebre troncos de árvores naturais.\",\"Madeiras do Nether (Carmesim e Distorcida) pagam mais.\"],\"xp-lines\":[\"Cada tronco quebrado concede XP.\"],\"example-targets\":[\"minecraft:oak_log\",\"minecraft:spruce_log\",\"minecraft:crimson_stem\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Lenhador!\",\"leave\":\"&cVocê deixou a profissão de Lenhador.\",\"level-up\":\"&aParabéns! Nível %level% de Lenhador! +%points% pontos\"}\n}";
    }

    private static String buildFarmerJson() {
        return "{\n  \"id\":\"farmer\",\"enabled\":true,\"display-name\":\"Fazendeiro\",\n" +
            "  \"short-description\":\"Plante, colha e cuide de animais\",\n" +
            "  \"description\":\"Profissão dedicada à agricultura e pecuária.\",\n" +
            "  \"icon\":\"minecraft:diamond_hoe\",\"category\":\"COMMON\",\"sort-order\":3,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.farmer\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"HARVEST-CROP\":{\"minecraft:wheat\":{\"money\":3,\"xp\":5},\"minecraft:potatoes\":{\"money\":3,\"xp\":5},\"minecraft:carrots\":{\"money\":3,\"xp\":5},\"minecraft:beetroots\":{\"money\":3,\"xp\":5},\"minecraft:nether_wart\":{\"money\":8,\"xp\":12},\"minecraft:pumpkin\":{\"money\":4,\"xp\":6},\"minecraft:melon\":{\"money\":4,\"xp\":6}},\"KILL-ENTITY\":{\"minecraft:cow\":{\"money\":5,\"xp\":10},\"minecraft:pig\":{\"money\":5,\"xp\":10},\"minecraft:sheep\":{\"money\":5,\"xp\":10},\"minecraft:chicken\":{\"money\":4,\"xp\":8},\"minecraft:rabbit\":{\"money\":4,\"xp\":8}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Colha plantações maduras.\",\"Abata animais de fazenda para obter recursos.\"],\"xp-lines\":[\"Cada colheita ou abate concede XP.\"],\"example-targets\":[\"minecraft:wheat\",\"minecraft:carrots\",\"minecraft:cow\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Fazendeiro!\",\"leave\":\"&cVocê deixou a profissão de Fazendeiro.\",\"level-up\":\"&aParabéns! Nível %level% de Fazendeiro! +%points% pontos\"}\n}";
    }

    private static String buildBuilderJson() {
        return "{\n  \"id\":\"builder\",\"enabled\":true,\"display-name\":\"Construtor\",\n" +
            "  \"short-description\":\"Construa estruturas e projetos\",\n" +
            "  \"description\":\"Profissão dedicada à construção e colocação de blocos decorativos e estruturais.\",\n" +
            "  \"icon\":\"minecraft:bricks\",\"category\":\"COMMON\",\"sort-order\":4,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.builder\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"PLACE-BLOCK\":{\"minecraft:stone_bricks\":{\"money\":2,\"xp\":4},\"minecraft:bricks\":{\"money\":2,\"xp\":4},\"minecraft:oak_planks\":{\"money\":1,\"xp\":2},\"minecraft:polished_andesite\":{\"money\":3,\"xp\":5},\"minecraft:polished_granite\":{\"money\":3,\"xp\":5},\"minecraft:polished_diorite\":{\"money\":3,\"xp\":5},\"minecraft:glass\":{\"money\":2,\"xp\":3},\"minecraft:terracotta\":{\"money\":2,\"xp\":4},\"minecraft:concrete\":{\"money\":2,\"xp\":4}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Coloque blocos decorativos e de construção.\",\"Blocos mais elaborados pagam mais.\"],\"xp-lines\":[\"Cada bloco colocado concede XP.\"],\"example-targets\":[\"minecraft:stone_bricks\",\"minecraft:polished_andesite\",\"minecraft:concrete\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Construtor!\",\"leave\":\"&cVocê deixou a profissão de Construtor.\",\"level-up\":\"&aParabéns! Nível %level% de Construtor! +%points% pontos\"}\n}";
    }

    private static String buildBlacksmithJson() {
        return "{\n  \"id\":\"blacksmith\",\"enabled\":true,\"display-name\":\"Ferreiro\",\n" +
            "  \"short-description\":\"Fundição de metais e criação de equipamentos\",\n" +
            "  \"description\":\"Profissão dedicada à fundição de minérios e criação de itens metálicos.\",\n" +
            "  \"icon\":\"minecraft:anvil\",\"category\":\"COMMON\",\"sort-order\":5,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.blacksmith\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"SMELT-ITEM\":{\"minecraft:iron_ingot\":{\"money\":3,\"xp\":6},\"minecraft:gold_ingot\":{\"money\":5,\"xp\":10},\"minecraft:copper_ingot\":{\"money\":2,\"xp\":4},\"minecraft:netherite_ingot\":{\"money\":50,\"xp\":100}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Fundição de minérios em fornalhas ou alto-fornos.\",\"Netherita paga muito mais que metais comuns.\"],\"xp-lines\":[\"Cada item fundido concede XP.\"],\"example-targets\":[\"minecraft:iron_ingot\",\"minecraft:gold_ingot\",\"minecraft:netherite_ingot\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Ferreiro!\",\"leave\":\"&cVocê deixou a profissão de Ferreiro.\",\"level-up\":\"&aParabéns! Nível %level% de Ferreiro! +%points% pontos\"}\n}";
    }

    private static String buildCrafterJson() {
        return "{\n  \"id\":\"crafter\",\"enabled\":true,\"display-name\":\"Artesão\",\n" +
            "  \"short-description\":\"Crie itens, blocos e decorações\",\n" +
            "  \"description\":\"Profissão dedicada à criação de itens via crafting.\",\n" +
            "  \"icon\":\"minecraft:crafting_table\",\"category\":\"COMMON\",\"sort-order\":6,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.crafter\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"CRAFT-ITEM\":{\"minecraft:chest\":{\"money\":3,\"xp\":5},\"minecraft:furnace\":{\"money\":4,\"xp\":8},\"minecraft:bookshelf\":{\"money\":5,\"xp\":10},\"minecraft:enchanting_table\":{\"money\":15,\"xp\":30},\"minecraft:beacon\":{\"money\":200,\"xp\":500}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Crie itens usando a mesa de trabalho.\",\"Itens mais complexos pagam mais.\"],\"xp-lines\":[\"Cada item craftado concede XP.\"],\"example-targets\":[\"minecraft:chest\",\"minecraft:bookshelf\",\"minecraft:beacon\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Artesão!\",\"leave\":\"&cVocê deixou a profissão de Artesão.\",\"level-up\":\"&aParabéns! Nível %level% de Artesão! +%points% pontos\"}\n}";
    }

    private static String buildExplorerJson() {
        return "{\n  \"id\":\"explorer\",\"enabled\":true,\"display-name\":\"Explorador\",\n" +
            "  \"short-description\":\"Explore biomas, estruturas e dimensões\",\n" +
            "  \"description\":\"Profissão dedicada à exploração do mundo e descoberta de novos locais.\",\n" +
            "  \"icon\":\"minecraft:compass\",\"category\":\"COMMON\",\"sort-order\":7,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.explorer\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"EXPLORE\":{\"minecraft:plains\":{\"money\":10,\"xp\":20},\"minecraft:desert\":{\"money\":10,\"xp\":20},\"minecraft:jungle\":{\"money\":15,\"xp\":25},\"minecraft:mushroom_fields\":{\"money\":25,\"xp\":50},\"minecraft:badlands\":{\"money\":15,\"xp\":25},\"minecraft:deep_dark\":{\"money\":50,\"xp\":100},\"default-reward\":{\"money\":5,\"xp\":10}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Descubra novos biomas e estruturas.\",\"Biomas raros como Deep Dark pagam muito mais.\"],\"xp-lines\":[\"Cada descoberta concede XP.\"],\"example-targets\":[\"minecraft:jungle\",\"minecraft:mushroom_fields\",\"minecraft:deep_dark\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Explorador!\",\"leave\":\"&cVocê deixou a profissão de Explorador.\",\"level-up\":\"&aParabéns! Nível %level% de Explorador! +%points% pontos\"}\n}";
    }

    private static String buildRangerJson() {
        return "{\n  \"id\":\"ranger\",\"enabled\":true,\"display-name\":\"Guardião\",\n" +
            "  \"short-description\":\"Derrote criaturas hostis e proteja o reino\",\n" +
            "  \"description\":\"Profissão dedicada ao combate contra criaturas hostis e proteção do mundo.\",\n" +
            "  \"icon\":\"minecraft:bow\",\"category\":\"COMMON\",\"sort-order\":8,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.ranger\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"KILL-ENTITY\":{\"minecraft:zombie\":{\"money\":5,\"xp\":10},\"minecraft:skeleton\":{\"money\":5,\"xp\":10},\"minecraft:creeper\":{\"money\":8,\"xp\":15},\"minecraft:spider\":{\"money\":4,\"xp\":8},\"minecraft:enderman\":{\"money\":12,\"xp\":20},\"minecraft:witch\":{\"money\":10,\"xp\":18},\"minecraft:phantom\":{\"money\":8,\"xp\":12},\"minecraft:blaze\":{\"money\":10,\"xp\":18},\"minecraft:wither_skeleton\":{\"money\":15,\"xp\":25},\"minecraft:ghast\":{\"money\":15,\"xp\":25},\"minecraft:guardian\":{\"money\":12,\"xp\":20},\"minecraft:elder_guardian\":{\"money\":30,\"xp\":50},\"minecraft:evoker\":{\"money\":25,\"xp\":40},\"minecraft:ravager\":{\"money\":20,\"xp\":35}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Derrote criaturas hostis.\",\"Chefes e criaturas raras pagam mais.\"],\"xp-lines\":[\"Cada criatura derrotada concede XP.\"],\"example-targets\":[\"minecraft:zombie\",\"minecraft:skeleton\",\"minecraft:wither_skeleton\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Guardião!\",\"leave\":\"&cVocê deixou a profissão de Guardião.\",\"level-up\":\"&aParabéns! Nível %level% de Guardião! +%points% pontos\"}\n}";
    }

    private static String buildCulinarianJson() {
        return "{\n  \"id\":\"culinarian\",\"enabled\":true,\"display-name\":\"Culinarista\",\n" +
            "  \"short-description\":\"Cozinhe alimentos e prepare banquetes\",\n" +
            "  \"description\":\"Profissão dedicada à culinária e preparação de alimentos.\",\n" +
            "  \"icon\":\"minecraft:cake\",\"category\":\"COMMON\",\"sort-order\":9,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.culinarian\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"CRAFT-ITEM\":{\"minecraft:bread\":{\"money\":2,\"xp\":4},\"minecraft:cooked_beef\":{\"money\":5,\"xp\":10},\"minecraft:cooked_porkchop\":{\"money\":5,\"xp\":10},\"minecraft:cooked_chicken\":{\"money\":4,\"xp\":8},\"minecraft:cooked_mutton\":{\"money\":4,\"xp\":8},\"minecraft:cooked_rabbit\":{\"money\":4,\"xp\":8},\"minecraft:cooked_salmon\":{\"money\":5,\"xp\":10},\"minecraft:cooked_cod\":{\"money\":5,\"xp\":10},\"minecraft:cake\":{\"money\":12,\"xp\":25},\"minecraft:pumpkin_pie\":{\"money\":8,\"xp\":15},\"minecraft:golden_apple\":{\"money\":20,\"xp\":50},\"minecraft:golden_carrot\":{\"money\":10,\"xp\":20}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Cozinhe alimentos usando fornalhas e crafting.\",\"Alimentos mais elaborados pagam mais.\"],\"xp-lines\":[\"Cada alimento preparado concede XP.\"],\"example-targets\":[\"minecraft:cooked_beef\",\"minecraft:cake\",\"minecraft:golden_apple\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Culinarista!\",\"leave\":\"&cVocê deixou a profissão de Culinarista.\",\"level-up\":\"&aParabéns! Nível %level% de Culinarista! +%points% pontos\"}\n}";
    }

    private static String buildMagicianJson() {
        return "{\n  \"id\":\"magician\",\"enabled\":true,\"display-name\":\"Mago\",\n" +
            "  \"short-description\":\"Encante itens e prepare poções mágicas\",\n" +
            "  \"description\":\"Profissão dedicada ao encantamento de itens e preparação de poções.\",\n" +
            "  \"icon\":\"minecraft:enchanted_book\",\"category\":\"COMMON\",\"sort-order\":10,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.magician\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"USE-MAGIC\":{\"minecraft:enchanting_table\":{\"money\":10,\"xp\":20},\"minecraft:brewing_stand\":{\"money\":8,\"xp\":15}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Encante itens na mesa de encantamentos.\",\"Prepare poções no suporte de poções.\"],\"xp-lines\":[\"Cada encantamento ou poção concede XP.\"],\"example-targets\":[\"minecraft:enchanting_table\",\"minecraft:brewing_stand\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Mago!\",\"leave\":\"&cVocê deixou a profissão de Mago.\",\"level-up\":\"&aParabéns! Nível %level% de Mago! +%points% pontos\"}\n}";
    }

    private static String buildFishermanJson() {
        return "{\n  \"id\":\"fisherman\",\"enabled\":true,\"display-name\":\"Pescador\",\n" +
            "  \"short-description\":\"Pesque em rios, lagos e oceanos\",\n" +
            "  \"description\":\"Profissão dedicada à pesca em todos os biomas aquáticos.\",\n" +
            "  \"icon\":\"minecraft:fishing_rod\",\"category\":\"COMMON\",\"sort-order\":11,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.fisherman\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":true,\"license-required\":false,\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":100,\"multiplier\":1.0,\"exponent\":1.5},\n" +
            "  \"max-daily-earnings\":-1,\"money-bonus-per-level\":0.5,\"max-level-money-bonus\":50.0,\"skill-points-every\":2,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"FISH\":{\"minecraft:cod\":{\"money\":3,\"xp\":6},\"minecraft:salmon\":{\"money\":5,\"xp\":10},\"minecraft:tropical_fish\":{\"money\":8,\"xp\":12},\"minecraft:pufferfish\":{\"money\":6,\"xp\":10}}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Pesque com vara de pesca em qualquer corpo d'água.\",\"Peixes tropicais pagam mais que peixes comuns.\"],\"xp-lines\":[\"Cada peixe pescado concede XP.\"],\"example-targets\":[\"minecraft:cod\",\"minecraft:salmon\",\"minecraft:tropical_fish\"]},\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Pescador!\",\"leave\":\"&cVocê deixou a profissão de Pescador.\",\"level-up\":\"&aParabéns! Nível %level% de Pescador! +%points% pontos\"}\n}";
    }

    private static String buildResearcherJson() {
        return "{\n  \"id\":\"researcher\",\"enabled\":true,\"display-name\":\"Pesquisador Pokémon\",\n" +
            "  \"short-description\":\"Capture Pokémon e registre na Pokédex\",\n" +
            "  \"description\":\"Especialização Pokémon focada em captura e pesquisa de novas espécies.\",\n" +
            "  \"icon\":\"cobblemon:poke_ball\",\"category\":\"POKEMON_SPECIALIZATION\",\"sort-order\":12,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.researcher\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":false,\"license-required\":true,\n" +
            "  \"required-integration\":\"cobblemon\",\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":150,\"multiplier\":1.2,\"exponent\":1.6},\n" +
            "  \"max-daily-earnings\":15000,\"money-bonus-per-level\":1.0,\"max-level-money-bonus\":100.0,\"skill-points-every\":3,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"POKEMON-CAPTURED\":{\"cobblemon:mewtwo\":{\"money\":500,\"xp\":1000}}},\"DEX-ENTRY-ADDED\":{}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Capture Pokémon selvagens.\",\"Registre novas espécies na Pokédex para bônus extras.\",\"Pokémon lendários pagam recompensas enormes.\"],\"xp-lines\":[\"Cada captura e registro concede XP.\"],\"example-targets\":[\"cobblemon:pikachu\",\"cobblemon:charizard\",\"cobblemon:mewtwo\"]},\n" +
            "  \"license-objectives\":[{\"objective-id\":\"capture_50\",\"action-type\":\"POKEMON_CAPTURED\",\"required-amount\":50,\"progress-message\":\"Capture 50 Pokémon\"},{\"objective-id\":\"dex_30\",\"action-type\":\"DEX_ENTRY_ADDED\",\"required-amount\":30,\"progress-message\":\"Registre 30 espécies na Pokédex\"}],\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Pesquisador Pokémon!\",\"leave\":\"&cVocê deixou a especialização de Pesquisador.\",\"level-up\":\"&aParabéns! Nível %level% de Pesquisador! +%points% pontos\"}\n}";
    }

    private static String buildBreederJson() {
        return "{\n  \"id\":\"breeder\",\"enabled\":true,\"display-name\":\"Criador Pokémon\",\n" +
            "  \"short-description\":\"Choque ovos e crie novos Pokémon\",\n" +
            "  \"description\":\"Especialização Pokémon focada em breeding e criação de ovos.\",\n" +
            "  \"icon\":\"cobblemon:rare_candy\",\"category\":\"POKEMON_SPECIALIZATION\",\"sort-order\":13,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.breeder\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":false,\"license-required\":true,\n" +
            "  \"required-integration\":\"cobblemon\",\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":150,\"multiplier\":1.2,\"exponent\":1.6},\n" +
            "  \"max-daily-earnings\":15000,\"money-bonus-per-level\":1.0,\"max-level-money-bonus\":100.0,\"skill-points-every\":3,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"EGG-CREATED\":{},\"EGG-HATCHED\":{}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Produza ovos Pokémon via breeding.\",\"Choque os ovos para obter recompensas maiores.\"],\"xp-lines\":[\"Cada ovo criado e chocado concede XP.\"],\"example-targets\":[\"cobblemon:egg\",\"cobblemon:rare_candy\"]},\n" +
            "  \"license-objectives\":[{\"objective-id\":\"hatch_10\",\"action-type\":\"EGG_HATCHED\",\"required-amount\":10,\"progress-message\":\"Choque 10 ovos\"}],\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Criador Pokémon!\",\"leave\":\"&cVocê deixou a especialização de Criador.\",\"level-up\":\"&aParabéns! Nível %level% de Criador! +%points% pontos\"}\n}";
    }

    private static String buildTrainerJson() {
        return "{\n  \"id\":\"trainer\",\"enabled\":true,\"display-name\":\"Treinador Pokémon\",\n" +
            "  \"short-description\":\"Derrote treinadores NPC em batalhas\",\n" +
            "  \"description\":\"Especialização Pokémon focada em batalhas contra treinadores NPC.\",\n" +
            "  \"icon\":\"cobblemon:exp_share\",\"category\":\"POKEMON_SPECIALIZATION\",\"sort-order\":14,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.trainer\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":false,\"license-required\":true,\n" +
            "  \"required-integration\":\"cobblemon\",\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":150,\"multiplier\":1.2,\"exponent\":1.6},\n" +
            "  \"max-daily-earnings\":15000,\"money-bonus-per-level\":1.0,\"max-level-money-bonus\":100.0,\"skill-points-every\":3,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"TRAINER-BATTLE-WON\":{}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Derrote treinadores NPC em batalhas Pokémon.\"],\"xp-lines\":[\"Cada batalha vencida concede XP.\"],\"example-targets\":[\"cobblemon:trainer\"]},\n" +
            "  \"license-objectives\":[{\"objective-id\":\"win_25\",\"action-type\":\"TRAINER_BATTLE_WON\",\"required-amount\":25,\"progress-message\":\"Vença 25 batalhas contra treinadores NPC\"}],\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Treinador Pokémon!\",\"leave\":\"&cVocê deixou a especialização de Treinador.\",\"level-up\":\"&aParabéns! Nível %level% de Treinador! +%points% pontos\"}\n}";
    }

    private static String buildPastureKeeperJson() {
        return "{\n  \"id\":\"pasture_keeper\",\"enabled\":true,\"display-name\":\"Cuidador de Pasto\",\n" +
            "  \"short-description\":\"Gerencie pastos e cuide de Pokémon\",\n" +
            "  \"description\":\"Especialização Pokémon focada no gerenciamento de pastos.\",\n" +
            "  \"icon\":\"minecraft:hay_block\",\"category\":\"POKEMON_SPECIALIZATION\",\"sort-order\":15,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.pasture_keeper\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":false,\"license-required\":true,\n" +
            "  \"required-integration\":\"cobblemon\",\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":150,\"multiplier\":1.2,\"exponent\":1.6},\n" +
            "  \"max-daily-earnings\":15000,\"money-bonus-per-level\":1.0,\"max-level-money-bonus\":100.0,\"skill-points-every\":3,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"PASTURE-TASK-COMPLETED\":{}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Complete tarefas de pasto como alimentação e coleta.\"],\"xp-lines\":[\"Cada tarefa completada concede XP.\"],\"example-targets\":[\"minecraft:hay_block\"]},\n" +
            "  \"license-objectives\":[{\"objective-id\":\"tasks_25\",\"action-type\":\"PASTURE_TASK_COMPLETED\",\"required-amount\":25,\"progress-message\":\"Complete 25 tarefas de pasto\"}],\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Cuidador de Pasto!\",\"leave\":\"&cVocê deixou a especialização de Cuidador.\",\"level-up\":\"&aParabéns! Nível %level% de Cuidador! +%points% pontos\"}\n}";
    }

    private static String buildPaleontologistJson() {
        return "{\n  \"id\":\"paleontologist\",\"enabled\":true,\"display-name\":\"Paleontólogo\",\n" +
            "  \"short-description\":\"Reviva fósseis e descubra Pokémon antigos\",\n" +
            "  \"description\":\"Especialização Pokémon focada em reviver fósseis.\",\n" +
            "  \"icon\":\"minecraft:bone\",\"category\":\"POKEMON_SPECIALIZATION\",\"sort-order\":16,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.paleontologist\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":false,\"license-required\":true,\n" +
            "  \"required-integration\":\"cobblemon\",\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":150,\"multiplier\":1.2,\"exponent\":1.6},\n" +
            "  \"max-daily-earnings\":15000,\"money-bonus-per-level\":1.0,\"max-level-money-bonus\":100.0,\"skill-points-every\":3,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"FOSSIL-REVIVED\":{}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Reviva fósseis em estações compatíveis.\"],\"xp-lines\":[\"Cada fóssil revivido concede XP.\"],\"example-targets\":[\"cobblemon:fossil\"]},\n" +
            "  \"license-objectives\":[{\"objective-id\":\"revive_5\",\"action-type\":\"FOSSIL_REVIVED\",\"required-amount\":5,\"progress-message\":\"Reviva 5 fósseis\"}],\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Paleontólogo!\",\"leave\":\"&cVocê deixou a especialização de Paleontólogo.\",\"level-up\":\"&aParabéns! Nível %level% de Paleontólogo! +%points% pontos\"}\n}";
    }

    private static String buildRaiderJson() {
        return "{\n  \"id\":\"raider\",\"enabled\":true,\"display-name\":\"Incursionista\",\n" +
            "  \"short-description\":\"Enfrente raids e desafios em grupo\",\n" +
            "  \"description\":\"Especialização Pokémon focada em raids e batalhas em grupo.\",\n" +
            "  \"icon\":\"minecraft:totem_of_undying\",\"category\":\"POKEMON_SPECIALIZATION\",\"sort-order\":17,\n" +
            "  \"permission\":\"bigbangessentials.jobs.profession.raider\",\n" +
            "  \"visible-without-permission\":true,\"unlocked-by-default\":false,\"license-required\":true,\n" +
            "  \"required-integration\":\"cobblemon\",\n" +
            "  \"max-level\":100,\"xp-curve\":{\"type\":\"polynomial\",\"base\":150,\"multiplier\":1.2,\"exponent\":1.6},\n" +
            "  \"max-daily-earnings\":15000,\"money-bonus-per-level\":1.0,\"max-level-money-bonus\":100.0,\"skill-points-every\":3,\n" +
            "  \"reset-progress-on-leave\":false,\n" +
            "  \"actions\":{\"RAID-CLEARED\":{}},\n" +
            "  \"how-to-earn\":{\"money-header\":\"Como ganhar dinheiro\",\"xp-header\":\"Como ganhar XP\",\"money-lines\":[\"Conclua raids Pokémon com sucesso.\"],\"xp-lines\":[\"Cada raid concluída concede XP.\"],\"example-targets\":[\"cobblemon:raid_den\"]},\n" +
            "  \"license-objectives\":[{\"objective-id\":\"raids_5\",\"action-type\":\"RAID_CLEARED\",\"required-amount\":5,\"progress-message\":\"Conclua 5 raids\"}],\n" +
            "  \"crate-rewards\":[{\"actions\":[],\"key-id\":\"iniciante\",\"key-display-name\":\"Chave Iniciante\",\"chance\":0.02,\"amount\":1,\"minimum-job-level\":10,\"daily-limit\":3,\"cooldown-seconds\":3600,\"priority\":10,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"intermediaria\",\"key-display-name\":\"Chave Intermediária\",\"chance\":0.01,\"amount\":1,\"minimum-job-level\":25,\"daily-limit\":2,\"cooldown-seconds\":5400,\"priority\":20,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"avancada\",\"key-display-name\":\"Chave Avançada\",\"chance\":0.005,\"amount\":1,\"minimum-job-level\":50,\"daily-limit\":1,\"cooldown-seconds\":7200,\"priority\":30,\"one-reward-per-action\":true,\"physical-key\":false},{\"actions\":[],\"key-id\":\"lendaria\",\"key-display-name\":\"Chave Lendária\",\"chance\":0.002,\"amount\":1,\"minimum-job-level\":80,\"daily-limit\":1,\"cooldown-seconds\":14400,\"priority\":40,\"one-reward-per-action\":true,\"physical-key\":false}],\"skills\":{},\"level-up-rewards\":{},\n" +
            "  \"messages\":{\"join\":\"&aVocê agora é um Incursionista!\",\"leave\":\"&cVocê deixou a especialização de Incursionista.\",\"level-up\":\"&aParabéns! Nível %level% de Incursionista! +%points% pontos\"}\n}";
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        return parent.has(key) ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static String getString(JsonObject obj, String key, String defaultValue) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : defaultValue;
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }

    private static long getLong(JsonObject obj, String key, long defaultValue) {
        return obj.has(key) ? obj.get(key).getAsLong() : defaultValue;
    }

    private static double getDouble(JsonObject obj, String key, double defaultValue) {
        return obj.has(key) ? obj.get(key).getAsDouble() : defaultValue;
    }

    private static boolean getBool(JsonObject obj, String key, boolean defaultValue) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : defaultValue;
    }

    private static String requireNonEmpty(JsonObject obj, String key, String filename) {
        if (!obj.has(key) || obj.get(key).isJsonNull() || obj.get(key).getAsString().trim().isEmpty())
            throw new IllegalArgumentException(filename + ": '" + key + "' is required and cannot be empty");
        return obj.get(key).getAsString();
    }

    private static List<String> jsonArrayToList(JsonArray arr) {
        List<String> result = new ArrayList<>();
        if (arr != null) {
            for (JsonElement el : arr) {
                if (!el.isJsonNull()) result.add(el.getAsString());
            }
        }
        return result;
    }

    private static void copyDir(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
