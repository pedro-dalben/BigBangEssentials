package com.pedrodalben.bigbangessentials.pokemarket.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOperation(UUID id, UUID listingId, UUID buyer, UUID seller, BigDecimal gross, BigDecimal tax, BigDecimal net,
                                PurchaseOperationStatus status, String debitKey, String refundKey, long updatedAt) {}
