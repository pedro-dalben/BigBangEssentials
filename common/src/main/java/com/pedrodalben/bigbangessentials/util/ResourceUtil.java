package com.pedrodalben.bigbangessentials.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Centralized resource path management for BigBangEssentials
 * Provides consistent paths for configuration, data, and JAR resources
 */
public class ResourceUtil {
    
    // Standard Minecraft server directory structure
    // CONFIG_DIR: For actual configuration files stored in the world save
    public static final String CONFIG_DIR = "world/serverconfig/bigbangessentials/";
    public static final String LEGACY_CONFIG_DIR = "config/bigbangessentials/";

    // DATA_DIR: For all runtime data (player data, homes, warps, moderation, etc.)
    // This is in the server root for easy access and backup
    public static final String DATA_DIR = "bigbangessentials/";

    // JAR resource paths (internal mod resources)
    public static final String JAR_CONFIG_PATH = "/data/config/bigbangessentials/";
    public static final String JAR_LANG_PATH = "/data/lang/";
    public static final String JAR_ASSETS_PATH = "/assets/bigbangessentials/";
    
    /**
     * Get a configuration file path (stored in world/serverconfig/bigbangessentials/)
     */
    public static File getConfigFile(String filename) {
        return getConfigDirectoryPath().resolve(filename).toFile();
    }
    
    /**
     * Get a data file path (stored in bigbangessentials/ for runtime data)
     */
    public static File getDataFile(String filename) {
        return new File(DATA_DIR + filename);
    }
    
    /**
     * Get a Path for configuration files
     */
    public static Path getConfigPath(String filename) {
        return getConfigDirectoryPath().resolve(filename);
    }

    /**
     * Get the config directory path.
     */
    public static Path getConfigDirectoryPath() {
        return Paths.get(CONFIG_DIR);
    }

    /**
     * Get the legacy config directory path used by older versions.
     */
    public static Path getLegacyConfigDirectoryPath() {
        return Paths.get(LEGACY_CONFIG_DIR);
    }
    
    /**
     * Get a Path for data files
     */
    public static Path getDataPath(String filename) {
        return Paths.get(DATA_DIR + filename);
    }
    
    /**
     * Get InputStream for JAR configuration resource
     */
    public static InputStream getJarConfigResource(String filename) {
        return ResourceUtil.class.getResourceAsStream(JAR_CONFIG_PATH + filename);
    }
    
    /**
     * Get InputStream for JAR language resource
     */
    public static InputStream getJarLangResource(String filename) {
        return ResourceUtil.class.getResourceAsStream(JAR_LANG_PATH + filename);
    }
    
    /**
     * Get InputStream for JAR asset resource
     */
    @SuppressWarnings("unused") // Public API method
    public static InputStream getJarAssetResource(String filename) {
        return ResourceUtil.class.getResourceAsStream(JAR_ASSETS_PATH + filename);
    }
    
    /**
     * Ensure a directory exists
     */
    public static void ensureDirectoryExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                // Log error if directory creation fails
                System.err.println("Failed to create directory: " + dirPath);
            }
        }
    }
    
    /**
     * Ensure the config directory exists
     */
    @SuppressWarnings("unused") // Public API method
    public static void ensureConfigDirectory() {
        ensureDirectoryExists(CONFIG_DIR);
    }

    /**
     * Migrate files from the legacy config directory into the new world/serverconfig location.
     * Existing files in the new location are left untouched.
     *
     * @return true if at least one file was copied
     */
    public static boolean migrateLegacyConfigDirectory() {
        Path legacyDir = getLegacyConfigDirectoryPath();
        Path newDir = getConfigDirectoryPath();

        if (!Files.exists(legacyDir)) {
            return false;
        }

        final boolean[] copiedAny = {false};

        try {
            Files.createDirectories(newDir);

            try (var paths = Files.walk(legacyDir)) {
                paths.forEach(source -> {
                    try {
                        Path relative = legacyDir.relativize(source);
                        if (relative.toString().isEmpty()) {
                            return;
                        }

                        Path target = newDir.resolve(relative);
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                        } else if (!Files.exists(target)) {
                            Path parent = target.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                            copiedAny[0] = true;
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to migrate config file from legacy directory: " + e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Failed to migrate legacy config directory: " + e.getMessage());
        }

        return copiedAny[0];
    }

    /**
     * Remap a legacy config-relative path into the new world/serverconfig location.
     * Leaves custom or already-migrated paths unchanged.
     */
    public static String remapLegacyConfigPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }

        String normalized = path.replace('\\', '/');
        if (normalized.startsWith(LEGACY_CONFIG_DIR)) {
            return CONFIG_DIR + normalized.substring(LEGACY_CONFIG_DIR.length());
        }

        return path;
    }
    
    /**
     * Ensure the data directory exists
     */
    public static void ensureDataDirectory() {
        ensureDirectoryExists(DATA_DIR);
    }
    
    /**
     * Get standard language file (tries server directory first, then JAR)
     */
    public static File getLanguageFile(String locale) {
        return getDataFile("lang/" + locale + ".json");
    }
    
    /**
     * Get JAR language resource stream
     */
    public static InputStream getJarLanguageResource(String locale) {
        return getJarLangResource(locale + ".json");
    }
}
