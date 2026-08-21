package com.pedrodalben.bigbangessentials.api.rankup;

@FunctionalInterface
public interface RankTransitionListener {
    void onRankTransition(RankTransitionCompletedEvent event);
}
