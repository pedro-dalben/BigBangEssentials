package com.pedrodalben.bigbangessentials.pokemarket.model;

import java.math.BigDecimal;

/** Session-only browse filters; no player input is persisted. */
public record ListingSearch(String species, ListingType type, Boolean shiny, Integer minLevel, Integer maxLevel,
                            Integer minPerfectIvs, BigDecimal minPrice, BigDecimal maxPrice, Sort sort) {
    public static ListingSearch empty() { return new ListingSearch(null, null, null, null, null, null, null, null, Sort.NEWEST); }
    public enum Sort { NEWEST, PRICE_ASC, PRICE_DESC, LEVEL_DESC }
}
