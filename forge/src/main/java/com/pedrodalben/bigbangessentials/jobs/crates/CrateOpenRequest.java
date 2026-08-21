package com.pedrodalben.bigbangessentials.jobs.crates;

public record CrateOpenRequest(boolean instant, String idempotencyKey) {}
