package com.pedrodalben.bigbangessentials.rankup.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RankupRequirements {
    private final double money;
    private final int gems;
    private final RankupTaskMode taskMode;
    private final List<RankupTask> tasks;

    public RankupRequirements(double money, int gems, RankupTaskMode taskMode, List<RankupTask> tasks) {
        this.money = money;
        this.gems = gems;
        this.taskMode = taskMode != null ? taskMode : RankupTaskMode.ALL;
        this.tasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
    }

    public double money() {
        return money;
    }

    public int gems() {
        return gems;
    }

    public RankupTaskMode taskMode() {
        return taskMode;
    }

    public List<RankupTask> tasks() {
        return Collections.unmodifiableList(tasks);
    }

    public RankupRequirements withMoney(double money) {
        return new RankupRequirements(money, gems, taskMode, tasks);
    }

    public RankupRequirements withGems(int gems) {
        return new RankupRequirements(money, gems, taskMode, tasks);
    }

    public RankupRequirements withTaskMode(RankupTaskMode taskMode) {
        return new RankupRequirements(money, gems, taskMode, tasks);
    }

    public RankupRequirements withTasks(List<RankupTask> tasks) {
        return new RankupRequirements(money, gems, taskMode, tasks);
    }
}
