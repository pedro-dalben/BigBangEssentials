package com.pedrodalben.bigbangessentials.api.economy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IdempotentEconomyService {
    CompletableFuture<EconomyOperationReceipt> debit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata);
    CompletableFuture<EconomyOperationReceipt> credit(UUID playerId, BigDecimal amount, String key, String reason, Map<String, String> metadata);
    CompletableFuture<Optional<EconomyOperationReceipt>> findOperation(String key);
}
