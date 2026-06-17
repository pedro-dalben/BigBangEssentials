package com.pedrodalben.bigbangessentials.customcommands;

/**
 * Data class representing a single custom command alias.
 * <p>
 * A custom command maps a simple command name (e.g., "participar") to a target
 * command string (e.g., "campeonato participar"). When a player runs the custom
 * command, the target command is executed with any additional arguments appended.
 */
public class CustomCommandEntry {
    private String name;
    private String command;
    private String permission;
    private boolean enabled;
    private String description;
    private boolean requirePlayer;

    /**
     * Create a new custom command entry.
     *
     * @param name           The custom command name (without /)
     * @param command        The target command to execute (without /)
     * @param permission     The permission node required to use this command
     * @param enabled        Whether this command is currently enabled
     * @param description    A brief description of what this command does
     * @param requirePlayer  Whether this command can only be run by players (not console)
     */
    public CustomCommandEntry(String name, String command, String permission,
                              boolean enabled, String description, boolean requirePlayer) {
        this.name = name;
        this.command = command;
        this.permission = permission;
        this.enabled = enabled;
        this.description = description;
        this.requirePlayer = requirePlayer;
    }

    /**
     * Create a new custom command entry with default values.
     * Permission defaults to "bigbangessentials.customcmd.{name}".
     * Enabled defaults to true, requirePlayer defaults to false.
     *
     * @param name    The custom command name (without /)
     * @param command The target command to execute (without /)
     */
    public CustomCommandEntry(String name, String command) {
        this(name, command,
             "bigbangessentials.customcmd." + name.toLowerCase(),
             true,
             "Custom command alias for /" + command,
             false);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequirePlayer() {
        return requirePlayer;
    }

    public void setRequirePlayer(boolean requirePlayer) {
        this.requirePlayer = requirePlayer;
    }

    @Override
    public String toString() {
        return "CustomCommandEntry{" +
                "name='" + name + '\'' +
                ", command='" + command + '\'' +
                ", permission='" + permission + '\'' +
                ", enabled=" + enabled +
                ", description='" + description + '\'' +
                ", requirePlayer=" + requirePlayer +
                '}';
    }
}
