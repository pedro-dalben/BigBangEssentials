package com.pedrodalben.bigbangessentials.pokemarket.model;

import java.math.BigDecimal;
import java.util.UUID;

public record TradeOperation(UUID id, UUID listingId, UUID seller, UUID buyer,
                             UUID offeredPokemonUuid, byte[] offeredPokemonData,
                             String offeredPokemonChecksum, String offeredPokemonSummaryJson,
                             TradeOperationStatus status, BigDecimal feeAmount, String feeOperationKey,
                             UUID buyerClaimId, UUID sellerClaimId, long updatedAt) {}
