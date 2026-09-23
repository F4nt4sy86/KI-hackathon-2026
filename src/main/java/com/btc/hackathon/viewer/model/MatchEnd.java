package com.btc.hackathon.viewer.model;

import java.util.List;

/**
 * Endergebnis eines Matches.
 *
 * <p>Der Server bleibt danach auf {@code match_over} stehen, bis die Moderation
 * zuruecksetzt - die Ergebnisanzeige darf also beliebig lange stehen bleiben.
 *
 * @param winner {@code null} bei unentschieden oder Abbruch
 */
public record MatchEnd(
        long tick,
        EndReason reason,
        Integer winner,
        List<MatchResult> results) implements ServerMessage {

    public MatchEnd {
        results = List.copyOf(results);
    }

    public boolean hasWinner() {
        return winner != null;
    }
}
