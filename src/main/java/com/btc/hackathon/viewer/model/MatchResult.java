package com.btc.hackathon.viewer.model;

/** Ergebniszeile eines Spielers. {@code placement} ist 1-basiert; Gleichstand teilt sich einen Platz. */
public record MatchResult(int id, int placement, int score) {
}
