package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;

public class JobConfigurationLoader {
    public static JobsConfig loadAndValidate() throws Exception {
        return JobsConfig.loadAndValidate();
    }
}
