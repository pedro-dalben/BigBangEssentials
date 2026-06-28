package com.pedrodalben.bigbangessentials.economy.gems.persistence;

import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GemsState {
    public int schemaVersion = 1;
    public long revision = 0;
    public Map<String, Long> balances = new ConcurrentHashMap<>();
    public Map<String, GemReservation> reservations = new ConcurrentHashMap<>();

    public GemsState cloneState() {
        GemsState copy = new GemsState();
        copy.schemaVersion = this.schemaVersion;
        copy.revision = this.revision;
        for (Map.Entry<String, Long> entry : this.balances.entrySet()) {
            copy.balances.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, GemReservation> entry : this.reservations.entrySet()) {
            copy.reservations.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }
}
