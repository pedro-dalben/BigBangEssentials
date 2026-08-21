package com.pedrodalben.bigbangessentials.pokemarket.transaction;

@FunctionalInterface
public interface PokeMarketFailureInjector {
    void checkpoint(PokeMarketCheckpoint checkpoint);
    PokeMarketFailureInjector NO_OP = checkpoint -> {};
}
