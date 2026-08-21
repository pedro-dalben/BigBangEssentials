package com.pedrodalben.bigbangessentials.pokemarket.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ClaimRecord(UUID id, UUID owner, UUID listing, ClaimType type, UUID pokemonUuid, byte[] payload, BigDecimal money, ClaimStatus status) {}
