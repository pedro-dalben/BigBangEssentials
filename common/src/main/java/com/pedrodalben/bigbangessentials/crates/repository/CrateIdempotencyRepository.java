package com.pedrodalben.bigbangessentials.crates.repository;

public interface CrateIdempotencyRepository {
    boolean markProcessed(String idempotencyKey, String operationType);
}
