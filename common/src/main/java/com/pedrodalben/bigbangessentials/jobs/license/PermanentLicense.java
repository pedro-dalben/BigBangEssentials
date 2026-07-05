package com.pedrodalben.bigbangessentials.jobs.license;

/**
 * Represents a permanently acquired job profession license.
 */
public record PermanentLicense(
        String jobId,
        long licensedAt,
        String sourceMilestone,
        int version,
        String grantedBy
) {}
