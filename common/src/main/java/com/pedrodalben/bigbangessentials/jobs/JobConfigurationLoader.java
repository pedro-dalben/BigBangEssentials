package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfigLoader;

public class JobConfigurationLoader {
    public static JobsConfig loadAndValidate() {
        try {
            return JobsConfigLoader.loadAndValidate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load jobs configuration: " + e.getMessage(), e);
        }
    }
}
