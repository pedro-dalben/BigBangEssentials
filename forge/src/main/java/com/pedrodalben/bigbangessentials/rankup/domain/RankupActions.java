package com.pedrodalben.bigbangessentials.rankup.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RankupActions {
    private final String broadcast;
    private final List<String> commands;

    public RankupActions(String broadcast, List<String> commands) {
        this.broadcast = broadcast;
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
    }

    public String broadcast() {
        return broadcast;
    }

    public List<String> commands() {
        return Collections.unmodifiableList(commands);
    }

    public RankupActions withBroadcast(String broadcast) {
        return new RankupActions(broadcast, commands);
    }

    public RankupActions withCommands(List<String> commands) {
        return new RankupActions(broadcast, commands);
    }
}
