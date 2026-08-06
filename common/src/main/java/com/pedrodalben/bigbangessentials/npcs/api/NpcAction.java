package com.pedrodalben.bigbangessentials.npcs.api;

import java.util.Objects;

public final class NpcAction {
    private final NpcActionType type;
    private final String command;

    public NpcAction(NpcActionType type, String command) {
        this.type = type != null ? type : NpcActionType.NONE;
        this.command = command != null ? command.trim() : "";
    }

    public static NpcAction none() {
        return new NpcAction(NpcActionType.NONE, "");
    }

    public static NpcAction playerCommand(String command) {
        return new NpcAction(NpcActionType.PLAYER_COMMAND, stripSlash(command));
    }

    public static NpcAction consoleCommand(String command) {
        return new NpcAction(NpcActionType.CONSOLE_COMMAND, stripSlash(command));
    }

    private static String stripSlash(String cmd) {
        if (cmd == null) return "";
        String trimmed = cmd.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
    }

    public NpcActionType type() { return type; }
    public String command() { return command; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NpcAction that)) return false;
        return type == that.type && command.equals(that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, command);
    }
}
