package com.btc.hackathon.viewer.model;

/** Ein Teilnehmer des laufenden Matches, wie in {@code match_init} genannt. */
public record MatchPlayer(int id, String name) {

    public MatchPlayer {
        name = name == null ? "" : name;
    }
}
