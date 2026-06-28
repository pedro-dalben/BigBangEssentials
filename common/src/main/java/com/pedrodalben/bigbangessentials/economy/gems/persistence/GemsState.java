package com.pedrodalben.bigbangessentials.economy.gems.persistence;

import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GemsState {
    public int schemaVersion = 1;
    public long revision = 0;
    public Map<String, Long> balances = new ConcurrentHashMap<>();
    public Map<String, GemReservation> reservations = new ConcurrentHashMap<>();
}
