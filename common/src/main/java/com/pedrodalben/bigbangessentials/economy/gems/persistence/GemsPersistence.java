package com.pedrodalben.bigbangessentials.economy.gems.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.economy.gems.config.GemConfig;
import com.pedrodalben.bigbangessentials.economy.gems.config.GemConfigValidator;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemTransaction;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GemsPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(GemsPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_MIN = new GsonBuilder().create(); // For ledger single-line entries

    public static GemsPersistenceFailpoint activeFailpoint = null;

    private void checkFailpoint(GemsPersistenceFailpoint expected) {
        if (activeFailpoint == expected) {
            LOGGER.warn("TRIGGERING CRASH FAILPOINT: {}", expected);
            throw new RuntimeException("Crash Injection Failpoint: " + expected);
        }
    }

    private static final String CONFIG_FILE = "gems.json";
    private static final String STATE_FILE = "gems_state.json";
    private static final String STATE_TMP_FILE = "gems_state.json.tmp";
    private static final String LEDGER_FILE = "gems_transactions.jsonl";
    private static final String BACKUP_DIR = "gems_backups";

    private final File baseDir;
    private GemConfig config;
    private boolean gemsEnabled = false;

    public GemsPersistence() {
        this(null);
    }

    public GemsPersistence(File baseDir) {
        this.baseDir = baseDir;
        loadConfig();
    }

    private File getFile(String filename) {
        if (baseDir != null) {
            baseDir.mkdirs();
            return new File(baseDir, filename);
        }
        return ResourceUtil.getDataFile(filename);
    }

    public GemConfig getConfig() {
        return config;
    }

    public boolean isGemsEnabled() {
        return gemsEnabled && (config == null || config.enabled);
    }

    public void setGemsEnabled(boolean enabled) {
        this.gemsEnabled = enabled;
    }

    /**
     * Loads the gems.json configuration. If it doesn't exist, writes a default one.
     */
    public void loadConfig() {
        File file = getFile(CONFIG_FILE);
        if (!file.exists()) {
            LOGGER.info("Gems config file not found. Creating default gems.json.");
            config = new GemConfig();
            saveConfigDirect(config);
            gemsEnabled = true;
            return;
        }

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            GemConfig loaded = GSON.fromJson(reader, GemConfig.class);
            GemConfigValidator.ValidationResult result = GemConfigValidator.validate(loaded);
            if (result.valid) {
                config = loaded;
                gemsEnabled = config.enabled;
            } else {
                LOGGER.error("Gems configuration validation failed! Errors: {}", result.errors);
                LOGGER.error("Using secure fallback configuration. gems.json remains unchanged.");
                config = new GemConfig(); // Fallback config
                gemsEnabled = false; // Disable writing API or functionality
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read Gems config. Using fallback config.", e);
            config = new GemConfig();
            gemsEnabled = false;
        }
    }

    private void saveConfigDirect(GemConfig configToSave) {
        File file = getFile(CONFIG_FILE);
        ResourceUtil.ensureDataDirectory();
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(configToSave, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save default Gems config file", e);
        }
    }

    /**
     * Loads gems_state.json.
     */
    public GemsState loadState() {
        File file = getFile(STATE_FILE);
        if (!file.exists()) {
            LOGGER.info("Gems state file not found. Initializing empty state.");
            return new GemsState();
        }

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            GemsState state = GSON.fromJson(reader, GemsState.class);
            if (state == null) {
                return new GemsState();
            }
            if (state.balances == null) {
                state.balances = new ConcurrentHashMap<>();
            }
            if (state.reservations == null) {
                state.reservations = new ConcurrentHashMap<>();
            }
            return state;
        } catch (Exception e) {
            LOGGER.error("Failed to read Gems state file '{}'. Corruption detected!", file.getAbsolutePath(), e);
            // Create a backup of the corrupted state and disable Gems
            createCorruptedBackup(file);
            gemsEnabled = false;
            throw new RuntimeException("Gems state file is corrupted! Writing has been disabled to prevent data loss.", e);
        }
    }

    private void createCorruptedBackup(File corruptedFile) {
        try {
            File backupDir = getFile(BACKUP_DIR);
            if (!backupDir.exists()) backupDir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backupFile = new File(backupDir, "gems_state_corrupted_" + timestamp + ".json");
            Files.copy(corruptedFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.error("Corrupted state file backup saved to: {}", backupFile.getAbsolutePath());
        } catch (IOException ex) {
            LOGGER.error("Failed to create backup of corrupted state file", ex);
        }
    }

    /**
     * Saves gems_state.json atomically using a temporary file.
     */
    public synchronized void saveState(GemsState state) {
        if (!isGemsEnabled()) {
            LOGGER.warn("Gems module is disabled. State save aborted.");
            return;
        }

        checkFailpoint(GemsPersistenceFailpoint.BEFORE_WRITE_TEMP);

        state.revision++;
        File tmpFile = getFile(STATE_TMP_FILE);
        File stateFile = getFile(STATE_FILE);

        ResourceUtil.ensureDataDirectory();

        // Write to temp file
        try (Writer writer = new FileWriter(tmpFile, StandardCharsets.UTF_8)) {
            GSON.toJson(state, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write temporary Gems state file", e);
            throw new RuntimeException("Persistence failure during Gems state save (write step)", e);
        }

        checkFailpoint(GemsPersistenceFailpoint.AFTER_WRITE_TEMP);
        checkFailpoint(GemsPersistenceFailpoint.BEFORE_ATOMIC_MOVE);

        // Atomic move / replace
        try {
            Files.move(tmpFile.toPath(), stateFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Atomic move failed or not supported by filesystem. Falling back to copy-replace.");
            try {
                Files.copy(tmpFile.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (!tmpFile.delete()) {
                    tmpFile.deleteOnExit();
                }
            } catch (IOException ex) {
                LOGGER.error("Fallback state copy-replace failed!", ex);
                throw new RuntimeException("Persistence failure during Gems state save (atomic copy step)", ex);
            }
        }

        checkFailpoint(GemsPersistenceFailpoint.AFTER_ATOMIC_MOVE);

        // Create backup if enabled
        if (config.persistence.createBackups) {
            createStateBackup(stateFile, state.revision);
        }
    }

    private void createStateBackup(File stateFile, long revision) {
        try {
            File backupDir = getFile(BACKUP_DIR);
            if (!backupDir.exists()) backupDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backupFile = new File(backupDir, "gems_state_" + timestamp + "_rev" + revision + ".json");
            Files.copy(stateFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Prune old backups to keep only the last 10
            File[] files = backupDir.listFiles((dir, name) -> name.startsWith("gems_state_") && name.endsWith(".json"));
            if (files != null && files.length > 10) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                for (int i = 0; i < files.length - 10; i++) {
                    if (!files[i].delete()) {
                        LOGGER.warn("Failed to delete old backup file: {}", files[i].getName());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to create state backup file", e);
        }
    }

    /**
     * Appends a transaction to the ledger file gems_transactions.jsonl.
     */
    public synchronized void appendTransaction(GemTransaction tx) {
        if (!isGemsEnabled()) {
            return;
        }

        checkFailpoint(GemsPersistenceFailpoint.BEFORE_APPEND_LEDGER);

        File file = getFile(LEDGER_FILE);
        ResourceUtil.ensureDataDirectory();

        String line = GSON_MIN.toJson(tx);
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, true)))) {
            pw.println(line);
        } catch (IOException e) {
            LOGGER.error("Failed to append transaction to ledger", e);
            throw new RuntimeException("Persistence failure: failed to write to Gems ledger", e);
        }

        checkFailpoint(GemsPersistenceFailpoint.AFTER_APPEND_LEDGER);

        // Trim/Prune the ledger if it exceeds maxTransactionLogEntries
        // We do this periodically or on every mutation. Pruning on every mutation can be slow, 
        // but let's implement a quick check/cleanup if size gets too large (e.g., exceeds limit * 1.2).
        int maxEntries = config.persistence.maxTransactionLogEntries;
        if (maxEntries > 0) {
            checkAndTrimLedger(file, maxEntries);
        }
    }

    private void checkAndTrimLedger(File ledgerFile, int maxEntries) {
        // To avoid trimming on every single write, we check if file is over-sized and trim only then.
        // E.g. trim when file is more than 1.1x the max entries.
        long lineCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(ledgerFile, StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                lineCount++;
            }
        } catch (IOException e) {
            return; // Ignore read errors for prune trigger
        }

        if (lineCount > maxEntries * 1.1) {
            LOGGER.info("Ledger size ({} lines) exceeds max entries limit ({}). Trimming ledger file.", lineCount, maxEntries);
            File tempFile = new File(ledgerFile.getParentFile(), ledgerFile.getName() + ".prune");
            List<String> lastLines = new ArrayList<>(maxEntries);

            try (BufferedReader reader = new BufferedReader(new FileReader(ledgerFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lastLines.add(line);
                    if (lastLines.size() > maxEntries) {
                        lastLines.remove(0); // keep only last
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read ledger file for trimming", e);
                return;
            }

            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(tempFile, StandardCharsets.UTF_8)))) {
                for (String l : lastLines) {
                    pw.println(l);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write temporary trimmed ledger file", e);
                return;
            }

            try {
                Files.move(tempFile.toPath(), ledgerFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                try {
                    Files.copy(tempFile.toPath(), ledgerFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    tempFile.delete();
                } catch (IOException ex) {
                    LOGGER.error("Failed to replace ledger file with trimmed version", ex);
                }
            }
        }
    }

    /**
     * Reads all transaction history for a player.
     */
    public synchronized List<GemTransaction> getHistory(UUID playerUuid) {
        List<GemTransaction> history = new ArrayList<>();
        File file = getFile(LEDGER_FILE);
        if (!file.exists()) {
            return history;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    GemTransaction tx = GSON_MIN.fromJson(line, GemTransaction.class);
                    if (tx != null && playerUuid.equals(tx.playerUuid())) {
                        history.add(tx);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Ignoring malformed transaction line in ledger: {}", line);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read transaction history", e);
        }

        // Sort by timestamp desc
        history.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        return history;
    }

    public synchronized void forceManualBackup(String reason) {
        File stateFile = getFile(STATE_FILE);
        if (!stateFile.exists()) {
            return;
        }
        try {
            File backupDir = getFile(BACKUP_DIR);
            if (!backupDir.exists()) backupDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backupFile = new File(backupDir, "gems_state_manual_" + reason + "_" + timestamp + ".json");
            Files.copy(stateFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Manual state backup saved: {}", backupFile.getName());
        } catch (IOException e) {
            LOGGER.error("Failed to perform manual backup", e);
            throw new RuntimeException("Failed to save backup for repair/verify", e);
        }
    }
}
