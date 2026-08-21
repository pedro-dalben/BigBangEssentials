package com.pedrodalben.bigbangessentials.holograms.command;

public final class HologramPermissions {
    private static final String BASE = "bigbangessentials.holograms.";

    public static final String ADMIN = BASE + "admin";
    public static final String HELP = BASE + "help";
    public static final String LIST = BASE + "list";
    public static final String CREATE = BASE + "create";
    public static final String INFO = BASE + "info";
    public static final String ENABLE = BASE + "enable";
    public static final String DISABLE = BASE + "disable";
    public static final String UPDATE = BASE + "update";
    public static final String CLONE = BASE + "clone";
    public static final String RENAME = BASE + "rename";
    public static final String DELETE = BASE + "delete";
    public static final String TELEPORT = BASE + "teleport";
    public static final String MOVE = BASE + "move";
    public static final String ALIGN = BASE + "align";
    public static final String LINES = BASE + "lines";
    public static final String PAGES = BASE + "pages";
    public static final String ACTIONS = BASE + "actions";
    public static final String VISIBILITY = BASE + "visibility";
    public static final String FLAGS = BASE + "flags";
    public static final String SAVE = BASE + "save";
    public static final String RELOAD = BASE + "reload";
    public static final String RECONCILE = BASE + "reconcile";
    public static final String DIAGNOSTICS = BASE + "diagnostics";
    public static final String STATS = BASE + "stats";
    public static final String EXPORT = BASE + "export";
    public static final String IMPORT = BASE + "import";
    public static final String SYSTEM_MANAGED = BASE + "system-managed";

    // Keep backward compat aliases
    public static final String EDIT = BASE + "lines";
    public static final String REMOVE = BASE + "delete";
    public static final String CLEANUP = BASE + "reconcile";

    private HologramPermissions() {}
}
