package com.pedrodalben.bigbangessentials.rankup.domain;

public record RankupGroupMutationResult(boolean success, String errorMessage) {
    public static RankupGroupMutationResult ok() {
        return new RankupGroupMutationResult(true, null);
    }

    public static RankupGroupMutationResult failure(String message) {
        return new RankupGroupMutationResult(false, message);
    }
}
