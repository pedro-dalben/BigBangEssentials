package com.pedrodalben.bigbangessentials.jobs.crates;

public record CrateOpenResult(boolean success, String message, Object auditRecord) {}
