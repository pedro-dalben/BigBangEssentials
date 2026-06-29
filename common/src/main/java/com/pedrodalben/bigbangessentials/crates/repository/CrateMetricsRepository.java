package com.pedrodalben.bigbangessentials.crates.repository;

import java.util.Map;

public interface CrateMetricsRepository {
    long incrementCounter(String metricKey);
    long getCounter(String metricKey);
    Map<String, Long> getAllCounters();
    void resetAll();
}
