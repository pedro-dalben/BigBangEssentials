package com.pedrodalben.bigbangessentials.rankup.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RankupTaskFilter {
    private final List<String> blocks;
    private final List<String> items;
    private final List<String> entities;
    private final List<String> biomes;
    private final List<String> advancements;
    private final List<String> species;
    private final List<String> types;
    private final Boolean legendary;
    private final Boolean shiny;
    private final Boolean fishOnly;
    private final Boolean bossOnly;

    public RankupTaskFilter() {
        this.blocks = new ArrayList<>();
        this.items = new ArrayList<>();
        this.entities = new ArrayList<>();
        this.biomes = new ArrayList<>();
        this.advancements = new ArrayList<>();
        this.species = new ArrayList<>();
        this.types = new ArrayList<>();
        this.legendary = null;
        this.shiny = null;
        this.fishOnly = null;
        this.bossOnly = null;
    }

    public RankupTaskFilter(List<String> blocks, List<String> items, List<String> entities, List<String> biomes,
                            List<String> advancements, List<String> species, List<String> types,
                            Boolean legendary, Boolean shiny, Boolean fishOnly, Boolean bossOnly) {
        this.blocks = blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.entities = entities != null ? new ArrayList<>(entities) : new ArrayList<>();
        this.biomes = biomes != null ? new ArrayList<>(biomes) : new ArrayList<>();
        this.advancements = advancements != null ? new ArrayList<>(advancements) : new ArrayList<>();
        this.species = species != null ? new ArrayList<>(species) : new ArrayList<>();
        this.types = types != null ? new ArrayList<>(types) : new ArrayList<>();
        this.legendary = legendary;
        this.shiny = shiny;
        this.fishOnly = fishOnly;
        this.bossOnly = bossOnly;
    }

    public List<String> blocks() { return Collections.unmodifiableList(blocks); }
    public List<String> items() { return Collections.unmodifiableList(items); }
    public List<String> entities() { return Collections.unmodifiableList(entities); }
    public List<String> biomes() { return Collections.unmodifiableList(biomes); }
    public List<String> advancements() { return Collections.unmodifiableList(advancements); }
    public List<String> species() { return Collections.unmodifiableList(species); }
    public List<String> types() { return Collections.unmodifiableList(types); }
    public Boolean legendary() { return legendary; }
    public Boolean shiny() { return shiny; }
    public Boolean fishOnly() { return fishOnly; }
    public Boolean bossOnly() { return bossOnly; }
}
