package com.pedrodalben.bigbangessentials.pokemarket.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ListingRecord(UUID id, UUID seller, String sellerName, UUID pokemonUuid, byte[] payload,
                            String summaryJson, String species, boolean shiny, int level, int perfectIvs,
                            ListingType type, BigDecimal price, ListingStatus status, long expiresAt) {}
