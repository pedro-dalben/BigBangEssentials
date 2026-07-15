package com.pedrodalben.bigbangessentials.database.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseException;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{ENV:([^}]+)\\}");

    public static DatabaseConfig load() throws DatabaseException {
        File file = ResourceUtil.getConfigFile("database.json");
        if (!file.exists()) {
            try {
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                file.createNewFile();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(GSON.toJson(new DatabaseConfig()));
                }
            } catch (IOException e) {
                throw new DatabaseException("Failed to create default config", e);
            }
        }
        
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            Matcher matcher = ENV_PATTERN.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String envVar = matcher.group(1);
                String envVal = System.getenv(envVar);
                if (envVal == null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement("__MISSING_ENV__" + envVar));
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(envVal));
                }
            }
            matcher.appendTail(sb);
            
            DatabaseConfig config = GSON.fromJson(sb.toString(), DatabaseConfig.class);
            if (config == null) {
                config = new DatabaseConfig();
            }
            if (config.getType() == com.pedrodalben.bigbangessentials.database.DatabaseType.SQLITE) {
                checkMissingEnv(config.getSqlite().getFile());
                checkMissingEnv(config.getSqlite().path);
                config.getPool().setMaximumPoolSize(1);
                config.getExecutor().setThreads(1);
            } else if (config.getType() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL) {
                checkMissingEnv(config.getMysql().getHost());
                checkMissingEnv(config.getMysql().getDatabase());
                checkMissingEnv(config.getMysql().getUsername());
                checkMissingEnv(config.getMysql().getPassword());
                checkMissingEnv(config.getMysql().getSslMode());
                checkMissingEnv(config.getMysql().getServerTimezone());
            }
            return config;
        } catch (IOException e) {
            throw new DatabaseException("Failed to read config", e);
        }
    }

    private static void checkMissingEnv(String val) throws DatabaseException {
        if (val != null && val.startsWith("__MISSING_ENV__")) {
            throw new DatabaseException("Missing environment variable: " + val.substring("__MISSING_ENV__".length()));
        }
    }

}
