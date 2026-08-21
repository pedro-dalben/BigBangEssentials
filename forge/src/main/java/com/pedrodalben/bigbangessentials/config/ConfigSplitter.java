package com.pedrodalben.bigbangessentials.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the compatibility view and migration of BigBangEssentials split configs.
 * Existing split files are canonical; bundled files only provide missing defaults.
 */
public final class ConfigSplitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigSplitter.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SPLIT_MARKER = ".split_configs";
    private static final String VERSION_KEY = "_configVersion";

    /** Monolithic sections that have a split owner. Order is also merge order. */
    private static final Map<String, String> SECTION_FILES = new LinkedHashMap<>();
    private static final Map<String, Integer> SPLIT_VERSIONS = new LinkedHashMap<>();
    /** Object-shaped standalone configs exposed in the legacy merged view. */
    private static final Map<String, String> STANDALONE_FILES = new LinkedHashMap<>();
    private static final List<String> DATA_LIST_FILES = List.of("crates.json", "keys.json", "shops.json");

    static {
        SECTION_FILES.put("modules", "modules.json");
        SECTION_FILES.put("logging", "main.json");
        SECTION_FILES.put("localization", "main.json");
        SECTION_FILES.put("permissions", "main.json");
        SECTION_FILES.put("security", "security.json");
        SECTION_FILES.put("commands", "commands.json");
        SECTION_FILES.put("webDashboard", "webdashboard.json");
        SECTION_FILES.put("items", "items.json");
        SECTION_FILES.put("afk", "afk.json");
        SECTION_FILES.put("kits", "kits.json");
        SECTION_FILES.put("teleportation", "teleportation.json");
        SECTION_FILES.put("moderation", "moderation.json");
        SECTION_FILES.put("chat", "chat.json");
        SECTION_FILES.put("tablist", "tablist.json");

        SPLIT_VERSIONS.put("main.json", 2);
        SPLIT_VERSIONS.put("commands.json", 1);
        SPLIT_VERSIONS.put("chat.json", 1);
        SPLIT_VERSIONS.put("teleportation.json", 2);
        SPLIT_VERSIONS.put("moderation.json", 1);
        SPLIT_VERSIONS.put("webdashboard.json", 1);
        SPLIT_VERSIONS.put("items.json", 1);
        SPLIT_VERSIONS.put("afk.json", 1);
        SPLIT_VERSIONS.put("security.json", 1);
        SPLIT_VERSIONS.put("modules.json", 3);
        SPLIT_VERSIONS.put("tablist.json", 2);
        SPLIT_VERSIONS.put("kits.json", 2);

        STANDALONE_FILES.put("economy", "economy.json");
        STANDALONE_FILES.put("database", "database.json");
        STANDALONE_FILES.put("discordAuth", "discord_auth.json");
        STANDALONE_FILES.put("rankup", "rankup.json");
        STANDALONE_FILES.put("customCommands", "custom_commands.json");
        STANDALONE_FILES.put("holograms", "holograms.json");
        STANDALONE_FILES.put("worth", "worth.json");
    }

    private ConfigSplitter() {
    }

    public static boolean isSplittingEnabled() {
        return new File(ResourceUtil.CONFIG_DIR, SPLIT_MARKER).exists();
    }

    /**
     * Startup validation is deliberately non-destructive for existing files.
     * Administrators use the explicit apply command for legacy-key migration.
     */
    public static void ensureSplitConfigsUpToDate() {
        if (!isSplittingEnabled()) return;

        File configFile = ResourceUtil.getConfigFile("config.json");
        JsonObject source = readUnifiedSource(configFile);
        if (source != null) {
            ensureMissingSplitConfigs(source);
        }

        for (Map.Entry<String, Integer> entry : SPLIT_VERSIONS.entrySet()) {
            File file = ResourceUtil.getConfigFile(entry.getKey());
            if (!file.exists()) continue;
            try {
                JsonElement parsed = readJson(file);
                if (!parsed.isJsonObject() && !DATA_LIST_FILES.contains(file.getName())) {
                    LOGGER.warn("Split config {} has an unsupported root type", file.getName());
                    continue;
                }
                if (parsed.isJsonObject()) {
                    int current = intValue(parsed.getAsJsonObject(), VERSION_KEY, 0);
                    if (current < entry.getValue()) {
                        LOGGER.info("Split config {} is outdated ({} < {}). Run '/bigbangessentials config split dry-run' then 'apply'.",
                            file.getName(), current, entry.getValue());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to validate split config {}: {}", file.getName(), e.getMessage());
            }
        }
    }

    /** Compatibility entry point used by the old command. */
    public static boolean migrateToSplitConfigs() {
        return applySplitMigration().success();
    }

    public static SplitMigrationReport inspectSplitMigration() {
        return migrate(false);
    }

    public static SplitMigrationReport applySplitMigration() {
        return migrate(true);
    }

    private static SplitMigrationReport migrate(boolean apply) {
        SplitMigrationReport report = new SplitMigrationReport(apply);
        File configFile = ResourceUtil.getConfigFile("config.json");
        JsonObject source = readUnifiedSource(configFile);

        if (!isSplittingEnabled() && source == null) {
            report.error("config.json não contém uma configuração monolítica válida");
            return report;
        }

        if (!isSplittingEnabled() && source != null) {
            report.change("ativar .split_configs e transformar config.json em stub");
            for (Map.Entry<String, String> entry : SECTION_FILES.entrySet()) {
                String section = entry.getKey();
                File target = ResourceUtil.getConfigFile(entry.getValue());
                if (!source.has(section)) continue;
                if (target.exists()) {
                    report.preserved(target.getName() + " já existe; valor canônico preservado");
                    continue;
                }
                report.change("criar " + target.getName() + " a partir de " + section);
                if (apply) writeAtomically(target, extractSection(source, section, target.getName()), report);
            }
            if (apply) {
                try {
                    File marker = new File(ResourceUtil.CONFIG_DIR, SPLIT_MARKER);
                    if (!marker.exists() && !marker.createNewFile()) {
                        report.error("não foi possível criar .split_configs");
                    }
                    backupFile(configFile, report);
                    writeAtomically(configFile, createStub(), report);
                } catch (IOException e) {
                    report.error("falha ao ativar split: " + e.getMessage());
                }
            }
        }

        if (isSplittingEnabled() || apply) {
            normalizeExistingSplitFiles(report, apply);
        }
        return report;
    }

    private static void normalizeExistingSplitFiles(SplitMigrationReport report, boolean apply) {
        File modulesFile = ResourceUtil.getConfigFile("modules.json");
        JsonObject modules = readObject(modulesFile, report);
        if (modules != null) {
            JsonObject normalized = normalizeModules(modules, report);
            normalizeVersion(normalized, "modules.json", report);
            writeIfChanged(modulesFile, modules, normalized, report, apply);
        }

        File mainFile = ResourceUtil.getConfigFile("main.json");
        JsonObject main = readObject(mainFile, report);
        if (main != null) {
            JsonObject normalized = main.deepCopy();
            if (normalized.has("modules") && normalized.get("modules").isJsonObject()) {
                report.change("migrar modules.modules de main.json para modules.json");
                if (modules == null) modules = new JsonObject();
                mergeMissing(modules, normalized.getAsJsonObject("modules"));
                normalized.remove("modules");
            }
            JsonObject source = readUnifiedSource(ResourceUtil.getConfigFile("config.json"));
            if (!normalized.has("localization") && source != null && source.has("localization")) {
                normalized.add("localization", source.get("localization").deepCopy());
                report.change("adicionar localization em main.json");
            }
            normalizeVersion(normalized, "main.json", report);
            writeIfChanged(mainFile, main, normalized, report, apply);
            if (modules != null) {
                JsonObject moduleRoot = normalizeModules(modules, report);
                normalizeVersion(moduleRoot, "modules.json", report);
                writeIfChanged(modulesFile, modules, moduleRoot, report, apply);
            }
        }

        File teleportationFile = ResourceUtil.getConfigFile("teleportation.json");
        JsonObject teleportation = readObject(teleportationFile, report);
        if (teleportation != null) {
            JsonObject normalized = teleportation.deepCopy();
            migrateTeleportation(normalized, report);
            normalizeVersion(normalized, "teleportation.json", report);
            writeIfChanged(teleportationFile, teleportation, normalized, report, apply);
        }

        for (String fileName : List.of("commands.json", "chat.json", "security.json", "webdashboard.json",
                "items.json", "afk.json", "moderation.json", "kits.json", "tablist.json")) {
            File file = ResourceUtil.getConfigFile(fileName);
            JsonObject object = readObject(file, report);
            if (object == null) continue;
            JsonObject normalized = object.deepCopy();
            JsonObject source = readUnifiedSource(ResourceUtil.getConfigFile("config.json"));
            String section = sectionForFile(fileName);
            if (source != null && section != null && source.has(section)) {
                mergeMissing(normalized, extractSection(source, section, fileName));
            }
            normalizeVersion(normalized, fileName, report);
            writeIfChanged(file, object, normalized, report, apply);
        }

        for (Map.Entry<String, String> entry : STANDALONE_FILES.entrySet()) {
            File file = ResourceUtil.getConfigFile(entry.getValue());
            if (!file.exists()) continue;
            try {
                JsonElement parsed = readJson(file);
                if (!parsed.isJsonObject()) {
                    report.preserved(entry.getValue() + " preservado: formato de dados não-objeto");
                } else {
                    report.preserved(entry.getValue() + " reconhecido como configuração independente");
                }
            } catch (Exception e) {
                report.error(entry.getValue() + " inválido: " + e.getMessage());
            }
        }
        for (String fileName : DATA_LIST_FILES) {
            File file = ResourceUtil.getConfigFile(fileName);
            if (!file.exists()) continue;
            try {
                JsonElement parsed = readJson(file);
                if (!parsed.isJsonArray()) report.error(fileName + " deveria conter uma lista JSON");
                else report.preserved(fileName + " preservado como lista de dados mutáveis");
            } catch (Exception e) {
                report.error(fileName + " inválido: " + e.getMessage());
            }
        }
    }

    static JsonObject normalizeModulesForTest(JsonObject original) {
        return normalizeModules(original, new SplitMigrationReport(false));
    }

    static JsonObject normalizeTeleportationForTest(JsonObject original) {
        JsonObject normalized = original.deepCopy();
        migrateTeleportation(normalized, new SplitMigrationReport(false));
        return normalized;
    }

    private static JsonObject normalizeModules(JsonObject original, SplitMigrationReport report) {
        JsonObject normalized = original.deepCopy();
        JsonObject nested = normalized.has("modules") && normalized.get("modules").isJsonObject()
            ? normalized.getAsJsonObject("modules") : null;
        if (nested != null) {
            for (Map.Entry<String, JsonElement> entry : nested.entrySet()) {
                if (!normalized.has(entry.getKey())) {
                    normalized.add(entry.getKey(), entry.getValue().deepCopy());
                    report.change("preencher modules.json." + entry.getKey() + " a partir da estrutura legada");
                }
            }
            normalized.remove("modules");
            report.change("remover estrutura duplicada modules.modules; flags da raiz vencem");
        }
        return normalized;
    }

    private static void migrateTeleportation(JsonObject root, SplitMigrationReport report) {
        JsonObject teleportation = root.has("teleportation") && root.get("teleportation").isJsonObject()
            ? root.getAsJsonObject("teleportation") : root;
        if (!teleportation.has("randomTeleportSettings") || !teleportation.get("randomTeleportSettings").isJsonObject()) return;
        JsonObject settings = teleportation.getAsJsonObject("randomTeleportSettings");
        if (!settings.has("world") && settings.has("targetWorld") && settings.get("targetWorld").isJsonPrimitive()) {
            JsonArray worlds = new JsonArray();
            worlds.add(settings.get("targetWorld").getAsString());
            settings.add("world", worlds);
            report.change("teleportation.randomTeleportSettings.targetWorld -> world[]");
        }
        if (settings.has("targetWorld") && settings.has("world")) {
            report.preserved("teleportation.targetWorld preservado como alias legado");
        }
        if (settings.has("defaultLocation") && settings.has("targetWorld")
                && settings.get("defaultLocation").isJsonPrimitive()
                && settings.get("targetWorld").isJsonPrimitive()) {
            String defaultLocation = settings.get("defaultLocation").getAsString();
            String targetWorld = settings.get("targetWorld").getAsString();
            if (defaultLocation.equals("{" + targetWorld + "}")) {
                settings.addProperty("defaultLocation", "{world}");
                report.change("normalizar placeholder defaultLocation para {world}");
            }
        }
    }

    private static void normalizeVersion(JsonObject object, String fileName, SplitMigrationReport report) {
        Integer expected = SPLIT_VERSIONS.get(fileName);
        if (expected == null) return;
        int current = intValue(object, VERSION_KEY, 0);
        if (current < expected) {
            object.addProperty(VERSION_KEY, expected);
            report.change("atualizar " + fileName + " para versão " + expected);
        }
    }

    public static JsonObject mergeSplitConfigs() {
        JsonObject merged = new JsonObject();
        merged.addProperty(VERSION_KEY, 22);
        merged.addProperty("_configVersion_comment", "NOTE: This is a virtual merged view. Edit individual config files instead.");

        for (Map.Entry<String, String> entry : SECTION_FILES.entrySet()) {
            String section = entry.getKey();
            String fileName = entry.getValue();
            JsonObject fileConfig = readObject(ResourceUtil.getConfigFile(fileName), null);
            if (fileConfig == null) continue;

            if ("modules.json".equals(fileName)) {
                JsonObject modules = merged.has("modules") ? merged.getAsJsonObject("modules") : new JsonObject();
                JsonObject nested = fileConfig.has("modules") && fileConfig.get("modules").isJsonObject()
                    ? fileConfig.getAsJsonObject("modules") : new JsonObject();
                mergeMissing(modules, nested);
                for (Map.Entry<String, JsonElement> module : fileConfig.entrySet()) {
                    if (!module.getKey().startsWith("_") && !"modules".equals(module.getKey())) {
                        modules.add(module.getKey(), module.getValue().deepCopy());
                    }
                }
                merged.add("modules", modules);
            } else if ("main.json".equals(fileName)) {
                if (fileConfig.has(section)) merged.add(section, fileConfig.get(section).deepCopy());
            } else if (fileConfig.has(section)) {
                merged.add(section, fileConfig.get(section).deepCopy());
            }
        }

        for (Map.Entry<String, String> entry : STANDALONE_FILES.entrySet()) {
            JsonObject fileConfig = readObject(ResourceUtil.getConfigFile(entry.getValue()), null);
            if (fileConfig == null) continue;
            String section = entry.getKey();
            if (fileConfig.has(section) && fileConfig.get(section).isJsonObject()) {
                merged.add(section, fileConfig.get(section).deepCopy());
            } else {
                merged.add(section, fileConfig.deepCopy());
            }
        }
        return merged;
    }

    public static boolean isFreshInstall() {
        File configFile = ResourceUtil.getConfigFile("config.json");
        return !configFile.exists() || !isSplittingEnabled();
    }

    public static boolean autoSplitForFreshInstall() {
        if (ResourceUtil.getConfigFile("config.json").exists()) return false;
        return createSplitConfigsFromJar();
    }

    private static boolean createSplitConfigsFromJar() {
        ResourceUtil.ensureConfigDirectory();
        boolean created = false;
        for (String fileName : SPLIT_VERSIONS.keySet()) {
            File target = ResourceUtil.getConfigFile(fileName);
            if (target.exists()) continue;
            JsonObject template = readSplitTemplate(fileName);
            if (template == null) continue;
            writeAtomically(target, template, null);
            created = true;
        }
        try {
            File marker = new File(ResourceUtil.CONFIG_DIR, SPLIT_MARKER);
            if (!marker.exists()) marker.createNewFile();
            return created;
        } catch (IOException e) {
            LOGGER.error("Failed to enable split configs: {}", e.getMessage());
            return false;
        }
    }

    public static void checkAndPromptMigration() {
        if (isSplittingEnabled()) return;
        File configFile = ResourceUtil.getConfigFile("config.json");
        if (configFile.exists()) {
            LOGGER.info("BigBangEssentials supports split configs. Run /bigbangessentials config split dry-run, then apply.");
            shouldNotifyAdmins = true;
        }
    }

    private static boolean shouldNotifyAdmins;

    public static boolean shouldNotifyAdmins() {
        return shouldNotifyAdmins;
    }

    public static void markAdminsNotified() {
        shouldNotifyAdmins = false;
    }

    public static final class SplitMigrationReport {
        private final boolean applying;
        private final List<String> changes = new ArrayList<>();
        private final List<String> preserved = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        private SplitMigrationReport(boolean applying) {
            this.applying = applying;
        }

        public boolean success() {
            return errors.isEmpty();
        }

        public boolean applying() {
            return applying;
        }

        public List<String> changes() {
            return List.copyOf(changes);
        }

        public List<String> preserved() {
            return List.copyOf(preserved);
        }

        public List<String> errors() {
            return List.copyOf(errors);
        }

        private void change(String message) {
            changes.add(message);
        }

        private void preserved(String message) {
            preserved.add(message);
        }

        private void error(String message) {
            errors.add(message);
            LOGGER.error("Split migration: {}", message);
        }
    }

    private static JsonObject createStub() {
        JsonObject stub = new JsonObject();
        stub.addProperty(VERSION_KEY, 22);
        stub.addProperty("_configVersion_comment", "This file is a virtual stub; edit the split files instead.");
        stub.addProperty("_notice", "This server is using SPLIT CONFIGURATION FILES.");
        stub.addProperty("_notice_info", "Edit individual files in world/serverconfig/bigbangessentials.");
        JsonObject files = new JsonObject();
        for (Map.Entry<String, String> entry : SECTION_FILES.entrySet()) {
            files.addProperty(entry.getValue(), "Configuration section: " + entry.getKey());
        }
        for (Map.Entry<String, String> entry : STANDALONE_FILES.entrySet()) {
            files.addProperty(entry.getValue(), "Standalone module configuration: " + entry.getKey());
        }
        stub.add("_split_config_files", files);
        stub.addProperty("_restore_instructions", "Restore config.json.backup and remove .split_configs to leave split mode.");
        return stub;
    }

    private static JsonObject extractSection(JsonObject source, String section, String fileName) {
        JsonObject result = new JsonObject();
        Integer version = SPLIT_VERSIONS.get(fileName);
        if (version != null) result.addProperty(VERSION_KEY, version);
        if ("modules.json".equals(fileName) && source.has("modules") && source.get("modules").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject("modules").entrySet()) {
                result.add(entry.getKey(), entry.getValue().deepCopy());
            }
        } else if ("main.json".equals(fileName)) {
            for (String name : List.of("logging", "localization", "permissions")) {
                if (source.has(name)) result.add(name, source.get(name).deepCopy());
            }
        } else if (source.has(section)) {
            result.add(section, source.get(section).deepCopy());
        }
        return result;
    }

    private static void ensureMissingSplitConfigs(JsonObject source) {
        for (Map.Entry<String, String> entry : SECTION_FILES.entrySet()) {
            File target = ResourceUtil.getConfigFile(entry.getValue());
            if (target.exists() || !source.has(entry.getKey())) continue;
            writeAtomically(target, extractSection(source, entry.getKey(), target.getName()), null);
            LOGGER.info("Generated missing split config {}", target.getName());
        }
    }

    private static String sectionForFile(String fileName) {
        if ("main.json".equals(fileName)) return "logging";
        for (Map.Entry<String, String> entry : SECTION_FILES.entrySet()) {
            if (entry.getValue().equals(fileName) && !"modules".equals(entry.getKey())) return entry.getKey();
        }
        return null;
    }

    private static JsonObject readUnifiedSource(File configFile) {
        for (File candidate : List.of(configFile, new File(configFile.getParentFile(), "config.json.backup"))) {
            JsonObject object = readObject(candidate, null);
            if (object != null && !object.has("_split_config_files")) return object;
        }
        try (InputStream in = ConfigSplitter.class.getClassLoader().getResourceAsStream("data/config/bigbangessentials/config.json")) {
            if (in == null) return null;
            JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            LOGGER.warn("Could not read bundled config template: {}", e.getMessage());
            return null;
        }
    }

    private static JsonObject readSplitTemplate(String fileName) {
        try (InputStream in = ResourceUtil.getJarConfigResource(fileName)) {
            if (in != null) {
                JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read split template {}: {}", fileName, e.getMessage());
        }
        JsonObject unified = readUnifiedSource(ResourceUtil.getConfigFile("config.json"));
        String section = sectionForFile(fileName);
        return unified != null && section != null && unified.has(section)
            ? extractSection(unified, section, fileName) : null;
    }

    private static JsonObject readObject(File file, SplitMigrationReport report) {
        if (!file.exists()) return null;
        try {
            JsonElement parsed = readJson(file);
            if (!parsed.isJsonObject()) {
                if (report != null) report.preserved(file.getName() + " preservado: raiz não é objeto");
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (Exception e) {
            if (report != null) report.error(file.getName() + " inválido: " + e.getMessage());
            return null;
        }
    }

    private static JsonElement readJson(File file) throws IOException {
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void mergeMissing(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!target.has(entry.getKey())) {
                target.add(entry.getKey(), entry.getValue().deepCopy());
            } else if (target.get(entry.getKey()).isJsonObject() && entry.getValue().isJsonObject()) {
                mergeMissing(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
            }
        }
    }

    private static void writeIfChanged(File file, JsonObject original, JsonObject normalized,
                                       SplitMigrationReport report, boolean apply) {
        if (original.toString().equals(normalized.toString())) return;
        if (!apply) return;
        backupFile(file, report);
        writeAtomically(file, normalized, report);
    }

    private static void backupFile(File file, SplitMigrationReport report) {
        if (!file.exists()) return;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File backup = new File(file.getParentFile(), file.getName().replace(".json", "_split-migration_" + timestamp + ".bak"));
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (report != null) report.preserved("backup criado: " + backup.getName());
        } catch (IOException e) {
            if (report != null) report.error("não foi possível criar backup de " + file.getName() + ": " + e.getMessage());
        }
    }

    private static void writeAtomically(File file, JsonObject object, SplitMigrationReport report) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            if (report != null) report.error("não foi possível criar " + parent.getAbsolutePath());
            return;
        }
        File temp = new File(file.getPath() + ".tmp");
        try (FileWriter writer = new FileWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(object, writer);
        } catch (IOException e) {
            if (report != null) report.error("falha ao escrever " + file.getName() + ": " + e.getMessage());
            return;
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                if (report != null) report.error("falha ao substituir " + file.getName() + ": " + e.getMessage());
            }
        }
    }
}
