package com.pedrodalben.bigbangessentials.core;

import com.pedrodalben.bigbangessentials.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Small lifecycle/status registry for top-level modules. */
public final class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleManager.class);
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final Map<String, Entry> modules = new LinkedHashMap<>();

    public static ModuleManager getInstance() { return INSTANCE; }

    public synchronized void register(String id, BooleanSupplier enabled, String... dependencies) {
        modules.put(id, new Entry(id, enabled, List.of(dependencies), ModuleHealth.registered()));
    }

    public synchronized boolean prepare(String id) {
        Entry entry = modules.get(id);
        if (entry == null) return false;
        if (!entry.enabled.getAsBoolean()) {
            entry.health = new ModuleHealth(ModuleState.DISABLED, "Disabled by configuration", 0);
            return false;
        }
        List<String> missing = entry.dependencies.stream()
            .filter(dep -> !isRunning(dep))
            .toList();
        if (!missing.isEmpty()) {
            entry.health = new ModuleHealth(ModuleState.BLOCKED,
                "Missing dependency: " + String.join(", ", missing), 0);
            LOGGER.warn("Module '{}' disabled: {}", id, entry.health.message());
            return false;
        }
        entry.health = new ModuleHealth(ModuleState.STARTING, "Starting", 0);
        return true;
    }

    public synchronized void started(String id, long millis) {
        Entry entry = modules.get(id);
        if (entry != null) entry.health = new ModuleHealth(ModuleState.RUNNING, "OK", millis);
    }

    public synchronized void failed(String id, Throwable error) {
        Entry entry = modules.get(id);
        if (entry != null) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            entry.health = new ModuleHealth(ModuleState.FAILED, message, entry.health.startupMillis());
        }
    }

    public synchronized boolean isRunning(String id) {
        Entry entry = modules.get(id);
        return entry != null && entry.health.state() == ModuleState.RUNNING;
    }

    public synchronized boolean isActive(String id) {
        Entry entry = modules.get(id);
        if (entry == null) return false;
        if (entry.health.state() == ModuleState.RUNNING) return true;
        // Command registration can happen before the server-start callback.
        // Allow configured modules to register, while runtime event handlers
        // become active only after prepare()/started().
        return entry.health.state() == ModuleState.REGISTERED
            && entry.enabled.getAsBoolean()
            && entry.dependencies.stream().allMatch(this::configured);
    }

    private boolean configured(String id) {
        Entry entry = modules.get(id);
        return entry != null && entry.enabled.getAsBoolean();
    }

    public synchronized ModuleHealth health(String id) {
        Entry entry = modules.get(id);
        return entry == null ? new ModuleHealth(ModuleState.FAILED, "Unknown module", 0) : entry.health;
    }

    public synchronized Map<String, ModuleHealth> health() {
        Map<String, ModuleHealth> result = new LinkedHashMap<>();
        modules.forEach((id, entry) -> result.put(id, entry.health));
        return result;
    }

    public synchronized String formatHealth() {
        StringBuilder result = new StringBuilder("BigBangEssentials modules:\n");
        modules.forEach((id, entry) -> result.append("- ").append(id)
            .append(": ").append(entry.health.state())
            .append(" — ").append(entry.health.message())
            .append(entry.health.startupMillis() > 0 ? " (" + entry.health.startupMillis() + "ms)" : "")
            .append('\n'));
        return result.toString();
    }

    private static final class Entry {
        private final String id;
        private final BooleanSupplier enabled;
        private final List<String> dependencies;
        private ModuleHealth health;

        private Entry(String id, BooleanSupplier enabled, List<String> dependencies, ModuleHealth health) {
            this.id = id;
            this.enabled = enabled;
            this.dependencies = dependencies;
            this.health = health;
        }
    }
}
