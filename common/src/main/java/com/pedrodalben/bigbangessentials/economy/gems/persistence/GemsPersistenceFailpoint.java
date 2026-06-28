package com.pedrodalben.bigbangessentials.economy.gems.persistence;

public enum GemsPersistenceFailpoint {
    BEFORE_WRITE_TEMP,
    AFTER_WRITE_TEMP,
    BEFORE_ATOMIC_MOVE,
    AFTER_ATOMIC_MOVE,
    BEFORE_APPEND_LEDGER,
    AFTER_APPEND_LEDGER,
    BEFORE_CACHE_SWAP,
    BEFORE_EVENT_PUBLISH
}
